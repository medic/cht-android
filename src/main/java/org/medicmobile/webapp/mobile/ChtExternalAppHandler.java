package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.app.Activity.RESULT_OK;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RequestCode;
import static org.medicmobile.webapp.mobile.JavascriptUtils.safeFormat;
import static org.medicmobile.webapp.mobile.MedicLog.error;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

import android.app.Activity;
import android.content.Intent;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.Optional;

public class ChtExternalAppHandler {

	private final Activity context;
	private Intent lastIntent;

	ChtExternalAppHandler(Activity context) {
		this.context = context;
	}

	String processResult(int resultCode, Intent intent) {
		if (resultCode != RESULT_OK) {
			String message = "ChtExternalAppHandler :: Bad result code: %s. The external app either: " +
					"explicitly returned this result, didn't return any result or crashed during the operation.";

			warn(this, message, resultCode);
			return safeFormat("console.error('" + message + "')", resultCode);
		}

		try {
			Optional<JSONObject> json = new ChtExternalApp
					.Response(intent, this.context)
					.getData();
			String data = json.map(JSONObject::toString).orElse(null);
			return makeJavaScript(data);

		} catch (Exception exception) {
			String message = "ChtExternalAppHandler :: Problem serialising the intent response.";
			error(exception, message);
			return safeFormat("console.error('" + message + "', %s)", exception);
		}
	}

	void startIntent(ChtExternalApp chtExternalApp) {
		Intent chtExternalAppIntent = chtExternalApp.createIntent();

		if (ContextCompat.checkSelfPermission(this.context, READ_EXTERNAL_STORAGE) != PERMISSION_GRANTED) {
			trace(this, "ChtExternalAppHandler :: Requesting storage permissions to process image files taken from external apps");
			this.lastIntent = chtExternalAppIntent; // Saving intent to start it when permission is granted.
			Intent requestStorageIntent = new Intent(this.context, RequestStoragePermissionActivity.class);
			requestStorageIntent.putExtra(
				RequestStoragePermissionActivity.TRIGGER_CLASS,
				ChtExternalAppHandler.class.getName()
			);
			this.context.startActivityForResult(requestStorageIntent, RequestCode.ACCESS_STORAGE_PERMISSION.getCode());
			return;
		}

		startActivity(chtExternalAppIntent);
	}

	void resumeActivity() {
		if (this.lastIntent == null) {
			return;
		}

		startActivity(this.lastIntent);
		this.lastIntent = null; // Cleaning as we don't need it anymore.
	}

	//> PRIVATE

	private void startActivity(Intent intent) {
		try {
			trace(this, "ChtExternalAppHandler :: Starting activity %s %s", intent, intent.getExtras());
			this.context.startActivityForResult(intent, RequestCode.CHT_EXTERNAL_APP_ACTIVITY.getCode());

		} catch (Exception exception) {
			error(exception, "ChtExternalAppHandler :: Error when starting the activity %s %s", intent, intent.getExtras());
		}
	}

	private static String makeJavaScript(String data) {
		String javaScript = "try {" +
				"const api = window.CHTCore.AndroidApi;" +
				"if (api && api.v1 && api.v1.resolveCHTExternalAppResponse) {" +
				"  api.v1.resolveCHTExternalAppResponse(%s);" +
				"}" +
				"} catch (error) { " +
				"  console.error('ChtExternalAppHandler :: Error on sending intent response to CHT-Core webapp', error);" +
				"}";

		return safeFormat(javaScript, data);
	}
}
