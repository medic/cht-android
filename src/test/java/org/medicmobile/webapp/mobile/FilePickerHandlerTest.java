package org.medicmobile.webapp.mobile;

import static android.app.Activity.RESULT_OK;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class FilePickerHandlerTest {
	@Mock
	Activity mockContext;

	@Before
	public void setup() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	public void processResult_withResultOK_sendsFileCorrectly() {
		//> GIVEN
		ValueCallback<Uri[]> filePickerCallbackMock = mock(ValueCallback.class);
		FilePickerHandler filePickerHandler = new FilePickerHandler(mockContext);
		filePickerHandler.setFilePickerCallback(filePickerCallbackMock);

		Uri uri = Uri.parse("content://some/content/location.jpg");
		Intent intent = mock(Intent.class);
		when(intent.getData()).thenReturn(uri);

		//> WHEN
		filePickerHandler.processResult(RESULT_OK, intent);

		//> THEN
		verify(filePickerCallbackMock).onReceiveValue(eq(new Uri[]{ uri }));
	}

}
