package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.AsyncTask;
import android.webkit.ValueCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static android.app.Activity.RESULT_OK;
import static android.provider.MediaStore.ACTION_IMAGE_CAPTURE;
import static com.mvc.imagepicker.ImagePicker.getImageFromResult;
import static com.mvc.imagepicker.ImagePicker.getPickImageIntent;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.GRAB_PHOTO_ACTIVITY_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;
import static org.medicmobile.webapp.mobile.Utils.intentHandlerAvailableFor;
import static org.medicmobile.webapp.mobile.Utils.showSpinner;

class PhotoGrabber {
	/** Max permitted file size for uploaded photos, in bytes. */
	private static final long MAX_FILE_SIZE = 1 << 15; // 32kb
	/** Smallest permitted image dimension in pixels */
	private static final int MINIMUM_DIMENSION = 240;
	/**
	 * This is actually the value of
	 * {@code ImagePicker.DEFAULT_REQUEST_CODE}.  This value is not exposed
	 * by the library, but needs to be supplied to
	 * {@code getImageFromResult()} so that it properly returns the full-
	 * size image.
	 */
	private static final int FAKE_REQUEST_CODE = 234;

	private final Activity a;

	private ValueCallback<Uri[]> uploadCallback;

	PhotoGrabber(Activity a) {
		this.a = a;
	}

//> EXTERNAL METHODS
	boolean canHandle(String[] acceptTypes, boolean capture) {
		if (!capture || canStartCamera()) {
			for (String acceptType : acceptTypes) {
				if (acceptType.startsWith("image/")) {
					return true;
				}
			}
		}
		return false;
	}

	void chooser(ValueCallback<Uri[]> callback, boolean capture) {
		uploadCallback = callback;
		if(capture) takePhoto();
		else pickImage();
	}

	void process(int requestCode, int resultCode, Intent i) {
		if(uploadCallback == null) {
			warn(this, "uploadCallback is null for requestCode %s", requestCode);
			return;
		}

		if(resultCode != RESULT_OK) {
			String data = i == null ? null : i.getDataString();
			trace(this, "process() :: non-OK result code :: resultCode=%s, i=%s, intentData=%s", i, resultCode, data);
		} else {
			try {
				handleBitmapCallback(getImageFromResult(a, FAKE_REQUEST_CODE, resultCode, i));
				return;
			} catch(Exception ex) {
				warn(ex, "process() :: error getting image from result");
			}
		}

		handleNullCallback();
	}

//> PRIVATE HELPERS
	private void handleNullCallback() {
		uploadCallback.onReceiveValue(null);
		uploadCallback = null;
	}

	private void handleBitmapCallback(final Bitmap bitmap) {
		trace(this, "handleBitmapCallback() :: bitmap=%s", bitmap);
		final ProgressDialog spinner = showSpinner(a, R.string.spnCompressingImage);
		AsyncTask.execute(new Runnable() {
			public void run() {
				Uri uri = null;
				try {
					uri = writeToFile(bitmap);
					trace(this, "process() :: image written to temp file.  uri=%s", uri);
				} catch(Exception ex) {
					warn(ex, "process() :: error writing image to temp file");
				}
				spinner.dismiss();

				uploadCallback.onReceiveValue(new Uri[]{ uri });
				uploadCallback = null;
			}
		});
	}

	private void takePhoto() {
		a.startActivityForResult(cameraIntent(), GRAB_PHOTO_ACTIVITY_REQUEST_CODE);
	}

	private void pickImage() {
		trace(this, "picking image intent");
		Intent i = getPickImageIntent(a, a.getString(R.string.promptChooseImage));
		trace(this, "starting activity :: %s", i);
		a.startActivityForResult(i, GRAB_PHOTO_ACTIVITY_REQUEST_CODE);
	}

	private boolean canStartCamera() {
		return intentHandlerAvailableFor(a, cameraIntent());
	}

	private static Intent cameraIntent() {
		return new Intent(ACTION_IMAGE_CAPTURE);
	}

	/**
	 * Write the supplied {@code Bitmap} to a random file location.
	 *
	 * Android documentation suggests that while these are nominally "temp"
	 * files, they may not be cleaned up automatically.  For now, this may
	 * be considered a useful feature.
	 *
	 * @return the {@code android.net.Uri} of the created file
	 */
	private Uri writeToFile(Bitmap bitmap) throws IOException {
		File temp = tempFile();
		long tempFileSize = -1;
		int quality;

		do {
			for(quality=90; quality>=30; quality-=10) {
				trace(this, "writeToFile() :: Attempting to write bitmap=%s (%sx%s) to file=%s at quality=%s; size of last write=%s...",
						bitmap, bitmap.getWidth(), bitmap.getHeight(), temp, quality, tempFileSize);

				writeToFile(temp, bitmap, quality);

				tempFileSize = temp.length();

				if(tempFileSize <= MAX_FILE_SIZE) {
					trace(this, "writeToFile() :: wrote temp file %s with %s bytes (max size = %s bytes)", temp, tempFileSize, MAX_FILE_SIZE);
					return Uri.fromFile(temp);
				}

			}

			bitmap = downsize(bitmap);

		} while(bitmap.getWidth() > MINIMUM_DIMENSION && bitmap.getHeight() > MINIMUM_DIMENSION);

		warn(this, "Failed to compress image small enough.  Final quality=%s, tempFileSize=%s", quality, tempFileSize);
		return null;
	}

	private void writeToFile(File file, Bitmap bitmap, int quality) throws IOException {
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(file);
			bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos);
		} finally {
			if(fos != null) try {
				fos.close();
			} catch(IOException ex) {
				warn(ex, "writeToFile() :: exception closing FileOutputStream to %s", file);
			}
		}
	}

	/**
	 * https://stackoverflow.com/questions/4837715/how-to-resize-a-bitmap-in-android#10703256
	 */
	private Bitmap downsize(Bitmap bm) {
		int width = bm.getWidth();
		int height = bm.getHeight();

		float scaleWidth = 0.5f;
		float scaleHeight = 0.5f;

		// CREATE A MATRIX FOR THE MANIPULATION
		Matrix matrix = new Matrix();
		// RESIZE THE BIT MAP
		matrix.postScale(scaleWidth, scaleHeight);

		// "RECREATE" THE NEW BITMAP
		Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
		bm.recycle();
		return resizedBitmap;
	}

	private File tempFile() throws IOException {
		File imageCacheDir = new File(a.getCacheDir(), "medic-form-photos");
		boolean mkdirSuccess = imageCacheDir.mkdirs();
		if(!mkdirSuccess) {
			log(this, "tempFile() :: imageCacheDir.mkdirs() failed. " +
					"This may cause problems with taking photos.");
		}
		return File.createTempFile("photo", ".jpg", imageCacheDir);
	}
}
