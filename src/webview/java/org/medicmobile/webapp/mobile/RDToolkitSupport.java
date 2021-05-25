package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.rdtoolkit.support.interop.RdtIntentBuilder;
import org.rdtoolkit.support.interop.RdtUtils;
import org.rdtoolkit.support.model.session.ProvisionMode;
import org.rdtoolkit.support.model.session.TestSession;
import org.rdtoolkit.support.model.session.TestSession.TestResult;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

import static android.app.Activity.RESULT_OK;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RDTOOLKIT_PROVISION_ACTIVITY_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.JavascriptUtils.safeFormat;
import static org.medicmobile.webapp.mobile.MedicLog.error;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.Utils.getUriFromFilePath;
import static org.medicmobile.webapp.mobile.Utils.getUtcIsoDate;
import static org.medicmobile.webapp.mobile.Utils.json;

public class RDToolkitSupport {

	private final Activity ctx;

	RDToolkitSupport(Activity ctx) {
		this.ctx = ctx;
	}

	String process(int requestCode, int resultCode, Intent intentData) {

		trace(this, "RDToolkitSupport :: process requestCode=%s", requestCode);

		switch (requestCode) {
			case RDTOOLKIT_PROVISION_ACTIVITY_REQUEST_CODE:
				return processProvisionTestActivity(resultCode, intentData);

			case RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE:
				return processCaptureResponseActivity(resultCode, intentData);

			default:
				throw new RuntimeException("RDToolkitSupport :: Bad request type: " + requestCode);
		}
	}

	Intent provisionRDTest(String sessionId, String patientName, String patientId, String rdtFilter, String monitorApiURL) {
		ProvisionMode provisionMode = !rdtFilter.trim().matches("\\S+") ? ProvisionMode.CRITERIA_SET_AND : ProvisionMode.CRITERIA_SET_OR;
		Intent intent = RdtIntentBuilder
				.forProvisioning()
				.setCallingPackage(ctx.getPackageName())
				.setReturnApplication(ctx)
				// Type of RD Test to choose from
				.requestProfileCriteria(rdtFilter, provisionMode)
				// Unique ID for RD Test
				.setSessionId(sessionId)
				// First line text to display in RDToolkit App and to differentiate running tests
				.setFlavorOne(patientName)
				// Second line text to display in RDToolkit App and to differentiate running tests
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
				// Unique ID for RD Test
				.setSessionId(sessionId)
				.build();

		if (intent.resolveActivity(ctx.getPackageManager()) != null) {
			ctx.startActivityForResult(intent, RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE);
		}

		return intent;
	}

	String getImage(String path) {
		if (path == null || path.length() == 0) {
			return null;
		}

		try {
			trace(this, "RDToolkitSupport :: Retrieving image file");
			Uri filePath = getUriFromFilePath(path);
			ParcelFileDescriptor parcelFileDescriptor = ctx
					.getContentResolver()
					.openFileDescriptor(filePath, "r");

			InputStream file = new FileInputStream(parcelFileDescriptor.getFileDescriptor());
			Bitmap imgBitmap = BitmapFactory.decodeStream(file);
			file.close();

			trace(this, "RDToolkitSupport :: Compressing image file");
			ByteArrayOutputStream outputFile = new ByteArrayOutputStream();
			imgBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputFile);

			trace(this, "RDToolkitSupport :: Encoding image file to Base64");
			byte[] imageBytes = outputFile.toByteArray();

			return Base64.encodeToString(imageBytes, Base64.NO_WRAP);

		} catch (Exception exception) {
			error(exception, "RDToolkitSupport :: Failed to process image file from path: %s", path);
		}

		return null;
	}

//> PRIVATE HELPERS

	private String processCaptureResponseActivity(int resultCode, Intent intentData) {
		if (resultCode != RESULT_OK) {
			throw new RuntimeException("RDToolkitSupport :: Bad result code for capturing result: " + resultCode);
		}

		try {
			JSONObject response = parseCaptureResponseToJson(intentData);
			return makeCaptureResponseJavaScript(response);

		} catch (Exception exception) {
			error(exception, "RDToolkitSupport :: Problem serialising the captured result");
			return safeFormat("console.error('Problem serialising the captured result: %s')", exception);
		}
	}

	private String processProvisionTestActivity(int resultCode, Intent intentData) {
		if (resultCode != RESULT_OK) {
			throw new RuntimeException("RDToolkitSupport :: Bad result code for the provisioned RD Test: " + resultCode);
		}

		try {
			JSONObject response = parseProvisionTestResponseToJson(intentData);
			return makeProvisionTestJavaScript(response);

		} catch (Exception exception) {
			error(exception, "RDToolkitSupport :: Problem serialising the provisioned RD Test");
			return safeFormat("console.error('Problem serialising the provisioned RD Test: %s')", exception);
		}
	}

	private String makeProvisionTestJavaScript(Object response) {
		String javaScript = "try {" +
				"const api = window.CHTCore.AndroidApi;" +
				"if (api && api.rdToolkitProvisionedTestResponse) {" +
				"  api.rdToolkitProvisionedTestResponse(%s);" +
				"}" +
				"} catch (error) { " +
				"  console.error('RDToolkitSupport :: Error on sending provisioned RD Test data to CHT-Core - Webapp', error);" +
				"}";

		return safeFormat(javaScript, response);
	}

	private String makeCaptureResponseJavaScript(Object response) {
		String javaScript = "try {" +
				"const api = window.CHTCore.AndroidApi;" +
				"if (api && api.rdToolkitCapturedTestResponse) {" +
				"  api.rdToolkitCapturedTestResponse(%s);" +
				"}" +
				"} catch (error) { " +
				"  console.error('RDToolkitSupport :: Error on sending captured results of RD Test to CHT-Core - Webapp', error);" +
				"}";

		return safeFormat(javaScript, response, response);
	}

	private JSONObject parseProvisionTestResponseToJson(Intent intentData) throws NullPointerException, JSONException {
		TestSession session = RdtUtils.getRdtSession(intentData);
		trace(this, "RDToolkitSupport :: RD Test started, see session: %s", session);

		return json(
				"sessionId", session.getSessionId(),
				"timeResolved", getUtcIsoDate(session.getTimeResolved()),
				"timeStarted", getUtcIsoDate(session.getTimeStarted()),
				"state", session.getState()
		);
	}

	private JSONObject parseCaptureResponseToJson(Intent intentData) throws NullPointerException, JSONException {
		TestSession session = RdtUtils.getRdtSession(intentData);
		TestResult result = session.getResult();
		trace(this, "RDToolkitSupport :: RD Test completed, session: %s, results: %s", session, result);

		return json(
				"sessionId", session.getSessionId(),
				"state", session.getState(),
				"timeResolved", getUtcIsoDate(session.getTimeResolved()),
				"timeStarted", getUtcIsoDate(session.getTimeStarted()),
				"timeRead", result == null ? null : getUtcIsoDate(result.getTimeRead()),
				"croppedImage", result == null ? null : getImage(result.getImages().get("cropped")),
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
}
