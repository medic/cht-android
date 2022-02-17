package org.medicmobile.webapp.mobile;

import static android.app.Activity.RESULT_OK;
import static android.provider.MediaStore.ACTION_IMAGE_CAPTURE;
import static android.provider.MediaStore.ACTION_VIDEO_CAPTURE;
import static android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION;
import static android.provider.MediaStore.EXTRA_OUTPUT;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RequestCode;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient.FileChooserParams;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.Arrays;

public class FilePickerHandler {

	private final Activity context;
	private ValueCallback<Uri[]> filePickerCallback;
	private Uri tempFileUri;

	public FilePickerHandler(Activity context) {
		this.context = context;
	}

	void setFilePickerCallback(ValueCallback<Uri[]> filePickerCallback) {
		this.filePickerCallback = filePickerCallback;
	}

	void setTempFileUri(Uri tempFileUri) {
		this.tempFileUri = tempFileUri;
	}

	void openPicker(FileChooserParams fileChooserParams, ValueCallback<Uri[]> filePickerCallback) {
		trace(this, "FilePickerHandler :: Start file capture activity");
		setFilePickerCallback(filePickerCallback);
		setTempFileUri(null);
		startFileCaptureActivity(fileChooserParams);
	}

	void processResult(int resultCode, Intent intent) {
		if (resultCode != RESULT_OK) {
			String message = "FilePickerHandler :: Bad result code: %s. " +
				"Either the user didn't select a file or there's an error during the operation.";
			warn(this, message, resultCode);
			sendDataToWebapp(null);
			return;
		}

		sendDataToWebapp(intent);
	}

//> PRIVATE

	/**
	 * Executes the file picker callback.
	 * The callback will give control back to WebView that automatically fetches the file by using a
	 * provided URI, finally it assigns the bytes to the form's input. Even if there's not URI (ie null),
	 * it needs to give control back to WebView, otherwise when clicking again on the form's input
	 * it won't open the file picker.
	 *
	 * @param intent Intent from activity's response.
	 */
	private void sendDataToWebapp(Intent intent) {
		if (this.filePickerCallback == null) {
			warn(this, "FilePickerHandler :: Cannot send data back to webapp, filePickerCallback is null.");
			return;
		}

		Uri uri = getSelectedFileUri(intent);
		trace(this, "FilePickerHandler :: Sending data back to webapp, URI: %s", uri);
		this.filePickerCallback.onReceiveValue(uri == null ? null : new Uri[] { uri });
		this.filePickerCallback = null;
	}

	private Uri getSelectedFileUri(Intent intent) {
		Uri uri = intent == null ? null : intent.getData();

		if (uri == null && this.tempFileUri != null) {
			uri = this.tempFileUri;
			setTempFileUri(null);
		}

		return uri;
	}

	private void startFileCaptureActivity(FileChooserParams fileChooserParams) {
		String[] mimeTypes = fileChooserParams.getAcceptTypes();
		if (mimeTypes == null) {
			warn(this, "FilePickerHandler :: MIME type is null, please specify a MIME type.");
			sendDataToWebapp(null);
			return;
		}

		trace(this, "FilePickerHandler :: Accepted MIME types: %s", Arrays.toString(mimeTypes));

		Intent pickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
		pickerIntent.addCategory(Intent.CATEGORY_OPENABLE);
		pickerIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
		Intent captureIntent = null;

		if (mimeTypes.length == 1) {
			String mimeType = mimeTypes[0];
			pickerIntent.setType(mimeType);
			captureIntent = getCaptureIntent(mimeType);
		} else {
			pickerIntent.setType("*/*");
			pickerIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
		}

		Intent chooserIntent = Intent.createChooser(pickerIntent, this.context.getString(R.string.promptChooseFile));
		// Ensure that there's an activity to handle the intent for capturing media.
		if (captureIntent != null && captureIntent.resolveActivity(this.context.getPackageManager()) != null) {
			chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{ captureIntent });
		}

		trace(this, "FilePickerHandler :: Starting activity with intents: chooser=%s, picker=%s, capture=%s",
			chooserIntent, pickerIntent, captureIntent);

		this.context.startActivityForResult(chooserIntent, RequestCode.FILE_PICKER_ACTIVITY.getCode());
	}

	private Intent getCaptureIntent(String mimeType) {
		if (mimeType.contains("video/")) {
			return new Intent(ACTION_VIDEO_CAPTURE);
		}

		if (mimeType.contains("audio/")) {
			return new Intent(RECORD_SOUND_ACTION);
		}

		if (mimeType.contains("image/")) {
			try {
				createTempImageFile();
				Intent imageIntent = new Intent(ACTION_IMAGE_CAPTURE);
				imageIntent.putExtra(EXTRA_OUTPUT, this.tempFileUri);
				return imageIntent;
			} catch (Exception ex) {
				warn(this, "FilePickerHandler :: Cannot create temporary file for image capture: %s", ex);
			}
		}

		trace(this, "FilePickerHandler :: No capture intent for MIME type: %s", mimeType);
		return null;
	}

	private void createTempImageFile() throws Exception {
		String imageFileName = "medic-" + System.currentTimeMillis();
		File storageDir = this.context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
		File file = File.createTempFile(imageFileName, ".jpg", storageDir);
		Uri uri = FileProvider.getUriForFile(this.context, this.context.getPackageName() + ".fileprovider", file);
		setTempFileUri(uri);
	}
}
