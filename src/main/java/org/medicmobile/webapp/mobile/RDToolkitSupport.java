package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.content.Intent;

import org.json.JSONObject;
import org.rdtoolkit.support.interop.RdtIntentBuilder;
import org.rdtoolkit.support.interop.RdtUtils;
import org.rdtoolkit.support.model.session.ProvisionMode;
import org.rdtoolkit.support.model.session.TestSession;
import org.rdtoolkit.support.model.session.TestSession.TestResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static android.app.Activity.RESULT_OK;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RDTOOLKIT_PROVISION_ACTIVITY_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.JavascriptUtils.safeFormat;
import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;
import static org.medicmobile.webapp.mobile.Utils.json;

public class RDToolkitSupport {
	private final Activity ctx;

	RDToolkitSupport(Activity ctx) {
		this.ctx = ctx;
	}

	String process(int requestCode, int resultCode, Intent intentData) {

		trace(this, "process() :: requestCode=%s", requestCode);

		switch(requestCode) {
			case RDTOOLKIT_PROVISION_ACTIVITY_REQUEST_CODE: {
				if (resultCode == RESULT_OK) {
					try {
						TestSession session = RdtUtils.getRdtSession(intentData);
						log("RDToolkit provisioned test for sessionId: %s, will be available to read at %s", session.getSessionId(), session.getTimeResolved().toString());
						JSONObject response = json(
								"sessionId", session.getSessionId(),
								"timeResolved", session.getTimeResolved(),
								"timeStarted", session.getTimeStarted(),
								"state", session.getState()
						);

						return sendProvisionedResponseToJavaScriptApp(response);
					} catch(Exception /*| JSONException*/ ex) {
						warn(ex, "Problem serialising RDToolkit provisioned test");
						return safeFormat("console.log('Problem serialising RDToolkit provisioned test: %s')", ex);
					}
				}
			}

			case RDTOOLKIT_CAPTURE_ACTIVITY_REQUEST_CODE: {
				if (resultCode == RESULT_OK) {
					try {
						TestSession session = RdtUtils.getRdtSession(intentData);
						TestResult result = session.getResult();
						log("RDToolkit test completed for sessionId: %s, see result: %s", session.getSessionId(), result);
						/* ToDo fix chopped image
						InputStream mainImage = ctx.getContentResolver().openInputStream(Uri.parse(result.getMainImage()));
						String mainImageBase64 = Base64.encodeToString(getBytes(mainImage), Base64.NO_WRAP);

						String croppedImageBase64 = "";
						if (result.getImages().size() > 0 && result.getImages().containsKey("cropped")) {
							InputStream croppedImage = ctx.getContentResolver().openInputStream(Uri.parse(result.getImages().get("cropped")));
							croppedImageBase64 = Base64.encodeToString(getBytes(croppedImage), Base64.NO_WRAP);
						}
						*/
						JSONObject response = json(
								"sessionId", session.getSessionId(),
								"timeRead", result.getTimeRead(),
								"mainImage", "", // , mainImageBase64,
								"croppedImage", "", // , croppedImageBase64,
								"results", result.getResults()
						);

						return sendCapturedResponseToJavaScriptApp(response);
					} catch(Exception /*| JSONException*/ ex) {
						warn(ex, "Problem serialising RDToolkit captured test");
						return safeFormat("console.log('Problem serialising RDToolkit captured test: %s')", ex);
					}
				}
			}

			default: throw new RuntimeException("Bad request type: " + requestCode);
		}
	}

	Intent createProvisioningRDTest(String sessionId, String patientName, String patientId) {
		Intent intent = RdtIntentBuilder
				.forProvisioning()
				// Type of test to choose from
				.requestProfileCriteria("mal_pf", ProvisionMode.CRITERIA_SET_AND)
				// Unique ID for RDT test
				.setSessionId(sessionId)
				// Text to display in RDT App and differentiate running tests
				.setFlavorOne(patientName)
				// Text to display in RDT App and differentiate running tests
				.setFlavorTwo(patientId)
				.setReturnApplication(ctx)
				// For debugging add: .setInTestQaMode()
				.build();

		if (intent.resolveActivity(ctx.getPackageManager()) != null) {
			ctx.startActivityForResult(intent, RDTOOLKIT_PROVISION_ACTIVITY_REQUEST_CODE);
		}

		return intent;
	}

	Intent createCaptureRDTest(String sessionId) {
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

	private String sendProvisionedResponseToJavaScriptApp(Object response) {
		String javaScript = "try {" +
				"const api = angular.element(document.body).injector().get('AndroidApi');" +
				"if (api.v1.rdToolkitProvisionedTestResponse) {" +
				"	api.v1.rdToolkitProvisionedTestResponse(%s);" +
				"}" +
				"} catch (e) { alert(e); }";

		return safeFormat(javaScript, response);
	}

	private String sendCapturedResponseToJavaScriptApp(Object response) {
		String javaScript = "try {" +
				"const api = angular.element(document.body).injector().get('AndroidApi');" +
				"if (api.v1.rdToolkitCapturedTestResponse) {" +
				"	api.v1.rdToolkitCapturedTestResponse(%s);" +
				"}" +
				"} catch (e) { alert(e); }";

		return safeFormat(javaScript, response, response);
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
