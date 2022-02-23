package org.medicmobile.webapp.mobile;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static android.provider.MediaStore.ACTION_IMAGE_CAPTURE;
import static android.provider.MediaStore.ACTION_VIDEO_CAPTURE;
import static android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION;
import static android.provider.MediaStore.EXTRA_OUTPUT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RequestCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient.FileChooserParams;

import androidx.core.content.FileProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class FilePickerHandlerTest {
	@Mock
	Activity contextMock;

	@Before
	public void setup() {
		MockitoAnnotations.openMocks(this);
	}

	private void stubMethodsInContextMock() {
		String packageName = "org.medicmobile.webapp.mobile";
		ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
		applicationInfo.packageName = packageName;

		ActivityInfo activityInfo = mock(ActivityInfo.class);
		activityInfo.applicationInfo = applicationInfo;
		activityInfo.name = "TEST_ACTIVITY";

		ResolveInfo resolveInfo = mock(ResolveInfo.class);
		resolveInfo.activityInfo = activityInfo;

		PackageManager packageManager = mock(PackageManager.class);
		when(packageManager.resolveActivity(any(Intent.class), anyInt())).thenReturn(resolveInfo);

		when(contextMock.getPackageManager()).thenReturn(packageManager);
		when(contextMock.getExternalFilesDir(any())).thenReturn(mock(File.class));
		when(contextMock.getPackageName()).thenReturn(packageName);
	}

	@Test
	public void processResult_withResultOk_sendsFileUri() {
		try (MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			//> GIVEN
			ValueCallback<Uri[]> filePickerCallbackMock = mock(ValueCallback.class);
			FilePickerHandler filePickerHandler = new FilePickerHandler(contextMock);
			filePickerHandler.setFilePickerCallback(filePickerCallbackMock);

			Uri uri = Uri.parse("content://some/content/image.jpg");
			Intent intent = mock(Intent.class);
			when(intent.getData()).thenReturn(uri);

			//> WHEN
			filePickerHandler.processResult(RESULT_OK, intent);

			//> THEN
			verify(filePickerCallbackMock).onReceiveValue(eq(new Uri[]{ uri }));
			medicLogMock.verify(() -> MedicLog.trace(
				eq(filePickerHandler),
				eq("FilePickerHandler :: Sending data back to webapp, URI: %s"),
				eq("[content://some/content/image.jpg]")
			));
			medicLogMock.verify(() -> MedicLog.warn(
				eq(filePickerHandler),
				eq("FilePickerHandler :: Cannot send data back to webapp, filePickerCallback is null.")
			), never());
		}
	}

	@Test
	public void processResult_withNullUri_finishesPicker() {
		try (MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			//> GIVEN
			ValueCallback<Uri[]> filePickerCallbackMock = mock(ValueCallback.class);
			FilePickerHandler filePickerHandler = new FilePickerHandler(contextMock);
			filePickerHandler.setFilePickerCallback(filePickerCallbackMock);

			Intent intent = mock(Intent.class);
			when(intent.getData()).thenReturn(null);

			//> WHEN
			filePickerHandler.processResult(RESULT_OK, intent);

			//> THEN
			verify(filePickerCallbackMock).onReceiveValue(eq(null));
			medicLogMock.verify(() -> MedicLog.trace(
				eq(filePickerHandler),
				eq("FilePickerHandler :: Sending data back to webapp, URI: %s"),
				eq("null"))
			);
			medicLogMock.verify(() -> MedicLog.warn(
				eq(filePickerHandler),
				eq("FilePickerHandler :: Cannot send data back to webapp, filePickerCallback is null.")
			), never());
		}
	}

	@Test
	public void processResult_withBadResult_logWarningAndFinishesPicker() {
		try (MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			//> GIVEN
			ValueCallback<Uri[]> filePickerCallbackMock = mock(ValueCallback.class);
			FilePickerHandler filePickerHandler = new FilePickerHandler(contextMock);
			filePickerHandler.setFilePickerCallback(filePickerCallbackMock);

			Uri uri = Uri.parse("content://some/content/image.jpg");
			Intent intent = mock(Intent.class);
			when(intent.getData()).thenReturn(uri);

			//> WHEN
			filePickerHandler.processResult(RESULT_CANCELED, intent);

			//> THEN
			medicLogMock.verify(() -> MedicLog.warn(
				eq(filePickerHandler),
				eq("FilePickerHandler :: Bad result code: %s. " +
					"Either the user didn't select a file or there's an error during the operation."),
				eq(RESULT_CANCELED)
			));
			verify(filePickerCallbackMock).onReceiveValue(null);
			verify(intent, never()).getData();
			medicLogMock.verify(() -> MedicLog.trace(
				eq(filePickerHandler),
				eq("FilePickerHandler :: Sending data back to webapp, URI: %s"),
				eq("null")
			));
		}
	}

	@Test
	public void processResult_withNullPickerCallback_logWarning() {
		try (MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			//> GIVEN
			FilePickerHandler filePickerHandler = new FilePickerHandler(contextMock);

			Uri uri = Uri.parse("content://some/content/image.jpg");
			Intent intent = mock(Intent.class);
			when(intent.getData()).thenReturn(uri);

			//> WHEN
			filePickerHandler.processResult(RESULT_OK, intent);

			//> THEN
			medicLogMock.verify(() -> MedicLog.warn(
				eq(filePickerHandler),
				eq("FilePickerHandler :: Cannot send data back to webapp, filePickerCallback is null."))
			);
			medicLogMock.verify(() -> MedicLog.trace(
				eq(filePickerHandler),
				eq("FilePickerHandler :: Sending data back to webapp, URI: %s")
			), never());
		}
	}

	@Test
	public void openPicker_withImageMimeType_startActivity() {
		try (
			MockedStatic<File> fileMock = mockStatic(File.class);
			MockedStatic<FileProvider> fileProviderMock = mockStatic(FileProvider.class);
		) {
			//> GIVEN
			stubMethodsInContextMock();
			ValueCallback<Uri[]> filePickerCallbackMock = mock(ValueCallback.class);
			FilePickerHandler filePickerHandlerMock = spy(new FilePickerHandler(contextMock));

			FileChooserParams fileChooserParamsMock = mock(FileChooserParams.class);
			when(fileChooserParamsMock.getAcceptTypes()).thenReturn(new String[]{"image/*"});

			fileMock.when(() -> File.createTempFile(any(), any(), any())).thenReturn(mock(File.class));
			fileProviderMock.when(() -> FileProvider.getUriForFile(any(), any(), any())).thenReturn(Uri.parse("medic-1.jpg"));

			//> WHEN
			filePickerHandlerMock.openPicker(fileChooserParamsMock, filePickerCallbackMock);

			//> THEN
			verify(filePickerHandlerMock).setFilePickerCallback(filePickerCallbackMock);
			fileMock.verify(() -> File.createTempFile(matches("medic-\\d+$"), eq(".jpg"), any(File.class)));
			fileProviderMock.verify(() -> FileProvider.getUriForFile(eq(contextMock), eq("org.medicmobile.webapp.mobile.fileprovider"), any(File.class)));

			ArgumentCaptor<Intent> argument = ArgumentCaptor.forClass(Intent.class);
			verify(contextMock).startActivityForResult(argument.capture(), eq(RequestCode.FILE_PICKER_ACTIVITY.getCode()));

			Intent chooserIntent = argument.getValue();
			assertEquals(Intent.ACTION_CHOOSER, chooserIntent.getAction());
			Bundle chooserExtras = chooserIntent.getExtras();

			Intent[] initialIntents = (Intent[]) chooserExtras.get(Intent.EXTRA_INITIAL_INTENTS);
			assertEquals(1, initialIntents.length);
			Intent captureIntent = initialIntents[0];
			assertEquals(ACTION_IMAGE_CAPTURE, captureIntent.getAction());
			assertEquals("medic-1.jpg", captureIntent.getParcelableExtra(EXTRA_OUTPUT).toString());

			Intent pickerIntent = chooserExtras.getParcelable(Intent.EXTRA_INTENT);
			assertEquals("image/*", pickerIntent.getType());
			assertTrue(pickerIntent.getCategories().contains(Intent.CATEGORY_OPENABLE));
			assertTrue(pickerIntent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false));
		}
	}

	@Test
	public void openPicker_withFileException_startActivityWithoutCaptureIntent() {
		try (
			MockedStatic<File> fileMock = mockStatic(File.class);
			MockedStatic<FileProvider> fileProviderMock = mockStatic(FileProvider.class);
		) {
			//> GIVEN
			stubMethodsInContextMock();
			ValueCallback<Uri[]> filePickerCallbackMock = mock(ValueCallback.class);
			FilePickerHandler filePickerHandlerMock = spy(new FilePickerHandler(contextMock));

			FileChooserParams fileChooserParamsMock = mock(FileChooserParams.class);
			when(fileChooserParamsMock.getAcceptTypes()).thenReturn(new String[]{"image/*"});

			fileMock.when(() -> File.createTempFile(any(), any(), any())).thenThrow(IOException.class);

			//> WHEN
			filePickerHandlerMock.openPicker(fileChooserParamsMock, filePickerCallbackMock);

			//> THEN
			verify(filePickerHandlerMock).setFilePickerCallback(filePickerCallbackMock);
			fileMock.verify(() -> File.createTempFile(matches("medic-\\d+$"), eq(".jpg"), any(File.class)));
			fileProviderMock.verify(() -> FileProvider.getUriForFile(any(), any(), any()), never());

			ArgumentCaptor<Intent> argument = ArgumentCaptor.forClass(Intent.class);
			verify(contextMock).startActivityForResult(argument.capture(), eq(RequestCode.FILE_PICKER_ACTIVITY.getCode()));

			Intent chooserIntent = argument.getValue();
			assertEquals(Intent.ACTION_CHOOSER, chooserIntent.getAction());
			Bundle chooserExtras = chooserIntent.getExtras();

			Intent[] initialIntents = (Intent[]) chooserExtras.get(Intent.EXTRA_INITIAL_INTENTS);
			assertNull(initialIntents);

			Intent pickerIntent = chooserExtras.getParcelable(Intent.EXTRA_INTENT);
			assertEquals("image/*", pickerIntent.getType());
			assertTrue(pickerIntent.getCategories().contains(Intent.CATEGORY_OPENABLE));
			assertTrue(pickerIntent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false));
		}
	}

	@Test
	public void openPicker_withVideoMimeType_startActivity() {
		//> GIVEN
		stubMethodsInContextMock();
		ValueCallback<Uri[]> filePickerCallbackMock = mock(ValueCallback.class);
		FilePickerHandler filePickerHandlerMock = spy(new FilePickerHandler(contextMock));

		FileChooserParams fileChooserParamsMock = mock(FileChooserParams.class);
		when(fileChooserParamsMock.getAcceptTypes()).thenReturn(new String[]{ "video/*" });

		//> WHEN
		filePickerHandlerMock.openPicker(fileChooserParamsMock, filePickerCallbackMock);

		//> THEN
		verify(filePickerHandlerMock).setFilePickerCallback(filePickerCallbackMock);
		ArgumentCaptor<Intent> argument = ArgumentCaptor.forClass(Intent.class);
		verify(contextMock).startActivityForResult(argument.capture(), eq(RequestCode.FILE_PICKER_ACTIVITY.getCode()));

		Intent chooserIntent = argument.getValue();
		assertEquals(Intent.ACTION_CHOOSER, chooserIntent.getAction());
		Bundle chooserExtras = chooserIntent.getExtras();

		Intent[] initialIntents = (Intent[]) chooserExtras.get(Intent.EXTRA_INITIAL_INTENTS);
		assertEquals(1, initialIntents.length);
		Intent captureIntent = initialIntents[0];
		assertEquals(ACTION_VIDEO_CAPTURE, captureIntent.getAction());

		Intent pickerIntent = chooserExtras.getParcelable(Intent.EXTRA_INTENT);
		assertEquals("video/*", pickerIntent.getType());
		assertTrue(pickerIntent.getCategories().contains(Intent.CATEGORY_OPENABLE));
		assertTrue(pickerIntent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false));
	}

	@Test
	public void openPicker_withAudioMimeType_startActivity() {
		//> GIVEN
		stubMethodsInContextMock();
		ValueCallback<Uri[]> filePickerCallbackMock = mock(ValueCallback.class);
		FilePickerHandler filePickerHandlerMock = spy(new FilePickerHandler(contextMock));

		FileChooserParams fileChooserParamsMock = mock(FileChooserParams.class);
		when(fileChooserParamsMock.getAcceptTypes()).thenReturn(new String[]{ "audio/*" });

		//> WHEN
		filePickerHandlerMock.openPicker(fileChooserParamsMock, filePickerCallbackMock);

		//> THEN
		verify(filePickerHandlerMock).setFilePickerCallback(filePickerCallbackMock);
		ArgumentCaptor<Intent> argument = ArgumentCaptor.forClass(Intent.class);
		verify(contextMock).startActivityForResult(argument.capture(), eq(RequestCode.FILE_PICKER_ACTIVITY.getCode()));

		Intent chooserIntent = argument.getValue();
		assertEquals(Intent.ACTION_CHOOSER, chooserIntent.getAction());
		Bundle chooserExtras = chooserIntent.getExtras();

		Intent[] initialIntents = (Intent[]) chooserExtras.get(Intent.EXTRA_INITIAL_INTENTS);
		assertEquals(1, initialIntents.length);
		Intent captureIntent = initialIntents[0];
		assertEquals(RECORD_SOUND_ACTION, captureIntent.getAction());

		Intent pickerIntent = chooserExtras.getParcelable(Intent.EXTRA_INTENT);
		assertEquals("audio/*", pickerIntent.getType());
		assertTrue(pickerIntent.getCategories().contains(Intent.CATEGORY_OPENABLE));
		assertTrue(pickerIntent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false));
	}

	@Test
	public void openPicker_withoutActivityForCaptureIntent_startActivityWithoutCaptureIntent() {
		//> GIVEN
		when(contextMock.getPackageManager()).thenReturn(mock(PackageManager.class));
		ValueCallback<Uri[]> filePickerCallbackMock = mock(ValueCallback.class);
		FilePickerHandler filePickerHandlerMock = spy(new FilePickerHandler(contextMock));

		FileChooserParams fileChooserParamsMock = mock(FileChooserParams.class);
		when(fileChooserParamsMock.getAcceptTypes()).thenReturn(new String[]{ "video/*" });

		//> WHEN
		filePickerHandlerMock.openPicker(fileChooserParamsMock, filePickerCallbackMock);

		//> THEN
		verify(filePickerHandlerMock).setFilePickerCallback(filePickerCallbackMock);
		ArgumentCaptor<Intent> argument = ArgumentCaptor.forClass(Intent.class);
		verify(contextMock).startActivityForResult(argument.capture(), eq(RequestCode.FILE_PICKER_ACTIVITY.getCode()));

		Intent chooserIntent = argument.getValue();
		assertEquals(Intent.ACTION_CHOOSER, chooserIntent.getAction());
		Bundle chooserExtras = chooserIntent.getExtras();

		Intent[] initialIntents = (Intent[]) chooserExtras.get(Intent.EXTRA_INITIAL_INTENTS);
		assertNull(initialIntents);

		Intent pickerIntent = chooserExtras.getParcelable(Intent.EXTRA_INTENT);
		assertEquals("video/*", pickerIntent.getType());
		assertTrue(pickerIntent.getCategories().contains(Intent.CATEGORY_OPENABLE));
		assertTrue(pickerIntent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false));
	}

	@Test
	public void openPicker_withOtherMimeType_startActivityWithoutCaptureIntent() {
		//> GIVEN
		FileChooserParams fileChooserParamsMock = mock(FileChooserParams.class);
		when(fileChooserParamsMock.getAcceptTypes()).thenReturn(new String[]{ "other/*" });
		ValueCallback<Uri[]> filePickerCallbackMock = mock(ValueCallback.class);
		FilePickerHandler filePickerHandlerMock = spy(new FilePickerHandler(contextMock));

		//> WHEN
		filePickerHandlerMock.openPicker(fileChooserParamsMock, filePickerCallbackMock);

		//> THEN
		verify(filePickerHandlerMock).setFilePickerCallback(filePickerCallbackMock);
		ArgumentCaptor<Intent> argument = ArgumentCaptor.forClass(Intent.class);
		verify(contextMock).startActivityForResult(argument.capture(), eq(RequestCode.FILE_PICKER_ACTIVITY.getCode()));

		Intent chooserIntent = argument.getValue();
		assertEquals(Intent.ACTION_CHOOSER, chooserIntent.getAction());
		Bundle chooserExtras = chooserIntent.getExtras();
		assertNull(chooserExtras.get(Intent.EXTRA_INITIAL_INTENTS));

		Intent pickerIntent = chooserExtras.getParcelable(Intent.EXTRA_INTENT);
		assertEquals("other/*", pickerIntent.getType());
		assertTrue(pickerIntent.getCategories().contains(Intent.CATEGORY_OPENABLE));
		assertTrue(pickerIntent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false));
	}

	@Test
	public void openPicker_withManyMimeTypes_startActivityWithoutCaptureIntent() {
		//> GIVEN
		FileChooserParams fileChooserParamsMock = mock(FileChooserParams.class);
		String[] mimeTypes = new String[]{ "image/*", "video/mp4" };
		when(fileChooserParamsMock.getAcceptTypes()).thenReturn(mimeTypes);
		ValueCallback<Uri[]> filePickerCallbackMock = mock(ValueCallback.class);
		FilePickerHandler filePickerHandlerMock = spy(new FilePickerHandler(contextMock));

		//> WHEN
		filePickerHandlerMock.openPicker(fileChooserParamsMock, filePickerCallbackMock);

		//> THEN
		verify(filePickerHandlerMock).setFilePickerCallback(filePickerCallbackMock);
		ArgumentCaptor<Intent> argument = ArgumentCaptor.forClass(Intent.class);
		verify(contextMock).startActivityForResult(argument.capture(), eq(RequestCode.FILE_PICKER_ACTIVITY.getCode()));

		Intent chooserIntent = argument.getValue();
		assertEquals(Intent.ACTION_CHOOSER, chooserIntent.getAction());
		Bundle chooserExtras = chooserIntent.getExtras();
		assertNull(chooserExtras.get(Intent.EXTRA_INITIAL_INTENTS));

		Intent pickerIntent = chooserExtras.getParcelable(Intent.EXTRA_INTENT);
		assertEquals("*/*", pickerIntent.getType());
		assertTrue(pickerIntent.getCategories().contains(Intent.CATEGORY_OPENABLE));
		assertTrue(pickerIntent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false));
		assertEquals(
			Arrays.toString(mimeTypes),
			Arrays.toString(pickerIntent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
		);
	}

	@Test
	public void openPicker_withNullMimeType_finishesPicker() {
		try (MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			//> GIVEN
			FileChooserParams fileChooserParamsMock = mock(FileChooserParams.class);
			when(fileChooserParamsMock.getAcceptTypes()).thenReturn(null);
			ValueCallback<Uri[]> filePickerCallbackMock = mock(ValueCallback.class);
			FilePickerHandler filePickerHandlerMock = spy(new FilePickerHandler(contextMock));

			//> WHEN
			filePickerHandlerMock.openPicker(fileChooserParamsMock, filePickerCallbackMock);

			//> THEN
			verify(filePickerHandlerMock).setFilePickerCallback(filePickerCallbackMock);
			verify(contextMock, never()).startActivityForResult(any(), eq(RequestCode.FILE_PICKER_ACTIVITY.getCode()));
			verify(filePickerCallbackMock).onReceiveValue(null);
			medicLogMock.verify(() -> MedicLog.warn(
				eq(filePickerHandlerMock),
				eq("FilePickerHandler :: MIME type is null or empty, please specify a MIME type.")
			));
			medicLogMock.verify(() -> MedicLog.trace(
				eq(filePickerHandlerMock),
				eq("FilePickerHandler :: Sending data back to webapp, URI: %s"),
				eq("null")
			));
		}
	}

	@Test
	public void openPicker_withEmptyMimeType_finishesPicker() {
		try (MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class)) {
			//> GIVEN
			FileChooserParams fileChooserParamsMock = mock(FileChooserParams.class);
			when(fileChooserParamsMock.getAcceptTypes()).thenReturn(new String[]{});
			ValueCallback<Uri[]> filePickerCallbackMock = mock(ValueCallback.class);
			FilePickerHandler filePickerHandlerMock = spy(new FilePickerHandler(contextMock));

			//> WHEN
			filePickerHandlerMock.openPicker(fileChooserParamsMock, filePickerCallbackMock);

			//> THEN
			verify(filePickerHandlerMock).setFilePickerCallback(filePickerCallbackMock);
			verify(contextMock, never()).startActivityForResult(any(), eq(RequestCode.FILE_PICKER_ACTIVITY.getCode()));
			verify(filePickerCallbackMock).onReceiveValue(null);
			medicLogMock.verify(() -> MedicLog.warn(
				eq(filePickerHandlerMock),
				eq("FilePickerHandler :: MIME type is null or empty, please specify a MIME type.")
			));
			medicLogMock.verify(() -> MedicLog.trace(
				eq(filePickerHandlerMock),
				eq("FilePickerHandler :: Sending data back to webapp, URI: %s"),
				eq("null")
			));
		}
	}
}
