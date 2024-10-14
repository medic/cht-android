package org.medicmobile.webapp.mobile;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.RequestCode;

public class BarcodeScanner extends AppCompatActivity {

	private PreviewView scannerPreview;
	private ExecutorService cameraExecutor;
	private static final String TAG = "BarcodeScanner";

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.barcode_scanner);
		scannerPreview = findViewById(R.id.scannerPreview);
		cameraExecutor = Executors.newSingleThreadExecutor();

		if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
				== PackageManager.PERMISSION_GRANTED) {
			startCamera();
		} else {
			ActivityCompat.requestPermissions(this,
					new String[]{Manifest.permission.CAMERA},
					RequestCode.BARCODE_SCANNER.getCode());
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		cameraExecutor.shutdown();
	}

	private void startCamera() {
		ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
		cameraProviderFuture.addListener(() -> {
				try {
					ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
					bindPreview(cameraProvider);
				} catch (ExecutionException | InterruptedException e){
					Log.e(TAG, Objects.requireNonNull(e.getMessage()));
				}
			},
			ContextCompat.getMainExecutor(this));
	}

	private void bindPreview(ProcessCameraProvider cameraProvider){
		Preview preview = new Preview.Builder().build();
		preview.setSurfaceProvider(scannerPreview.getSurfaceProvider());
		CameraSelector cameraSelector = new CameraSelector.Builder()
				.requireLensFacing(CameraSelector.LENS_FACING_BACK)
				.build();

		ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
				.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
				.build();
		imageAnalysis.setAnalyzer(cameraExecutor, this::imageAnalyzer);

		cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
	}


	@OptIn(markerClass = ExperimentalGetImage.class)
	private void imageAnalyzer(ImageProxy imageProxy) {

		if (imageProxy.getImage() != null) {
			InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
			BarcodeScannerOptions options =
					new BarcodeScannerOptions.Builder()
							.setBarcodeFormats(
									//formats to support
									Barcode.FORMAT_QR_CODE,
									Barcode.FORMAT_AZTEC,
									Barcode.FORMAT_CODE_39,
									Barcode.FORMAT_PDF417,
									Barcode.FORMAT_EAN_13,
									Barcode.FORMAT_CODE_128,
									Barcode.FORMAT_UPC_E,
									Barcode.FORMAT_UPC_A,
									Barcode.FORMAT_EAN_8
									)
							.build();
			com.google.mlkit.vision.barcode.BarcodeScanner scanner = BarcodeScanning.getClient(options);

			Task<List<Barcode>> result = scanner.process(image);
			result.addOnSuccessListener(barcodes -> {
				for (Barcode barcode : barcodes) {
					String rawValue = barcode.getRawValue();
					if (rawValue != null) {
						returnIntent(rawValue);
					}
				}
			});
			result.addOnFailureListener(e -> {
				Log.e(TAG, Objects.requireNonNull(e.getMessage()));
				Toast.makeText(this, "Failed to scan image", Toast.LENGTH_SHORT).show();
			});

			result.addOnCompleteListener(task -> {
				imageProxy.close();
			});
		}
	}

	private void returnIntent(String barcode) {
		Intent resultIntent = new Intent();
		resultIntent.putExtra("CHT_QR_CODE", barcode);
		setResult(RESULT_OK, resultIntent);
		finish();
	}
}
