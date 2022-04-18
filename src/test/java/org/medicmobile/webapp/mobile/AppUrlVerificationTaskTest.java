package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mockStatic;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
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
		try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
			utilsMock.when(() -> Utils.isDebug()).thenReturn(true);

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
}
