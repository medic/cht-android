package org.medicmobile.webapp.mobile.p2p;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

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
 * Guards validated:
 *   G18 — Timestamp within 10 minutes
 *   G19 — type == "cht-p2p"
 */
public class QrScannerActivity extends Activity {

    private static final String TAG = "QrScannerActivity";

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Let IntentIntegrator parse the result
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if (scanResult != null) {
            String scannedContent = scanResult.getContents();

            if (scannedContent == null) {
                // User cancelled the scan
                Log.i(TAG, "QR scan cancelled by user");
                setResult(RESULT_CANCELED);
                finish();
                return;
            }

            Log.i(TAG, "QR code scanned, validating payload");
            handleScannedPayload(scannedContent);
        } else {
            // Unexpected result — not from ZXing
            super.onActivityResult(requestCode, resultCode, data);
            Log.w(TAG, "Unexpected onActivityResult — not a ZXing result");
            returnError("unexpected_scan_result");
        }
    }

    /**
     * Validate the scanned QR payload and return the result to the caller.
     * Checks G18 (timestamp) and G19 (type) guards.
     */
    private void handleScannedPayload(String scannedContent) {
        ValidationResult validation = QrCodeHelper.validateQrPayload(scannedContent);

        if (validation.isAccepted()) {
            Log.i(TAG, "QR payload validated successfully");
            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_QR_RESULT, scannedContent);
            setResult(RESULT_OK, resultIntent);
        } else {
            String reason = validation.getReason();
            Log.w(TAG, "QR payload validation failed: " + reason);
            Toast.makeText(this, "Invalid QR code: " + reason, Toast.LENGTH_LONG).show();
            returnError(reason);
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
            Log.w(TAG, "ZXing CaptureActivity not found, falling back to default");
            return null;
        }
    }
}
