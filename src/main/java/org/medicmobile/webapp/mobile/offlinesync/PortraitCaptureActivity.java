package org.medicmobile.webapp.mobile.offlinesync;

import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import org.medicmobile.webapp.mobile.R;

/**
	* The scanner screen, with a way out that ZXing's own screen lacks.
	*
	* ZXing declares its own CaptureActivity as sensorLandscape, so this one's manifest entry locks
	* it to portrait, like the rest of the flow.
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
