package org.droidparts.sample.activity;

import java.util.ArrayList;

import org.droidparts.activity.legacy.Activity;
import org.droidparts.annotation.inject.InjectBundleExtra;
import org.droidparts.annotation.inject.InjectDependency;
import org.droidparts.annotation.inject.InjectView;
import org.droidparts.sample.R;
import org.droidparts.sample.json.EntrySerializer;
import org.droidparts.sample.model.Entry;
import org.droidparts.util.L;
import org.droidparts.util.ui.AbstractDialogFactory;
import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

public class JsonViewActivity extends Activity implements OnClickListener {

	private static final String EXTRA_ARR_STR = "arr_str";

	public static Intent getIntent(Context ctx, ArrayList<Entry> entries) {
		Intent intent = new Intent(ctx, JsonViewActivity.class);
		intent.putExtra(EXTRA_ARR_STR, entries);
		return intent;
	}

	@InjectView(id = R.id.text)
	private TextView textView;

	@InjectDependency
	private EntrySerializer entrySerializer;
	@InjectDependency
	private AbstractDialogFactory dialogFactory;

	@InjectBundleExtra(key = EXTRA_ARR_STR)
	private ArrayList<Entry> entries;

	@Override
	public void onPreInject() {
		setContentView(R.layout.activity_json_view);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		init();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.button_close:
			dialogFactory.showToast("(:");
			finish();
			break;
		}
	}

	private void init() {
		String msg;
		try {
			JSONArray arr = entrySerializer.serialize(entries);
			msg = arr.toString();
		} catch (JSONException e) {
			L.e(e);
			msg = "o_O";
		}
		textView.setText(msg);
	}

}
