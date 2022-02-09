package org.medicmobile.webapp.mobile;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient.FileChooserParams;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RequestCode;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class FilePickerHandlerTest {
	@Mock
	Activity contextMock;

	@Before
	public void setup() {
		MockitoAnnotations.openMocks(this);
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
			verify(filePickerCallbackMock).onReceiveValue(eq(new Uri[]{uri}));
			medicLogMock.verify(() -> MedicLog.trace(
				eq(filePickerHandler),
				eq("FilePickerHandler :: Sending data back to webapp, URIs: %s"),
				eq("[content://some/content/image.jpg]")
			));
			medicLogMock.verify(() -> MedicLog.warn(
				eq(filePickerHandler),
				eq("FilePickerHandler :: Cannot send file back to webapp, filePickerCallback is null.")
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
				eq("FilePickerHandler :: Sending data back to webapp, URIs: %s"),
				eq("null"))
			);
			medicLogMock.verify(() -> MedicLog.warn(
				eq(filePickerHandler),
				eq("FilePickerHandler :: Cannot send file back to webapp, filePickerCallback is null.")
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
				eq("FilePickerHandler :: Sending data back to webapp, URIs: %s"),
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
				eq("FilePickerHandler :: Cannot send file back to webapp, filePickerCallback is null."))
			);
			medicLogMock.verify(() -> MedicLog.trace(
				eq(filePickerHandler),
				eq("FilePickerHandler :: Sending data back to webapp, URIs: %s")
			), never());
		}
	}

	@Test
	public void openPicker_withOneMimeType_startActivity() {
		//> GIVEN
		FileChooserParams fileChooserParamsMock = mock(FileChooserParams.class);
		when(fileChooserParamsMock.getAcceptTypes()).thenReturn(new String[]{ "image/*" });
		ValueCallback<Uri[]> filePickerCallbackMock = mock(ValueCallback.class);
		FilePickerHandler filePickerHandlerMock = spy(new FilePickerHandler(contextMock));

		//> WHEN
		filePickerHandlerMock.openPicker(fileChooserParamsMock, filePickerCallbackMock);

		//> THEN
		verify(filePickerHandlerMock).setFilePickerCallback(filePickerCallbackMock);
		ArgumentCaptor<Intent> argument = ArgumentCaptor.forClass(Intent.class);
		verify(contextMock).startActivityForResult(argument.capture(), eq(RequestCode.FILE_PICKER_ACTIVITY.getCode()));

		Intent actualIntent = argument.getValue();
		Set<String> categories = actualIntent.getCategories();
		Bundle extras = actualIntent.getExtras();

		assertEquals("image/*", actualIntent.getType());
		assertEquals(1, categories.size());
		assertTrue(categories.contains(Intent.CATEGORY_OPENABLE));
		assertEquals(1, extras.size());
		assertEquals(true, extras.get(Intent.EXTRA_LOCAL_ONLY));
	}

	@Test
	public void openPicker_withManyMimeTypes_startActivity() {
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

		Intent actualIntent = argument.getValue();
		Set<String> categories = actualIntent.getCategories();
		Bundle extras = actualIntent.getExtras();

		assertEquals("*/*", actualIntent.getType());
		assertEquals(1, categories.size());
		assertTrue(categories.contains(Intent.CATEGORY_OPENABLE));
		assertEquals(2, extras.size());
		assertEquals(true, extras.get(Intent.EXTRA_LOCAL_ONLY));
		assertEquals(mimeTypes, extras.get(Intent.EXTRA_MIME_TYPES));
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
				eq("FilePickerHandler :: MIME type is null, specify a MIME type.")
			));
			medicLogMock.verify(() -> MedicLog.trace(
				eq(filePickerHandlerMock),
				eq("FilePickerHandler :: Sending data back to webapp, URIs: %s"),
				eq("null")
			));
		}
	}
}
