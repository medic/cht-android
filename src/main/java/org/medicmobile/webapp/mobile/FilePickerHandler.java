package org.medicmobile.webapp.mobile;

import static android.app.Activity.RESULT_OK;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient.FileChooserParams;

import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RequestCode;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

import java.util.Arrays;

public class FilePickerHandler {

	private final Activity context;
	private ValueCallback<Uri[]> filePickerCallback;

	public FilePickerHandler(Activity context) {
		this.context = context;
	}

	void setFilePickerCallback(ValueCallback<Uri[]> filePickerCallback) {
		this.filePickerCallback = filePickerCallback;
	}

	void openPicker(FileChooserParams fileChooserParams, ValueCallback<Uri[]> filePickerCallback) {
		trace(this, "FilePickerHandler :: Open file picker");
		setFilePickerCallback(filePickerCallback);
		startFilePicker(fileChooserParams.getAcceptTypes());
	}

	void processResult(int resultCode, Intent intent) {
		if (resultCode != RESULT_OK) {
			String message = "FilePickerHandler :: Bad result code: %s. " +
				"Either the user didn't select a file or there's an error during the operation.";
			warn(this, message, resultCode);
			sendDataToWebapp(null);
			return;
		}

		Uri uri = intent.getData();
		sendDataToWebapp(uri == null ? null : new Uri[]{ uri });
	}

//> PRIVATE

	/**
	 * Executes the file picker callback.
	 * The callback will give back control to WebView that automatically fetches the file by using the
	 * provided URI and assigns the bytes to the form's input. Even if there's not URI (ie null)
	 * it needs to give back control to WebView, otherwise when clicking again on the form's input
	 * it won't open the file picker.
	 *
	 * @param uris Array of files' URI
	 */
	private void sendDataToWebapp(Uri[] uris) {
		if (this.filePickerCallback == null) {
			warn(this, "FilePickerHandler :: Cannot send data back to webapp, filePickerCallback is null.");
			return;
		}

		trace(this, "FilePickerHandler :: Sending data back to webapp, URIs: %s", Arrays.toString(uris));
		this.filePickerCallback.onReceiveValue(uris);
		this.filePickerCallback = null;
	}

	private void startFilePicker(String[] mimeTypes) {
		if (mimeTypes == null) {
			warn(this, "FilePickerHandler :: MIME type is null, specify a MIME type.");
			sendDataToWebapp(null);
			return;
		}

		trace(this, "FilePickerHandler :: Accepted MIME types: %s", Arrays.toString(mimeTypes));
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

		if (mimeTypes.length == 1) {
			intent.setType(mimeTypes[0]);
		} else {
			intent.setType("*/*");
			intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
		}

		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
		trace(this, "FilePickerHandler :: Starting activity to open picker: %s", intent);
		this.context.startActivityForResult(intent, RequestCode.FILE_PICKER_ACTIVITY.getCode());
	}
}
