package org.medicmobile.webapp.mobile;

import android.app.Activity;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static android.content.pm.PackageManager.PERMISSION_DENIED;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import static org.junit.Assert.assertEquals;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.ACCESS_STORAGE_PERMISSION_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.CHT_EXTERNAL_APP_ACTIVITY_REQUEST_CODE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ChtExternalAppHandlerTest {
	@Mock
	Activity mockContext;

	@Before
	public void setup() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	public void processResult_withIntentExtras_returnsScriptCorrectly() {
		//> GIVEN
		ChtExternalAppHandler chtExternalAppHandler = new ChtExternalAppHandler(mockContext);

		Bundle secondLevelExtras = new Bundle();
		secondLevelExtras.putString("id", "abc-1234");
		secondLevelExtras.putBoolean("married", true);
		secondLevelExtras.putInt("age", 36);
		secondLevelExtras.putDouble("score", 20.5);

		Bundle extras = new Bundle();
		extras.putString("name", "Eric");
		extras.putBundle("details", secondLevelExtras);

		Intent intent = mock(Intent.class);
		when(intent.getExtras()).thenReturn(extras);

		String expectedJson = "{" +
					"\"name\":\"Eric\"," +
					"\"details\":{" +
						"\"id\":\"abc-1234\"," +
						"\"age\":36," +
						"\"score\":20.5," +
						"\"married\":true" +
					"}" +
				"}";

		String expectedScript = "try {" +
				"const api = window.CHTCore.AndroidApi;" +
				"if (api && api.v1 && api.v1.resolveCHTExternalAppResponse) {" +
				"  api.v1.resolveCHTExternalAppResponse(" + expectedJson + ");" +
				"}" +
				"} catch (error) { " +
				"  console.error('ChtExternalAppHandler :: Error on sending intent response to CHT-Core webapp', error);" +
				"}";

		//> WHEN
		String script = chtExternalAppHandler.processResult(RESULT_OK, intent);

		//> THEN
		assertEquals(expectedScript, script);
	}

	@Test
	public void processResult_withoutIntentExtras_returnsScriptCorrectly() {
		//> GIVEN
		ChtExternalAppHandler chtExternalAppHandler = new ChtExternalAppHandler(mockContext);

		Intent intent = mock(Intent.class);
		when(intent.getExtras()).thenReturn(null);

		String expectedScript = "try {" +
				"const api = window.CHTCore.AndroidApi;" +
				"if (api && api.v1 && api.v1.resolveCHTExternalAppResponse) {" +
				"  api.v1.resolveCHTExternalAppResponse(null);" +
				"}" +
				"} catch (error) { " +
				"  console.error('ChtExternalAppHandler :: Error on sending intent response to CHT-Core webapp', error);" +
				"}";

		//> WHEN
		String script = chtExternalAppHandler.processResult(RESULT_OK, intent);

		//> THEN
		assertEquals(expectedScript, script);
	}

	@Test
	public void processResult_withException_catchesException() {
		//> GIVEN
		ChtExternalAppHandler chtExternalAppHandler = new ChtExternalAppHandler(mockContext);

		Intent intent = mock(Intent.class);
		when(intent.getExtras()).thenThrow(NullPointerException.class);

		//> WHEN
		String script = chtExternalAppHandler.processResult(RESULT_OK, intent);

		//> THEN
		assertEquals("console.error('ChtExternalAppHandler :: Problem serialising the intent response.', java.lang.NullPointerException)", script);
	}

	@Test
	public void processResult_withBadResultCode_logError() {
		try (MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			//> GIVEN
			Intent intent = mock(Intent.class);
			ChtExternalAppHandler chtExternalAppHandler = new ChtExternalAppHandler(mockContext);
			String expectedMessageWarn = "ChtExternalAppHandler :: Bad result code: %s. The external app either: " +
					"explicitly returned this result, didn't return any result or crashed during the operation.";
			String expectedMessageConsole = "ChtExternalAppHandler :: Bad result code: " + RESULT_CANCELED + ". The external app either: " +
					"explicitly returned this result, didn't return any result or crashed during the operation.";

			//> WHEN
			String script = chtExternalAppHandler.processResult(RESULT_CANCELED, intent);

			//> THEN
			assertEquals("console.error('" + expectedMessageConsole + "')", script);
			medicLogMock.verify(() -> MedicLog.warn(eq(chtExternalAppHandler), eq(expectedMessageWarn), eq(RESULT_CANCELED)));
		}
	}

	@Test
	public void startIntent_withValidIntent_startsIntentCorrectly() {
		//> GIVEN
		ChtExternalApp chtExternalApp = mock(ChtExternalApp.class);
		ChtExternalAppHandler chtExternalAppHandler = new ChtExternalAppHandler(mockContext);
		doNothing().when(mockContext).startActivityForResult(any(), anyInt());

		Intent intent = new Intent();
		intent.setAction("an.action");
		intent.addCategory("a.category");
		intent.setType("a.type");
		intent.putExtra("name", "Eric");
		intent.putExtra("id", 1234);
		intent.setPackage("org.example");
		intent.setData(Uri.parse("example://some:action"));
		intent.setFlags(5);
		when(chtExternalApp.createIntent()).thenReturn(intent);

		//> WHEN
		chtExternalAppHandler.startIntent(chtExternalApp);

		//> THEN
		verify(chtExternalApp).createIntent();
		verify(mockContext).startActivityForResult(eq(intent), eq(CHT_EXTERNAL_APP_ACTIVITY_REQUEST_CODE));
	}

	@Test
	public void startIntent_withException_catchesException() {
		try (MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			//> GIVEN
			ChtExternalApp chtExternalApp = mock(ChtExternalApp.class);
			ChtExternalAppHandler chtExternalAppHandler = new ChtExternalAppHandler(mockContext);
			doThrow(ActivityNotFoundException.class).when(mockContext).startActivityForResult(any(), anyInt());

			Intent intent = new Intent();
			intent.setAction("an.action");
			intent.addCategory("a.category");
			intent.setType("a.type");
			intent.putExtra("name", "Eric");
			when(chtExternalApp.createIntent()).thenReturn(intent);

			//> WHEN
			chtExternalAppHandler.startIntent(chtExternalApp);

			//> THEN
			verify(chtExternalApp).createIntent();
			verify(mockContext).startActivityForResult(eq(intent), eq(CHT_EXTERNAL_APP_ACTIVITY_REQUEST_CODE));
			medicLogMock.verify(() -> MedicLog.error(
					any(),
					eq("ChtExternalAppHandler :: Error when starting the activity %s %s"),
					eq(intent),
					any())
			);

		}
	}

	@Test
	public void startIntent_withoutStoragePermissions_requestsPermissions() {
		try (
			MockedStatic<ActivityCompat> activityCompatMock = mockStatic(ActivityCompat.class);
			MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
		) {
			//> GIVEN
			ChtExternalApp chtExternalApp = mock(ChtExternalApp.class);
			ChtExternalAppHandler chtExternalAppHandler = new ChtExternalAppHandler(mockContext);
			doNothing().when(mockContext).startActivityForResult(any(), anyInt());
			when(chtExternalApp.createIntent()).thenReturn(new Intent());
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), anyString())).thenReturn(PERMISSION_DENIED);

			//> WHEN
			chtExternalAppHandler.startIntent(chtExternalApp);

			//> THEN
			verify(chtExternalApp).createIntent();
			contextCompatMock.verify(() -> ContextCompat.checkSelfPermission(mockContext, READ_EXTERNAL_STORAGE));
			activityCompatMock.verify(() -> ActivityCompat.requestPermissions(mockContext, new String[]{READ_EXTERNAL_STORAGE}, ACCESS_STORAGE_PERMISSION_REQUEST_CODE));
			verify(mockContext, never()).startActivityForResult(any(), anyInt());

		}
	}

	@Test
	public void resumeActivity_withLastIntent_startsIntentCorrectly() {
		try (
			MockedStatic<ActivityCompat> activityCompatMock = mockStatic(ActivityCompat.class);
			MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
		) {
			//> GIVEN
			ChtExternalApp chtExternalApp = mock(ChtExternalApp.class);
			ChtExternalAppHandler chtExternalAppHandler = new ChtExternalAppHandler(mockContext);
			doNothing().when(mockContext).startActivityForResult(any(), anyInt());
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), anyString())).thenReturn(PERMISSION_DENIED);

			Intent intent = new Intent();
			intent.setAction("an.action");
			intent.addCategory("a.category");
			intent.setType("a.type");
			intent.putExtra("name", "Eric");
			when(chtExternalApp.createIntent()).thenReturn(intent);

			chtExternalAppHandler.startIntent(chtExternalApp);

			//> WHEN
			chtExternalAppHandler.resumeActivity();

			//> THEN
			verify(chtExternalApp).createIntent();
			contextCompatMock.verify(() -> ContextCompat.checkSelfPermission(mockContext, READ_EXTERNAL_STORAGE));
			activityCompatMock.verify(() -> ActivityCompat.requestPermissions(mockContext, new String[]{READ_EXTERNAL_STORAGE}, ACCESS_STORAGE_PERMISSION_REQUEST_CODE));
			verify(mockContext).startActivityForResult(eq(intent), eq(CHT_EXTERNAL_APP_ACTIVITY_REQUEST_CODE));

		}
	}

	@Test
	public void resumeActivity_withoutIntent_doesNothing() {
		//> GIVEN
		ChtExternalAppHandler chtExternalAppHandler = new ChtExternalAppHandler(mockContext);
		doNothing().when(mockContext).startActivityForResult(any(), anyInt());

		//> WHEN
		chtExternalAppHandler.resumeActivity();

		//> THEN
		verify(mockContext, never()).startActivityForResult(any(), anyInt());
	}
}
