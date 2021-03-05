package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.content.Intent;

import org.rdtoolkit.support.interop.RdtIntentBuilder;
import org.rdtoolkit.support.interop.RdtUtils;
import org.rdtoolkit.support.model.session.ProvisionMode;
import org.rdtoolkit.support.model.session.TestSession;
import org.rdtoolkit.support.model.session.TestSession.TestResult;

import static android.app.Activity.RESULT_OK;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.CW_RDT_PROVISION_ACTIVITY_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.CW_RDT_CAPTURE_ACTIVITY_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.MedicLog.trace;

public class CloudWorksRDT {
	private final Activity ctx;

	CloudWorksRDT(Activity ctx) {
		this.ctx = ctx;
	}

	void process(int requestCode, int resultCode, Intent intentData) {

		trace(this, "process() :: requestCode=%s", requestCode);

		switch(requestCode) {
			case CW_RDT_PROVISION_ACTIVITY_REQUEST_CODE: {
				System.out.println(String.format("HOLA! it is the intent resolving: %s %s", resultCode, intentData));
				if (resultCode == RESULT_OK) {
					TestSession session = RdtUtils.getRdtSession(intentData);
					System.out.println(String.format("Test will be available to read at %s", session.getTimeResolved().toString()));
				}
			}

			case CW_RDT_CAPTURE_ACTIVITY_REQUEST_CODE: {
				if (resultCode == RESULT_OK) {
					TestSession session = RdtUtils.getRdtSession(intentData);
					TestResult result = session.getResult();
					System.out.println(String.format("Test completed see: %s", result.toString()));
				}
			}

			default: throw new RuntimeException("Bad request type: " + requestCode);
		}
	}

	Intent createProvisioningRDTest(String sessionId, String patientName, String patientId) {
		System.out.println(String.format("HOLA! it is creating an intention for provisioning: %s %s %s", sessionId, patientName, patientId));
		Intent i = RdtIntentBuilder.forProvisioning()
				// Explicitly declare an ID for the session
				.setSessionId(sessionId)
				// Let the user choose any available RDT which provides a PF and a PV result
				.requestProfileCriteria("mal_pf mal_pv", ProvisionMode.CRITERIA_SET_AND)
				// Text to differentiate running tests
				.setFlavorOne(patientName)
				// Text to differentiate running tests
				.setFlavorTwo(patientId)
				.build();
		if (i.resolveActivity(ctx.getPackageManager()) != null) {
			ctx.startActivity(i);
		}
		return i;
	}

	Intent createCaptureRDTest(String sessionId) {
		return RdtIntentBuilder.forCapture()
				// Explicitly declare an ID for the session
				.setSessionId(sessionId)
				.build();
	}
}
