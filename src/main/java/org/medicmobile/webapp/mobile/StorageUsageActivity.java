package org.medicmobile.webapp.mobile;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.webkit.ValueCallback;
import android.webkit.WebStorage;
import android.webkit.WebStorage.Origin;
import android.widget.*;

import java.util.Map;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;

public class StorageUsageActivity extends Activity {
	public void onCreate(Bundle savedInstanceState) {
		if(DEBUG) log("Starting...");
		super.onCreate(savedInstanceState);

		setContentView(R.layout.storage_usage);

		refresh();
	}

	public void refresh() {
		final WebStorage ws = WebStorage.getInstance();
		final StringBuffer bob = new StringBuffer();
		ws.getOrigins(new ValueCallback<Map>() {
			public void onReceiveValue(Map origins) {
				for(Object e : origins.entrySet()) {
					Map.Entry entry = (Map.Entry) e;
					Origin origin = (Origin) entry.getValue();
					long quota = origin.getQuota();
					long used = origin.getUsage();
					int percentFree = (int) ((quota - used) * 100 / quota);
					bob.append(entry.getKey() + " – available space: " +
							used + "/" + quota + " b\n" +
							"(" + percentFree + "% free)\n\n");
					text(R.id.txtStorageUsage, bob.toString());
				}
			}
		});
	}

	private void text(int componentId, String value) {
		TextView field = (TextView) findViewById(componentId);
		field.setText(value);
	}

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | StorageUsageActivity :: " +
				String.format(message, extras));
	}
}
