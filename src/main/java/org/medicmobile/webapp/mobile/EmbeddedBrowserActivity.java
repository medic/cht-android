package org.medicmobile.webapp.mobile;

import android.app.*;
import android.webkit.*;
import android.widget.*;
import android.os.*;

import org.xwalk.core.XWalkView;

public class EmbeddedBrowserActivity extends Activity {
	private static final boolean DEBUG = BuildConfig.DEBUG;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		XWalkView container = (XWalkView) findViewById(R.id.WebViewContainer);

		final String url = "https://demo.app.medicmobile.org";
		if(DEBUG) log("Pointing browser to %s", url);
		container.load(url, null);
	}

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | EmbeddedBrowserActivity::" +
				String.format(message, extras));
	}
}
