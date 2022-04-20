package org.medicmobile.webapp.mobile;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_ONE_SHOT;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.PendingIntent;
import android.content.Intent;
import android.telephony.SmsManager;

import androidx.core.content.ContextCompat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.medicmobile.webapp.mobile.SmsSender.Sms;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class SmsSenderTest {

	@Mock
	private SmsManager smsManager;

	@Mock
	EmbeddedBrowserActivity parentMock;

	@Captor
	ArgumentCaptor<Intent> argIntent;

	@Before
	public void setup() {
		MockitoAnnotations.openMocks(this);
		when(parentMock.registerReceiver(any(), any())).thenReturn(mock(Intent.class));
		doNothing().when(smsManager).sendMultipartTextMessage(any(), any(), any(), any(), any());
	}

	@Test
	public void send_withPermissions_sendsMultipartTextMessage() {
		try (
			MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
			MockedStatic<SmsManager> smsManagerStaticMock = mockStatic(SmsManager.class);
			MockedStatic<PendingIntent> pendingIntentMock = mockStatic(PendingIntent.class);
		) {
			//> GIVEN
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), anyString())).thenReturn(PERMISSION_GRANTED);

			ArrayList<String> expectedParts = new ArrayList<>();
			expectedParts.add("some");
			expectedParts.add("content");
			when(smsManager.divideMessage(any())).thenReturn(expectedParts);
			smsManagerStaticMock.when(() -> SmsManager.getDefault()).thenReturn(smsManager);

			SmsSender smsSender = new SmsSender(parentMock);
			Sms sms = new Sms("id-123", "+12234556543", "some content");

			//> WHEN
			smsSender.send(sms);

			//> THEN
			verify(smsManager).sendMultipartTextMessage(
				eq("+12234556543"),
				eq(null),
				eq(expectedParts),
				any(ArrayList.class),
				any(ArrayList.class)
			);
			pendingIntentMock.verify(() -> PendingIntent.getBroadcast(
				eq(parentMock),
				eq(0),
				argIntent.capture(),
				eq(FLAG_ONE_SHOT | FLAG_IMMUTABLE)
			), times(4));

			List<Intent> allIntents = argIntent.getAllValues();
			Intent sendIntentTxtPart1 = allIntents.get(0);
			assertEquals("medic.android.sms.SENDING_REPORT", sendIntentTxtPart1.getAction());
			assertEquals("id-123", sendIntentTxtPart1.getStringExtra("id"));
			assertEquals("+12234556543", sendIntentTxtPart1.getStringExtra("destination"));
			assertEquals("some content", sendIntentTxtPart1.getStringExtra("content"));
			assertEquals(0, sendIntentTxtPart1.getIntExtra("partIndex", -1));
			assertEquals(2, sendIntentTxtPart1.getIntExtra("totalParts", -1));

			Intent sendIntentTxtPart2 = allIntents.get(1);
			assertEquals("medic.android.sms.SENDING_REPORT", sendIntentTxtPart2.getAction());
			assertEquals("id-123", sendIntentTxtPart2.getStringExtra("id"));
			assertEquals("+12234556543", sendIntentTxtPart2.getStringExtra("destination"));
			assertEquals("some content", sendIntentTxtPart2.getStringExtra("content"));
			assertEquals(1, sendIntentTxtPart2.getIntExtra("partIndex", -1));
			assertEquals(2, sendIntentTxtPart2.getIntExtra("totalParts", -1));

			Intent deliveryIntentTxtPart1 = allIntents.get(2);
			assertEquals("medic.android.sms.DELIVERY_REPORT", deliveryIntentTxtPart1.getAction());
			assertEquals("id-123", deliveryIntentTxtPart1.getStringExtra("id"));
			assertEquals("+12234556543", deliveryIntentTxtPart1.getStringExtra("destination"));
			assertEquals("some content", deliveryIntentTxtPart1.getStringExtra("content"));
			assertEquals(0, deliveryIntentTxtPart1.getIntExtra("partIndex", -1));
			assertEquals(2, deliveryIntentTxtPart1.getIntExtra("totalParts", -1));

			Intent deliveryIntentTxtPart2 = allIntents.get(3);
			assertEquals("medic.android.sms.DELIVERY_REPORT", deliveryIntentTxtPart2.getAction());
			assertEquals("id-123", deliveryIntentTxtPart2.getStringExtra("id"));
			assertEquals("+12234556543", deliveryIntentTxtPart2.getStringExtra("destination"));
			assertEquals("some content", deliveryIntentTxtPart2.getStringExtra("content"));
			assertEquals(1, deliveryIntentTxtPart2.getIntExtra("partIndex", -1));
			assertEquals(2, deliveryIntentTxtPart2.getIntExtra("totalParts", -1));
		}
	}

	@Test
	public void resumeProcess_withResultOk_sendsMultipartTextMessage() {
		try (
			MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
			MockedStatic<SmsManager> smsManagerStaticMock = mockStatic(SmsManager.class);
			MockedStatic<PendingIntent> pendingIntentMock = mockStatic(PendingIntent.class);
		) {
			//> GIVEN
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), anyString())).thenReturn(PERMISSION_DENIED);

			ArrayList<String> expectedParts = new ArrayList<>();
			expectedParts.add("some content");
			when(smsManager.divideMessage(any())).thenReturn(expectedParts);
			smsManagerStaticMock.when(() -> SmsManager.getDefault()).thenReturn(smsManager);

			SmsSender smsSender = new SmsSender(parentMock);
			Sms sms = new Sms("id-123", "+12234556543", "some content");
			smsSender.send(sms);

			//> WHEN
			smsSender.resumeProcess(RESULT_OK);

			//> THEN
			verify(smsManager).sendMultipartTextMessage(
				eq("+12234556543"),
				eq(null),
				eq(expectedParts),
				any(ArrayList.class),
				any(ArrayList.class)
			);
			pendingIntentMock.verify(() -> PendingIntent.getBroadcast(
				eq(parentMock),
				eq(0),
				argIntent.capture(),
				eq(FLAG_ONE_SHOT | FLAG_IMMUTABLE)
			), times(2));

			List<Intent> allIntents = argIntent.getAllValues();
			Intent sendIntent = allIntents.get(0);
			assertEquals("medic.android.sms.SENDING_REPORT", sendIntent.getAction());
			assertEquals("id-123", sendIntent.getStringExtra("id"));
			assertEquals("+12234556543", sendIntent.getStringExtra("destination"));
			assertEquals("some content", sendIntent.getStringExtra("content"));
			assertEquals(0, sendIntent.getIntExtra("partIndex", -1));
			assertEquals(1, sendIntent.getIntExtra("totalParts", -1));

			Intent deliveryIntent = allIntents.get(1);
			assertEquals("medic.android.sms.DELIVERY_REPORT", deliveryIntent.getAction());
			assertEquals("id-123", deliveryIntent.getStringExtra("id"));
			assertEquals("+12234556543", deliveryIntent.getStringExtra("destination"));
			assertEquals("some content", deliveryIntent.getStringExtra("content"));
			assertEquals(0, deliveryIntent.getIntExtra("partIndex", -1));
			assertEquals(1, deliveryIntent.getIntExtra("totalParts", -1));
		}
	}

	@Test
	public void resumeProcess_withBadResult_logsWarningAndDoesntSendSms() {
		try (
			MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class);
		) {
			//> GIVEN
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), anyString())).thenReturn(PERMISSION_DENIED);

			SmsSender smsSender = new SmsSender(parentMock);
			Sms sms = new Sms("id-123", "+12234556543", "some content");
			smsSender.send(sms);

			//> WHEN
			smsSender.resumeProcess(RESULT_CANCELED);

			//> THEN
			verify(smsManager, never()).sendMultipartTextMessage(any(), any(), any(), any(), any());
			medicLogMock.verify(() -> MedicLog.trace(
				eq(parentMock),
				eq("SmsSender :: Cannot send sms without Send SMS permission. Sms ID=%s"),
				eq(sms.getId())
			));
		}
	}
}
