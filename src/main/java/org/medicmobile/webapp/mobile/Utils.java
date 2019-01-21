package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import static org.medicmobile.webapp.mobile.BuildConfig.APPLICATION_ID;
import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.VERSION_NAME;

final class Utils {
	private Utils() {}

	static boolean isUrlRelated(String appUrl, Uri uriToTest) {
		// android.net.Uri doesn't give us a host for URLs like blob:https://some-project.dev.medicmobile.org/abc-123
		// so we might as well just regex the URL string
		return uriToTest.toString().matches("^(blob:)?" + appUrl + "/.*$");
	}

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

	public static ProgressDialog showSpinner(Context ctx, int messageId) {
		return showSpinner(ctx, ctx.getString(messageId));
	}

	public static ProgressDialog showSpinner(Context ctx, String message) {
		ProgressDialog p = new ProgressDialog(ctx);
		p.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		if(message != null) p.setMessage(message);
		p.setIndeterminate(true);
		p.setCanceledOnTouchOutside(false);
		p.show();
		return p;
	}

	static String createUseragentFrom(String current) {
		if(current.contains(APPLICATION_ID)) return current;

		return String.format("%s %s/%s",
				current, APPLICATION_ID, VERSION_NAME);
	}
}
