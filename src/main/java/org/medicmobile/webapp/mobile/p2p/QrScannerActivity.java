package org.medicmobile.webapp.mobile.p2p;

import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

/**
	* Activity that launches ZXing QR scanner for P2P credential exchange.
	*
	* CHW scans the Supervisor's QR code to get WiFi hotspot credentials.
	* Result returned via onActivityResult with the scanned QR payload.
	*
	* Usage from the calling activity:
	*   Intent intent = new Intent(context, QrScannerActivity.class);
	*   startActivityForResult(intent, QrScannerActivity.REQUEST_CODE);
	*
	* Result extras:
	*   EXTRA_QR_RESULT  — the validated QR payload JSON string (on RESULT_OK)
	*   EXTRA_QR_ERROR   — error message string (on RESULT_CANCELED with error)
	*
	*   Timestamp within 10 minutes
	*   Type == "cht-p2p"
	*/
public class QrScannerActivity extends Activity {


	public static final String EXTRA_QR_RESULT = "qr_result";
	public static final String EXTRA_QR_ERROR = "qr_error";
	public static final int REQUEST_CODE = 42100;

	private boolean scannerLaunched = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
			scannerLaunched = savedInstanceState.getBoolean("scannerLaunched", false);
		}

		if (!scannerLaunched) {
			launchScanner();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putBoolean("scannerLaunched", scannerLaunched);
		super.onSaveInstanceState(outState);
	}

	/**
		* Launch the ZXing barcode scanner configured for QR codes only.
		*/
	@SuppressWarnings("deprecation") // IntentIntegrator deprecated but no replacement in zxing-android-embedded
	private void launchScanner() {
		scannerLaunched = true;

		IntentIntegrator integrator = new IntentIntegrator(this);
		integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
		integrator.setPrompt("Scan the Supervisor's QR code");
		integrator.setBeepEnabled(true);
		integrator.setOrientationLocked(true);
		integrator.setCaptureActivity(getCaptureActivityClass());
		integrator.initiateScan();
	}

	@SuppressWarnings("deprecation") // IntentIntegrator.parseActivityResult deprecated; no replacement
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

		if (scanResult != null) {
			String scannedContent = scanResult.getContents();

			if (scannedContent == null) {
				// User cancelled the scan
				log(this, "QR scan cancelled by user");
				setResult(RESULT_CANCELED);
				finish();
				return;
			}

			log(this, "QR code scanned, validating payload");
			handleScannedPayload(scannedContent);
		} else {
			// Unexpected result — not from ZXing
			super.onActivityResult(requestCode, resultCode, data);
			warn(this, "Unexpected onActivityResult — not a ZXing result");
			returnError("unexpected_scan_result");
		}
	}

	/**
		* Validate the scanned QR payload and return the result to the caller.
		* Validates QR timestamp and type fields.
		*/
	private void handleScannedPayload(String scannedContent) {
		QrValidation validation = QrCodeHelper.validateQrPayload(scannedContent);

		if (validation.isAccepted()) {
			log(this, "QR payload validated successfully");
			Intent resultIntent = new Intent();
			resultIntent.putExtra(EXTRA_QR_RESULT, scannedContent);
			setResult(RESULT_OK, resultIntent);
		} else {
			// The webapp shows the translated message for this code; the detail is for support.
			warn(this, "QR payload validation failed: " + validation.getDetail());
			returnError(validation.getCode());
		}

		finish();
	}

	/**
		* Return an error result to the caller.
		*/
	private void returnError(String error) {
		Intent resultIntent = new Intent();
		resultIntent.putExtra(EXTRA_QR_ERROR, error);
		setResult(RESULT_CANCELED, resultIntent);
		finish();
	}

	/**
		* Get the capture activity class. Uses the default ZXing capture activity.
		* Override this method in tests to provide a mock.
		*
		* @return the Activity class for QR capture
		*/
	protected Class<?> getCaptureActivityClass() {
		// Use the default ZXing embedded capture activity
		// This avoids requiring a separate ZXing app install
		try {
			return Class.forName("com.journeyapps.barcodescanner.CaptureActivity");
		} catch (ClassNotFoundException e) {
			warn(this, "ZXing CaptureActivity not found, falling back to default");
			return null;
		}
	}
}
