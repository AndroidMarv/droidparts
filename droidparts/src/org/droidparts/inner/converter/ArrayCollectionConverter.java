/**
 * Copyright 2014 Alex Yanchenko
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.droidparts.inner.converter;

import static org.droidparts.inner.ReflectionUtils.newInstance;
import static org.droidparts.inner.TypeHelper.isArray;
import static org.droidparts.inner.TypeHelper.isModel;
import static org.droidparts.util.Strings.isNotEmpty;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.droidparts.inner.ConverterRegistry;
import org.droidparts.inner.PersistUtils;
import org.droidparts.inner.TypeHelper;
import org.droidparts.model.Model;
import org.droidparts.persist.serializer.AbstractSerializer;
import org.droidparts.persist.serializer.JSONSerializer;
import org.droidparts.persist.serializer.XMLSerializer;
import org.droidparts.util.Arrays2;
import org.droidparts.util.Strings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.content.ContentValues;
import android.database.Cursor;

public class ArrayCollectionConverter extends Converter<Object> {

	// ASCII RS (record separator), '|' for readability
	private static final String SEP = "|" + (char) 30;

	@Override
	public boolean canHandle(Class<?> cls) {
		return TypeHelper.isArray(cls) || TypeHelper.isCollection(cls);
	}

	@Override
	public String getDBColumnType() {
		return TEXT;
	}

	@Override
	public <V> Object readFromJSON(Class<Object> valType,
			Class<V> componentType, JSONObject obj, String key)
			throws JSONException {
		Wrapper w = new Wrapper(obj.getJSONArray(key), null);
		return readFromWrapper(valType, componentType, w);
	}

	@Override
	public <V> Object readFromXML(Class<Object> valType,
			Class<V> componentType, Node node, String nodeListItemTagHint)
			throws Exception {
		NodeList nl = node.getChildNodes();
		ArrayList<Node> nodes = new ArrayList<Node>();
		for (int i = 0; i < nl.getLength(); i++) {
			Node n = nl.item(i);
			if (isNotEmpty(nodeListItemTagHint)) {
				if (nodeListItemTagHint.equals(n.getNodeName())) {
					nodes.add(n);
				}
			} else {
				nodes.add(n);
			}
		}
		Wrapper w = new Wrapper(null, nodes);
		return readFromWrapper(valType, componentType, w);
	}

	@Override
	public <V> void putToJSON(Class<Object> valType, Class<V> componentType,
			JSONObject obj, String key, Object val) throws Exception {
		Converter<V> converter = ConverterRegistry.getConverter(componentType);
		ArrayList<V> list = arrOrCollToList(valType, componentType, val);
		JSONArray vals = new JSONArray();
		JSONObject tmpObj = new JSONObject();
		for (V value : list) {
			converter.putToJSON(componentType, null, tmpObj, "key", value);
			vals.put(tmpObj.get("key"));
		}
		obj.put(key, vals);
	}

	@Override
	protected <V> Object parseFromString(Class<Object> valType,
			Class<V> componentType, String str) {
		throw new UnsupportedOperationException();
	}

	//
	@SuppressWarnings("unchecked")
	protected <V> Object readFromWrapper(Class<Object> valType,
			Class<V> componentType, Wrapper wrapper) {
		boolean isArr = isArray(valType);
		boolean isModel = isModel(componentType);
		Collection<Object> items;
		if (isArr) {
			items = new ArrayList<Object>();
		} else {
			items = (Collection<Object>) newInstance(valType);
		}
		AbstractSerializer<Model, Object, Object> serializer = null;
		if (isModel) {
			serializer = (AbstractSerializer<Model, Object, Object>) wrapper
					.makeSerializer((Class<Model>) componentType);
		}
		Converter<V> converter = ConverterRegistry.getConverter(componentType);
		for (int i = 0; i < wrapper.length(); i++) {
			Object item;
			try {
				if (isModel) {
					item = serializer.deserialize(wrapper.get(i));
				} else {
					item = wrapper.convert(wrapper.get(i), converter,
							componentType);
				}
				items.add(item);
			} catch (Exception e) {
				if (wrapper.isJSON()) {
					throw new IllegalArgumentException(e);
				} else {
					// TODO log?
				}
			}
		}
		if (isArr) {
			Object[] arr = items.toArray();
			if (isModel) {
				Object modelArr = Array.newInstance(componentType, arr.length);
				for (int i = 0; i < arr.length; i++) {
					Array.set(modelArr, i, arr[i]);
				}
				return modelArr;
			} else {
				String[] arr2 = new String[arr.length];
				for (int i = 0; i < arr.length; i++) {
					arr2[i] = arr[i].toString();
				}
				return parseTypeArr(converter, componentType, arr2);
			}
		} else {
			return items;
		}
	}

	private static class Wrapper {
		private final JSONArray arr;
		private final ArrayList<Node> nodes;

		Wrapper(JSONArray arr, ArrayList<Node> nodes) {
			this.arr = arr;
			this.nodes = nodes;
		}

		public int length() {
			return isJSON() ? arr.length() : nodes.size();
		}

		public <V> Object get(int i) throws Exception {
			if (isJSON()) {
				return arr.get(i);
			} else {
				return nodes.get(i);
			}
		}

		public <V> Object convert(Object item, Converter<V> conv,
				Class<V> valType) throws Exception {
			if (isJSON()) {
				return item;
			} else {
				Node n = (Node) item;
				String txt = PersistUtils.getNodeText(n);
				return conv.parseFromString(valType, null, txt);
			}
		}

		public <M extends Model> AbstractSerializer<M, ?, ?> makeSerializer(
				Class<M> componentType) {
			if (isJSON()) {
				return new JSONSerializer<M>(componentType, null);
			} else {
				return new XMLSerializer<M>(componentType, null);
			}
		}

		public boolean isJSON() {
			return (arr != null);
		}
	}

	//

	@Override
	public <V> void putToContentValues(Class<Object> valueType,
			Class<V> componentType, ContentValues cv, String key, Object val)
			throws IllegalArgumentException {
		Converter<V> converter = ConverterRegistry.getConverter(componentType);
		ArrayList<V> list = arrOrCollToList(valueType, componentType, val);
		ArrayList<Object> vals = new ArrayList<Object>();
		ContentValues tmpCV = new ContentValues();
		for (V obj : list) {
			converter
					.putToContentValues(componentType, null, tmpCV, "key", obj);
			vals.add(tmpCV.get("key"));
		}
		String strVal = Strings.join(vals, SEP);
		cv.put(key, strVal);
	}

	@Override
	public <V> Object readFromCursor(Class<Object> valType,
			Class<V> componentType, Cursor cursor, int columnIndex) {
		Converter<V> converter = ConverterRegistry.getConverter(componentType);
		String str = cursor.getString(columnIndex);
		String[] parts = (str.length() > 0) ? str.split("\\" + SEP)
				: new String[0];
		if (isArray(valType)) {
			return parseTypeArr(converter, componentType, parts);
		} else {
			return parseTypeColl(converter, valType, componentType, parts);
		}
	}

	@SuppressWarnings("unchecked")
	private <T> ArrayList<T> arrOrCollToList(Class<?> valType,
			Class<T> componentType, Object val) {
		ArrayList<T> list = new ArrayList<T>();
		if (isArray(valType)) {
			list.addAll((List<T>) Arrays.asList(Arrays2.toObjectArray(val)));
		} else {
			list.addAll((Collection<T>) val);
		}
		return list;
	}

	// say hello to arrays of primitives
	private final <T> Object parseTypeArr(Converter<T> converter,
			Class<T> valType, String[] arr) {
		Object objArr = Array.newInstance(valType, arr.length);
		for (int i = 0; i < arr.length; i++) {
			T item = converter.parseFromString(valType, null, arr[i]);
			Array.set(objArr, i, item);
		}
		return objArr;
	}

	private final <T> Collection<T> parseTypeColl(Converter<T> converter,
			Class<Object> collType, Class<T> componentType, String[] arr) {
		@SuppressWarnings("unchecked")
		Collection<T> coll = (Collection<T>) newInstance(collType);
		for (int i = 0; i < arr.length; i++) {
			T item = converter.parseFromString(componentType, null, arr[i]);
			coll.add(item);
		}
		return coll;
	}

}
