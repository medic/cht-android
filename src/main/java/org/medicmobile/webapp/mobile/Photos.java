package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static android.app.Activity.RESULT_OK;
import static android.provider.MediaStore.ACTION_IMAGE_CAPTURE;
import static com.mvc.imagepicker.ImagePicker.getPickImageIntent;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.PROCESS_FILE;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;
import static org.medicmobile.webapp.mobile.Utils.intentHandlerAvailableFor;

class Photos {
	private final Activity a;

	private ValueCallback<Uri> uploadCallback;

	Photos(Activity a) {
		this.a = a;
	}

//> EXTERNAL METHODS
	boolean canHandle(String acceptType, boolean capture) {
		return acceptType.startsWith("image/") && (!capture || canStartCamera());
	}

	void chooser(ValueCallback<Uri> callback, boolean capture) {
		uploadCallback = callback;
		if(capture) takePhoto();
		else pickImage();
	}

	void process(int requestCode, int resultCode, Intent i) {
		if(uploadCallback == null) {
			warn(this, "uploadCallback is null for requestCode %s", requestCode);
			return;
		}

		Uri uri = i==null? null: i.getData();
		trace(this, "process() :: i=%s, resultCodeOK? %s, intentData=%s", i, resultCode == RESULT_OK, uri);

		if(i == null) {
			// can't really do anything with a null intent
		} else if(resultCode != RESULT_OK) {
			// something went wrong; error code was logged above
			uri = null;
		} else if(uri == null) {
			// photo was not saved to a file - we've probably been provided
			// with the raw data.
			Bundle extras = i == null ? null : i.getExtras();
			if(extras == null) {
				warn(this, "process() :: expected to find photo data in the intent, but no file URL or extras were included.");
			} else if(extras.containsKey("data")) {
				trace(this, "process() :: found data extra.  Will write to temp file.");
				try {
					uri = writeToTempFile((Bitmap) extras.get("data"));
					trace(this, "process() :: image written to temp file.  uri=%s", uri);
				} catch(Exception ex) {
					warn(ex, "process() :: erorr writing image data to temp file");
				}
			} else {
				warn(this, "process() :: expected to find photo data in the intent, but no file URL or 'data' extra was found.  Provided extras: %s",
						Arrays.toString(extras.keySet().toArray()));
			}
		} // else: photo was saved to a file - we've been provided with the URI already

		uploadCallback.onReceiveValue(uri);
		uploadCallback = null;
	}

//> PRIVATE HELPERS
	private void takePhoto() {
		a.startActivityForResult(cameraIntent(), PROCESS_FILE);
	}

	private void pickImage() {
		Intent i = getPickImageIntent(a, a.getString(R.string.promptChooseImage));
//		i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, Uri.fromFile(tempFile()));

		a.startActivityForResult(i, PROCESS_FILE);
	}

	private boolean canStartCamera() {
		return intentHandlerAvailableFor(a, cameraIntent());
	}

	private static Intent cameraIntent() {
		return new Intent(ACTION_IMAGE_CAPTURE);
	}

	private File tempFile(String extension) throws IOException {
		File imageCacheDir = new File(a.getCacheDir(), "form-photos");
		imageCacheDir.mkdirs();
		return File.createTempFile("photo", "." + extension, imageCacheDir);
	}

	private Uri writeToTempFile(Bitmap bitmap) throws IOException {
		File temp = null;
		FileOutputStream fos = null;
		try {
			temp = tempFile("jpg");
			fos = new FileOutputStream(temp);
			bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
			trace(this, "writeToTempFile() :: wrote temp file %s with %s bytes", temp, temp.length());
			return Uri.fromFile(temp);
		} finally {
			if(fos == null) try {
				fos.close();
			} catch(IOException ex) {
				warn(ex, "writeToTempFile() :: exception closing FileOutputStream to %s", temp);
			}
		}
	}
}
