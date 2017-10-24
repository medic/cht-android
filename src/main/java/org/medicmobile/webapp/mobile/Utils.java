package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.json.JSONException;
import org.json.JSONObject;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;

final class Utils {
	private Utils() {}

	static JSONObject json(Object... keyVals) throws JSONException {
		if(DEBUG && keyVals.length % 2 != 0) throw new AssertionError();
		JSONObject o = new JSONObject();
		for(int i=keyVals.length-1; i>0; i-=2) {
			o.put(keyVals[i-1].toString(), keyVals[i]);
		}
		return o;
	}

	static boolean intentHandlerAvailableFor(Context ctx, Intent intent) {
		return intent.resolveActivity(ctx.getPackageManager()) != null;
	}

	static void startAppActivityChain(Activity a) {
		if(SettingsStore.in(a).hasWebappSettings()) {
			MmPromptForPermissionsActivity.startPermissionsRequestChainFrom(a);
		} else {
			a.startActivity(new Intent(a, SettingsDialogActivity.class));
			a.finish();
		}
	}

	static String urlDecode(String encodedString) {
		try {
			return URLDecoder.decode(encodedString, "UTF-8");
		} catch(UnsupportedEncodingException ex) {
			// this should never happen as UTF-8 is default encoding
			// on android.
			throw new RuntimeException(ex);
		}
	}
}
