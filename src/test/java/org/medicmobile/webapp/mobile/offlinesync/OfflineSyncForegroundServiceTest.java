package org.medicmobile.webapp.mobile.offlinesync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowService;
import org.robolectric.shadow.api.Shadow;

@RunWith(RobolectricTestRunner.class)
public class OfflineSyncForegroundServiceTest {

	private OfflineSyncForegroundService service() {
		return Robolectric.setupService(OfflineSyncForegroundService.class);
	}

	private Notification started(String status) {
		return started(status, 0);
	}

	private Notification started(String status, int delivered) {
		OfflineSyncForegroundService service = service();
		Intent intent = new Intent().putExtra("status", status).putExtra("delivered", delivered);

		service.onStartCommand(intent, 0, 1);

		ShadowService shadow = Shadow.extract(service);
		return shadow.getLastForegroundNotification();
	}

	@Test public void aSessionRunsInTheForeground() {
		assertNotNull(started(OfflineSyncForegroundService.STATUS_HOSTING));
	}

	@Test public void theNotificationCannotBeSwipedAway() {
		Notification notification = started(OfflineSyncForegroundService.STATUS_HOSTING);

		assertNotEquals(0, notification.flags & Notification.FLAG_ONGOING_EVENT);
	}

	@Test public void itSaysWhichSideOfTheTransferThisPhoneIsOn() {
		assertEquals("Ready to receive data from another device.", text(started(OfflineSyncForegroundService.STATUS_HOSTING)));
		assertEquals("Sending data to another device.", text(started(OfflineSyncForegroundService.STATUS_SENDING)));
	}

	@Test public void itDoesNotComeBackForASessionThatIsOver() {
		OfflineSyncForegroundService service = service();

		assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 1));
	}

	@Test public void itIsNotSomethingToBindTo() {
		assertNull(service().onBind(new Intent()));
	}

	private String text(Notification notification) {
		return notification.extras.getString(Notification.EXTRA_TEXT);
	}

	@Test public void itCountsWhatHasBeenHandedOver() {
		assertEquals("Transferred 3 packages to another device.",
				text(started(OfflineSyncForegroundService.STATUS_SENDING, 3)));
	}

	@Test public void itDoesNotCountBeforeAnythingHasBeenSent() {
		assertEquals("Sending data to another device.",
				text(started(OfflineSyncForegroundService.STATUS_SENDING, 0)));
	}
}
