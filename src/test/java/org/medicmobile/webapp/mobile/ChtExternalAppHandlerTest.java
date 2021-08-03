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

import junit.framework.TestCase;

import org.json.JSONException;
import org.json.JSONObject;
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
public class ChtExternalAppHandlerTest extends TestCase {
	@Mock
	Activity mockContext;

	@Before
	public void setup() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	public void processResult_chtExternalAppActivity_returnsScriptCorrectly() {
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
				"  console.error('ChtExternalAppLauncher :: Error on sending intent response to CHT-Core webapp', error);" +
				"}";

		//> WHEN
		String script = chtExternalAppHandler.processResult(RESULT_OK, intent);

		//> THEN
		assertEquals(expectedScript, script);
	}

	@Test
	public void processResult_chtExternalAppActivity_returnsScriptCorrectlyWhenNoResponseData() {
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
				"  console.error('ChtExternalAppLauncher :: Error on sending intent response to CHT-Core webapp', error);" +
				"}";

		//> WHEN
		String script = chtExternalAppHandler.processResult(RESULT_OK, intent);

		//> THEN
		assertEquals(expectedScript, script);
	}

	@Test
	public void processResult_chtExternalAppActivity_catchesException() {
		//> GIVEN
		ChtExternalAppHandler chtExternalAppHandler = new ChtExternalAppHandler(mockContext);

		Intent intent = mock(Intent.class);
		when(intent.getExtras()).thenThrow(NullPointerException.class);

		//> WHEN
		String script = chtExternalAppHandler.processResult(RESULT_OK, intent);

		//> THEN
		assertEquals("console.error('Problem serialising the intent response: java.lang.NullPointerException')", script);
	}

	@Test
	public void processResult_badResultCode_throwsException() {
		//> GIVEN
		Intent intent = mock(Intent.class);
		ChtExternalAppHandler chtExternalAppHandler = new ChtExternalAppHandler(mockContext);

		//> WHEN
		try {
			String script = chtExternalAppHandler.processResult(RESULT_CANCELED, intent);
			fail("Expected exception did not occurred.");

		} catch (Exception exception) {
			assertEquals(
					"ChtExternalAppLauncherActivity :: Bad result code: 0. The external app " +
							"either: explicitly returned this result, didn't return any result or crashed during the operation.",
					exception.getMessage()
			);
		}
	}

	@Test
	public void startIntent_startsIntentCorrectly() throws JSONException {
		//> GIVEN
		try (MockedStatic<ChtExternalAppLauncher> launcherMock = mockStatic(ChtExternalAppLauncher.class)) {
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
			launcherMock.when(() -> ChtExternalAppLauncher.createIntent(any())).thenReturn(intent);

			ChtExternalApp chtExternalApp = new ChtExternalApp(
					"an.action",
					"a.category",
					"a.type",
					new JSONObject("{ \"name\": \"Eric\", \"id\": 1234 }"),
					Uri.parse("example://some:action"),
					"org.example",
					5
			);

			//> WHEN
			chtExternalAppHandler.startIntent(chtExternalApp);

			//> THEN
			launcherMock.verify(() -> ChtExternalAppLauncher.createIntent(chtExternalApp));
			verify(mockContext).startActivityForResult(eq(intent), eq(CHT_EXTERNAL_APP_ACTIVITY_REQUEST_CODE));
		}
	}

	@Test
	public void startIntent_catchesException() throws JSONException {
		//> GIVEN
		try (
			MockedStatic<ChtExternalAppLauncher> launcherMock = mockStatic(ChtExternalAppLauncher.class);
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class);
		) {
			ChtExternalAppHandler chtExternalAppHandler = new ChtExternalAppHandler(mockContext);
			doThrow(ActivityNotFoundException.class).when(mockContext).startActivityForResult(any(), anyInt());

			Intent intent = new Intent();
			intent.setAction("an.action");
			intent.addCategory("a.category");
			intent.setType("a.type");
			intent.putExtra("name", "Eric");
			launcherMock.when(() -> ChtExternalAppLauncher.createIntent(any())).thenReturn(intent);

			ChtExternalApp chtExternalApp = new ChtExternalApp(
					"an.action",
					"a.category",
					"a.type",
					new JSONObject("{ \"name\": \"Eric\" }"),
					null,
					null,
					null
			);

			//> WHEN
			chtExternalAppHandler.startIntent(chtExternalApp);

			//> THEN
			launcherMock.verify(() -> ChtExternalAppLauncher.createIntent(chtExternalApp));
			verify(mockContext).startActivityForResult(eq(intent), eq(CHT_EXTERNAL_APP_ACTIVITY_REQUEST_CODE));
			medicLogMock.verify(() -> MedicLog.error(
					any(),
					eq("ChtExternalAppLauncherActivity :: Error when starting the activity %s %s"),
					eq(intent),
					any())
			);

		}
	}

	@Test
	public void startIntent_requestsPermissions() throws JSONException {
		//> GIVEN
		try (
			MockedStatic<ChtExternalAppLauncher> launcherMock = mockStatic(ChtExternalAppLauncher.class);
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class);
			MockedStatic<ActivityCompat> activityCompatMock = mockStatic(ActivityCompat.class);
			MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
		) {
			ChtExternalAppHandler chtExternalAppHandler = new ChtExternalAppHandler(mockContext);
			doNothing().when(mockContext).startActivityForResult(any(), anyInt());
			launcherMock.when(() -> ChtExternalAppLauncher.createIntent(any())).thenReturn(new Intent());
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), anyString())).thenReturn(PERMISSION_DENIED);
			ChtExternalApp chtExternalApp = new ChtExternalApp(
					"an.action",
					"a.category",
					"a.type",
					new JSONObject("{ \"name\": \"Eric\" }"),
					null,
					null,
					null
			);

			//> WHEN
			chtExternalAppHandler.startIntent(chtExternalApp);

			//> THEN
			launcherMock.verify(() -> ChtExternalAppLauncher.createIntent(chtExternalApp));
			contextCompatMock.verify(() -> ContextCompat.checkSelfPermission(mockContext, READ_EXTERNAL_STORAGE));
			activityCompatMock.verify(() -> ActivityCompat.requestPermissions(mockContext, new String[]{READ_EXTERNAL_STORAGE}, ACCESS_STORAGE_PERMISSION_REQUEST_CODE));
			verify(mockContext, never()).startActivityForResult(any(), anyInt());

		}
	}

	@Test
	public void resumeActivity_startsIntentCorrectly() throws JSONException {
		//> GIVEN
		try (
			MockedStatic<ChtExternalAppLauncher> launcherMock = mockStatic(ChtExternalAppLauncher.class);
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class);
			MockedStatic<ActivityCompat> activityCompatMock = mockStatic(ActivityCompat.class);
			MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
		) {
			ChtExternalAppHandler chtExternalAppHandler = new ChtExternalAppHandler(mockContext);
			doNothing().when(mockContext).startActivityForResult(any(), anyInt());
			contextCompatMock.when(() -> ContextCompat.checkSelfPermission(any(), anyString())).thenReturn(PERMISSION_DENIED);

			Intent intent = new Intent();
			intent.setAction("an.action");
			intent.addCategory("a.category");
			intent.setType("a.type");
			intent.putExtra("name", "Eric");
			launcherMock.when(() -> ChtExternalAppLauncher.createIntent(any())).thenReturn(intent);

			ChtExternalApp chtExternalApp = new ChtExternalApp(
					"an.action",
					"a.category",
					"a.type",
					new JSONObject("{ \"name\": \"Eric\" }"),
					null,
					null,
					null
			);

			chtExternalAppHandler.startIntent(chtExternalApp);

			//> WHEN
			chtExternalAppHandler.resumeActivity();

			//> THEN
			launcherMock.verify(() -> ChtExternalAppLauncher.createIntent(chtExternalApp));
			contextCompatMock.verify(() -> ContextCompat.checkSelfPermission(mockContext, READ_EXTERNAL_STORAGE));
			activityCompatMock.verify(() -> ActivityCompat.requestPermissions(mockContext, new String[]{READ_EXTERNAL_STORAGE}, ACCESS_STORAGE_PERMISSION_REQUEST_CODE));
			verify(mockContext).startActivityForResult(eq(intent), eq(CHT_EXTERNAL_APP_ACTIVITY_REQUEST_CODE));

		}
	}

	@Test
	public void resumeActivity_doesNothingIfNoLastIntent() {
		//> GIVEN
		ChtExternalAppHandler chtExternalAppHandler = new ChtExternalAppHandler(mockContext);
		doNothing().when(mockContext).startActivityForResult(any(), anyInt());

		//> WHEN
		chtExternalAppHandler.resumeActivity();

		//> THEN
		verify(mockContext, never()).startActivityForResult(any(), anyInt());
	}
}
