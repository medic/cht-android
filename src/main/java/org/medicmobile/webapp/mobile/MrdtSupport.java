package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.content.Intent;
import android.util.Base64;

//import org.json.JSONException;

import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.GRAB_MRDT_PHOTO_ACTIVITY_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.JavascriptUtils.safeFormat;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;
import static org.medicmobile.webapp.mobile.Utils.intentHandlerAvailableFor;

class MrdtSupport {
	private static final String ACTION_VERIFY = "medic.mrdt.verify";

	private final Activity ctx;

	MrdtSupport(Activity ctx) {
		this.ctx = ctx;
	}

	boolean isAppInstalled() {
		return intentHandlerAvailableFor(ctx, verifyIntent());
	}

	void startVerify() {
		ctx.startActivityForResult(verifyIntent(), GRAB_MRDT_PHOTO_ACTIVITY_REQUEST_CODE);
	}

	String process(int requestCode, int resultCode, Intent i) {
		trace(this, "process() :: requestCode=%s", requestCode);

		switch(requestCode) {
			case GRAB_MRDT_PHOTO_ACTIVITY_REQUEST_CODE: {
				try {
					byte[] data = i.getByteArrayExtra("data");
					String base64data = Base64.encodeToString(data, Base64.NO_WRAP);
					long timeTaken = i.getLongExtra("timeTaken", 0);

					String javaScript = "var api = angular.element(document.body).injector().get('AndroidApi');" +
							"if (api.v1.mrdtTimeTakenResponse) {" +
							"	api.v1.mrdtTimeTakenResponse('\"%s\"');" +
							"}" +
							"api.v1.mrdtResponse('\"%s\"');";
					return safeFormat(javaScript, String.valueOf(timeTaken), base64data);
				} catch(Exception /*| JSONException*/ ex) {
					warn(ex, "Problem serialising mrdt image.");
					return safeFormat("console.log('Problem serialising mrdt image: %s')", ex);
				}
			}

			default: throw new RuntimeException("Bad request type: " + requestCode);
		}
	}

//> PRIVATE HELPERS
	private Intent verifyIntent() {
		return new Intent(ACTION_VERIFY);
	}
}
