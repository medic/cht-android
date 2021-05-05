package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.icu.util.Output;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Base64;

import org.apache.commons.io.IOUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.rdtoolkit.support.interop.RdtIntentBuilder;
import org.rdtoolkit.support.interop.RdtUtils;
import org.rdtoolkit.support.model.session.ProvisionMode;
import org.rdtoolkit.support.model.session.TestSession;
import org.rdtoolkit.support.model.session.TestSession.TestResult;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import static android.app.Activity.RESULT_OK;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RDTOOLKIT_PROVISION_ACTIVITY_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.JavascriptUtils.safeFormat;
import static org.medicmobile.webapp.mobile.MedicLog.error;
import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;
import static org.medicmobile.webapp.mobile.Utils.json;
import static org.medicmobile.webapp.mobile.Utils.getISODate;

public class RDToolkitSupport {
	private final Activity ctx;

	RDToolkitSupport(Activity ctx) {
		this.ctx = ctx;
	}

	String process(int requestCode, int resultCode, Intent intentData) {

		trace(this, "process() :: requestCode=%s", requestCode);

		switch (requestCode) {
			case RDTOOLKIT_PROVISION_ACTIVITY_REQUEST_CODE: {
				if (resultCode != RESULT_OK) {
					throw new RuntimeException("RDToolkit Support - Bad result code for provisioned test: " + resultCode);
				}

				try {
					JSONObject response = parseProvisionTestResponseToJson(intentData);
					return makeProvisionTestJavaScript(response);
				} catch (Exception /*| JSONException*/ ex) {
					warn(ex, "Problem serialising RDToolkit provisioned test");
					return safeFormat("console.log('Problem serialising RDToolkit provisioned test: %s')", ex);
				}
			}

			case RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE: {
				if (resultCode != RESULT_OK) {
					throw new RuntimeException("RDToolkit Support - Bad result code for capture: " + resultCode);
				}

				try {
					JSONObject response = parseCaptureResponseToJson(intentData);
					return makeCaptureResponseJavaScript(response);
				} catch (Exception /*| JSONException*/ ex) {
					warn(ex, "Problem serialising RDToolkit capture");
					return safeFormat("console.log('Problem serialising RDToolkit capture: %s')", ex);
				}
			}

			default:
				throw new RuntimeException("RD Toolkit Support - Bad request type: " + requestCode);
		}
	}

	Intent provisionRDTest(String sessionId, String patientName, String patientId, String rdtFilter, String monitorApiURL) {
		ProvisionMode provisionMode = !rdtFilter.trim().matches("\\S+") ? ProvisionMode.CRITERIA_SET_AND : ProvisionMode.CRITERIA_SET_OR;
		Intent intent = RdtIntentBuilder
				.forProvisioning()
				.setCallingPackage(ctx.getPackageName())
				.setReturnApplication(ctx)
				// Type of test to choose from
				.requestProfileCriteria(rdtFilter, provisionMode)
				// Unique ID for RDT test
				.setSessionId(sessionId)
				// First line text to display in RDT App and differentiate running tests
				.setFlavorOne(patientName)
				// Second line text to display in RDT App and differentiate running tests
				.setFlavorTwo(patientId)
				.setCloudworksBackend(monitorApiURL, patientId)
				.build();

		if (intent.resolveActivity(ctx.getPackageManager()) != null) {
			ctx.startActivityForResult(intent, RDTOOLKIT_PROVISION_ACTIVITY_REQUEST_CODE);
		}

		return intent;
	}

	Intent captureRDTest(String sessionId) {
		Intent intent = RdtIntentBuilder
				.forCapture()
				// Unique ID for RDT test
				.setSessionId(sessionId)
				.build();

		if (intent.resolveActivity(ctx.getPackageManager()) != null) {
			ctx.startActivityForResult(intent, RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE);
		}

		return intent;
	}

//> PRIVATE HELPERS

	private String makeProvisionTestJavaScript(Object response) {
		String javaScript = "try {" +
				"const api = angular.element(document.body).injector().get('AndroidApi');" +
				"if (api.v1.rdToolkitProvisionedTestResponse) {" +
				"	api.v1.rdToolkitProvisionedTestResponse(%s);" +
				"}" +
				"} catch (e) { alert(e); }";

		return safeFormat(javaScript, response);
	}

	private String makeCaptureResponseJavaScript(Object response) {
		String javaScript = "try {" +
				"const api = angular.element(document.body).injector().get('AndroidApi');" +
				"if (api.v1.rdToolkitCapturedTestResponse) {" +
				"	api.v1.rdToolkitCapturedTestResponse(%s);" +
				"}" +
				"} catch (e) { alert(e); }";

		return safeFormat(javaScript, response, response);
	}

	private JSONObject parseProvisionTestResponseToJson(Intent intentData) throws JSONException {
		TestSession session = RdtUtils.getRdtSession(intentData);
		log(
				"RDToolkit provisioned test for sessionId: %s, will be available to read at %s, see session: %s",
				session.getSessionId(),
				session.getTimeResolved().toString(),
				session
		);

		return json(
				"sessionId", session.getSessionId(),
				"timeResolved", getISODate(session.getTimeResolved()),
				"timeStarted", getISODate(session.getTimeStarted()),
				"state", session.getState()
		);
	}

	private JSONObject parseCaptureResponseToJson(Intent intentData) throws JSONException {
		TestSession session = RdtUtils.getRdtSession(intentData);
		TestResult result = session.getResult();
		log(
				"RDToolkit test completed for session: %s, see result: %s",
				session,
				result
		);

		return json(
				"sessionId", session.getSessionId(),
				"state", session.getState(),
				"timeResolved", getISODate(session.getTimeResolved()),
				"timeStarted", getISODate(session.getTimeStarted()),
				"timeRead", result == null ? "" : getISODate(result.getTimeRead()),
				"croppedImage", getImage(result.getImages().get("cropped")),
				"results", parseResultsToJson(result)
		);
	}

	private JSONArray parseResultsToJson(TestResult result) throws JSONException {
		JSONArray jsonResult = new JSONArray();

		if (result != null) {
			Map<String, String> resultMap = result.getResults();

			for (Map.Entry<String, String> entry : resultMap.entrySet()) {
				jsonResult.put(json(
						"test", entry.getKey(),
						"result", entry.getValue()
				));
			}
		}

		return jsonResult;
	}

	// ToDo: this is an experiment to get images
	private String getImage(String path){
		try {

			log("RDToolkit getting image file");

			Uri filePath = Uri.parse(path);

			ParcelFileDescriptor parcelFileDescriptor = ctx
					.getContentResolver()
					.openFileDescriptor(filePath, "r");

			InputStream file = new FileInputStream(parcelFileDescriptor.getFileDescriptor());
			Bitmap bitmap = BitmapFactory.decodeStream(file);

			log("RDToolkit compressing image file");
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			boolean compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 75, byteArrayOutputStream);

			byte[] imageBytes = byteArrayOutputStream.toByteArray(); // IOUtils.toByteArray(file);
			file.close();
			String imageStr = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
			log("IMG: %s -> %s", imageStr.length(), imageStr);

			return imageStr;

		} catch (Exception exception) {
			error(exception, "Failed to get image from path: %s", path);
		}

		return null;
	}
}
