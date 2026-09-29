package org.medicmobile.webapp.mobile.offlinesync;

import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import org.medicmobile.webapp.mobile.R;

/**
	* The scanner screen, locked to portrait.
	*
	* ZXing declares its own CaptureActivity as sensorLandscape, so scanning a code turned the phone
	* sideways in the middle of a flow that is portrait everywhere else. Subclassing is the only way
	* to change it: the orientation comes from the manifest entry, not from the integrator.
	*/
public class PortraitCaptureActivity extends CaptureActivity {

	/**
		* Our layout rather than ZXing's, which is the preview alone.
		*
		* Leaving without a result is already handled as a cancelled scan, so the button only has to
		* finish the activity.
		*/
	@Override protected DecoratedBarcodeView initializeContent() {
		setContentView(R.layout.offline_sync_capture);
		findViewById(R.id.offlineSyncScannerCancel).setOnClickListener(view -> finish());
		return findViewById(R.id.zxing_barcode_scanner);
	}
}
