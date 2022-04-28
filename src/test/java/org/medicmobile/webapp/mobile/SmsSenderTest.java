package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.SEND_SMS;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_ONE_SHOT;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.telephony.SmsManager;

import androidx.core.content.ContextCompat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.medicmobile.webapp.mobile.SmsSender.Sms;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RunWith(RobolectricTestRunner.class)
public class SmsSenderTest {
	@Rule
	public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

	private static final String SENDING_REPORT = "medic.android.sms.SENDING_REPORT";
	private static final String DELIVERY_REPORT = "medic.android.sms.DELIVERY_REPORT";

	@Mock
	private PendingIntent mockPendingIntent;
	@Mock
	private SmsManager smsManager;
	@Mock
	private EmbeddedBrowserActivity parentMock;
	@Captor
	private ArgumentCaptor<ArrayList<PendingIntent>> sentIntentsArg;
	@Captor
	private ArgumentCaptor<ArrayList<PendingIntent>> deliveryIntentsArg;
	@Captor
	private ArgumentCaptor<Intent> broadcastIntentArg;

	private SmsSender smsSender;

	@Before
	public void setup() {
		smsSender = SmsSender.createInstance(parentMock);
	}

	@Test
	public void createInstance() {
		assertEquals(SmsSender.class, smsSender.getClass());
	}

	@Test
	@Config(sdk = 30)
	public void createInstance_RSmsSender() {
		assertEquals(SmsSender.RSmsSender.class, smsSender.getClass());
	}

	@Test
	@Config(sdk = 22)
	public void createInstance_LSmsSender() {
		assertEquals(SmsSender.LSmsSender.class, smsSender.getClass());
	}

	@Test
	public void send_withPermissions_sendsMultipartTextMessage() {
		try (
			MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
			MockedStatic<PendingIntent> pendingIntentMock = mockStatic(PendingIntent.class)
		) {
			//> GIVEN
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(eq(parentMock), eq(SEND_SMS))).thenReturn(PERMISSION_GRANTED);
			int expectedBroadcastFlags = FLAG_ONE_SHOT | FLAG_IMMUTABLE;
			mockGetBroadcast(pendingIntentMock, expectedBroadcastFlags);
			when(parentMock.getSystemService(android.telephony.SmsManager.class)).thenReturn(smsManager);
			Sms sms = new Sms("id-123", "+12234556543", "some content");
			ArrayList<String> expectedParts = new ArrayList<>(Arrays.asList("some", "content"));
			when(smsManager.divideMessage(eq(sms.getContent()))).thenReturn(expectedParts);

			//> WHEN
			smsSender.send(sms);

			//> THEN
			assertSendMultipartTextMessage(sms, expectedParts);

			pendingIntentMock.verify(() -> PendingIntent.getBroadcast(
				eq(parentMock),
				eq(0),
				broadcastIntentArg.capture(),
				eq(expectedBroadcastFlags)
			), times(4));
			List<Intent> allIntents = broadcastIntentArg.getAllValues();
			assertEquals(4, allIntents.size());
			assertBroadcastIntents(sms, SENDING_REPORT, allIntents.get(0), allIntents.get(1));
			assertBroadcastIntents(sms, DELIVERY_REPORT, allIntents.get(2), allIntents.get(3));
		}
	}

	@Test
	@Config(sdk = 30)
	public void send_withPermissions_sendsMultipartTextMessage_RSmsSender() {
		try (
			MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
			MockedStatic<SmsManager> smsManagerStaticMock = mockStatic(SmsManager.class);
			MockedStatic<PendingIntent> pendingIntentMock = mockStatic(PendingIntent.class)
		) {
			//> GIVEN
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(eq(parentMock), eq(SEND_SMS))).thenReturn(PERMISSION_GRANTED);
			int expectedBroadcastFlags = FLAG_ONE_SHOT | FLAG_IMMUTABLE;
			mockGetBroadcast(pendingIntentMock, expectedBroadcastFlags);
			smsManagerStaticMock.when(SmsManager::getDefault).thenReturn(smsManager);
			Sms sms = new Sms("id-123", "+12234556543", "some content");
			ArrayList<String> expectedParts = new ArrayList<>(Arrays.asList("some", "content"));
			when(smsManager.divideMessage(eq(sms.getContent()))).thenReturn(expectedParts);

			//> WHEN
			smsSender.send(sms);

			//> THEN
			assertSendMultipartTextMessage(sms, expectedParts);

			pendingIntentMock.verify(() -> PendingIntent.getBroadcast(
				eq(parentMock),
				eq(0),
				broadcastIntentArg.capture(),
				eq(expectedBroadcastFlags)
			), times(4));

			List<Intent> allIntents = broadcastIntentArg.getAllValues();
			assertEquals(4, allIntents.size());
			assertBroadcastIntents(sms, SENDING_REPORT, allIntents.get(0), allIntents.get(1));
			assertBroadcastIntents(sms, DELIVERY_REPORT, allIntents.get(2), allIntents.get(3));
		}
	}

	@Test
	@Config(sdk = 22)
	public void send_withPermissions_sendsMultipartTextMessage_LSmsSender() {
		try (
			MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
			MockedStatic<SmsManager> smsManagerStaticMock = mockStatic(SmsManager.class);
			MockedStatic<PendingIntent> pendingIntentMock = mockStatic(PendingIntent.class)
		) {
			//> GIVEN
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(eq(parentMock), eq(SEND_SMS))).thenReturn(PERMISSION_GRANTED);
			int expectedBroadcastFlags = FLAG_ONE_SHOT;
			mockGetBroadcast(pendingIntentMock, expectedBroadcastFlags);
			smsManagerStaticMock.when(SmsManager::getDefault).thenReturn(smsManager);
			Sms sms = new Sms("id-123", "+12234556543", "some content");
			ArrayList<String> expectedParts = new ArrayList<>(Arrays.asList("some", "content"));
			when(smsManager.divideMessage(eq(sms.getContent()))).thenReturn(expectedParts);

			//> WHEN
			smsSender.send(sms);

			//> THEN
			assertSendMultipartTextMessage(sms, expectedParts);

			pendingIntentMock.verify(() -> PendingIntent.getBroadcast(
				eq(parentMock),
				eq(0),
				broadcastIntentArg.capture(),
				eq(expectedBroadcastFlags)
			), times(4));

			List<Intent> allIntents = broadcastIntentArg.getAllValues();
			assertEquals(4, allIntents.size());
			assertBroadcastIntents(sms, SENDING_REPORT, allIntents.get(0), allIntents.get(1));
			assertBroadcastIntents(sms, DELIVERY_REPORT, allIntents.get(2), allIntents.get(3));
		}
	}

	@Test
	public void resumeProcess_withResultOk_sendsMultipartTextMessage() {
		try (
			MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
			MockedStatic<PendingIntent> pendingIntentMock = mockStatic(PendingIntent.class)
		) {
			//> GIVEN
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), anyString())).thenReturn(PERMISSION_DENIED);
			int expectedBroadcastFlags = FLAG_ONE_SHOT | FLAG_IMMUTABLE;
			mockGetBroadcast(pendingIntentMock, expectedBroadcastFlags);
			when(parentMock.getSystemService(android.telephony.SmsManager.class)).thenReturn(smsManager);
			Sms sms = new Sms("id-123", "+12234556543", "some content");
			ArrayList<String> expectedParts = new ArrayList<>(Collections.singleton("some content"));
			when(smsManager.divideMessage(sms.getContent())).thenReturn(expectedParts);

			//> WHEN
			smsSender.send(sms);
			smsSender.resumeProcess(Activity.RESULT_OK);

			//> THEN
			assertSendMultipartTextMessage(sms, expectedParts);

			pendingIntentMock.verify(() -> PendingIntent.getBroadcast(
				eq(parentMock),
				eq(0),
				broadcastIntentArg.capture(),
				eq(expectedBroadcastFlags)
			), times(2));
			List<Intent> allIntents = broadcastIntentArg.getAllValues();
			assertEquals(2, allIntents.size());
			assertBroadcastIntents(sms, SENDING_REPORT, allIntents.get(0));
			assertBroadcastIntents(sms, DELIVERY_REPORT, allIntents.get(1));
		}
	}

	@Test
	public void resumeProcess_withBadResult_logsWarningAndDoesntSendSms() {
		try (
			MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)
		) {
			//> GIVEN
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), anyString())).thenReturn(PERMISSION_DENIED);
			Sms sms = new Sms("id-123", "+12234556543", "some content");
			smsSender.send(sms);

			//> WHEN
			smsSender.resumeProcess(Activity.RESULT_CANCELED);

			//> THEN
			verify(smsManager, never()).sendMultipartTextMessage(any(), any(), any(), any(), any());
			medicLogMock.verify(() -> MedicLog.trace(
				eq(parentMock),
				eq("SmsSender :: Cannot send sms without Send SMS permission. Sms ID=%s"),
				eq(sms.getId())
			));
		}
	}

	private void mockGetBroadcast(MockedStatic<PendingIntent> pendingIntentMock, int expectedBroadcastFlags) {
		pendingIntentMock.when(() -> PendingIntent.getBroadcast(
			eq(parentMock),
			eq(0),
			any(Intent.class),
			eq(expectedBroadcastFlags)
		)).thenReturn(mockPendingIntent);
	}

	private void assertSendMultipartTextMessage(Sms sms, ArrayList<String> expectedParts) {
		verify(smsManager).sendMultipartTextMessage(
			eq(sms.getDestination()),
			isNull(),
			eq(expectedParts),
			sentIntentsArg.capture(),
			deliveryIntentsArg.capture()
		);
		List<PendingIntent> expectedIntents = expectedParts.stream()
			.map(ignored -> mockPendingIntent)
			.collect(Collectors.toList());
		assertEquals(expectedIntents, sentIntentsArg.getValue());
		assertEquals(expectedIntents, deliveryIntentsArg.getValue());
	}

	private void assertBroadcastIntents(Sms sms, String expectedAction, Intent... deliveryIntents) {
		int deliveryCount = deliveryIntents.length;
		IntStream.range(0, deliveryCount).forEach(index -> {
			Intent deliveryIntent = deliveryIntents[index];
			assertEquals(expectedAction, deliveryIntent.getAction());
			assertEquals(sms.getId(), deliveryIntent.getStringExtra("id"));
			assertEquals(sms.getDestination(), deliveryIntent.getStringExtra("destination"));
			assertEquals(sms.getContent(), deliveryIntent.getStringExtra("content"));
			assertEquals(index, deliveryIntent.getIntExtra("partIndex", -1));
			assertEquals(deliveryCount, deliveryIntent.getIntExtra("totalParts", -1));
		});
	}
}
