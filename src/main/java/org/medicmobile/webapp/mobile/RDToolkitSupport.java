package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.rdtoolkit.support.interop.RdtIntentBuilder;
import org.rdtoolkit.support.interop.RdtUtils;
import org.rdtoolkit.support.model.session.ProvisionMode;
import org.rdtoolkit.support.model.session.TestSession;
import org.rdtoolkit.support.model.session.TestSession.TestResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static android.app.Activity.RESULT_OK;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RDTOOLKIT_PROVISION_ACTIVITY_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.JavascriptUtils.safeFormat;
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

		switch(requestCode) {
			case RDTOOLKIT_PROVISION_ACTIVITY_REQUEST_CODE: {
				if (resultCode != RESULT_OK) {
					throw new RuntimeException("RDToolkit Support - Bad result code for provisioned test: " + resultCode);
				}

				try {
					JSONObject response = parseProvisionTestResponseToJson(intentData);
					return makeProvisionTestJavaScript(response);
				} catch(Exception /*| JSONException*/ ex) {
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
				} catch(Exception /*| JSONException*/ ex) {
					warn(ex, "Problem serialising RDToolkit capture");
					return safeFormat("console.log('Problem serialising RDToolkit capture: %s')", ex);
				}
			}

			default: throw new RuntimeException("RD Toolkit Support - Bad request type: " + requestCode);
		}
	}

	Intent provisionRDTest(String sessionId, String patientName, String patientId) {
		Intent intent = RdtIntentBuilder
				.forProvisioning()
				// Type of test to choose from
				.requestProfileCriteria("mal_pf", ProvisionMode.CRITERIA_SET_AND)
				// Unique ID for RDT test
				.setSessionId(sessionId)
				// First line text to display in RDT App and differentiate running tests
				.setFlavorOne(patientName)
				// Second line text to display in RDT App and differentiate running tests
				.setFlavorTwo(patientId)
				.setReturnApplication(ctx)
				// For debugging add: .setInTestQaMode()
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
				"RDToolkit test completed for sessionId: %s, see result: %s",
				session.getSessionId(),
				result
		);

		Map<String, String> resultMap = result.getResults();
		JSONArray jsonResult = new JSONArray();

		for (Map.Entry<String,String> entry : resultMap.entrySet()) {
			jsonResult.put(json(
					"test", entry.getKey(),
					"result", entry.getValue()
			));
		}

		/* ToDo fix image path to base64
		InputStream mainImage = ctx.getContentResolver().openInputStream(Uri.parse(result.getMainImage()));
		String mainImageBase64 = Base64.encodeToString(getBytes(mainImage), Base64.NO_WRAP);

		String croppedImageBase64 = "";
		if (result.getImages().size() > 0 && result.getImages().containsKey("cropped")) {
			InputStream croppedImage = ctx.getContentResolver().openInputStream(Uri.parse(result.getImages().get("cropped")));
			croppedImageBase64 = Base64.encodeToString(getBytes(croppedImage), Base64.NO_WRAP);
		}
		*/

		return json(
				"sessionId", session.getSessionId(),
				"state", session.getState(),
				"timeResolved", getISODate(session.getTimeResolved()),
				"timeStarted", getISODate(session.getTimeStarted()),
				"timeRead", getISODate(result.getTimeRead()),
				"mainImage", "", // , mainImageBase64,
				"croppedImage", "", // , croppedImageBase64,
				"results", jsonResult
		);
	}

	// ToDo find better alternative or move to utils.
	private byte[] getBytes(InputStream inputStream) throws IOException {
		ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
		int bufferSize = 1024;
		byte[] buffer = new byte[bufferSize];
		int len = 0;

		while ((len = inputStream.read(buffer)) != -1) {
			byteBuffer.write(buffer, 0, len);
		}

		return byteBuffer.toByteArray();
	}

}
