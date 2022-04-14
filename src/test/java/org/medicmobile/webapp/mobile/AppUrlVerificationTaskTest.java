package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class AppUrlVerificationTaskTest {

	@Test
	public void call_withAppUrl_returnsVerificationResult() {
		AppUrlVerification result = new AppUrlVerificationTask("https://a-project.medic.org").call();

		assertEquals("https://a-project.medic.org", result.appUrl);
		assertEquals(false, result.isOk);
		assertNotNull(result.failure);
	}

	@Test
	public void call_withNoAppURL_throwsException() {
		RuntimeException runtimeException = assertThrows(
			RuntimeException.class,
			() -> new AppUrlVerificationTask(null).call()
		);
		assertEquals(
			"AppUrlVerificationTask :: Cannot verify APP URL because it is not defined.",
			runtimeException.getMessage()
		);
	}
}
