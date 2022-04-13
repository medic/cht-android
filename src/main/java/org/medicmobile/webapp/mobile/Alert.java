package org.medicmobile.webapp.mobile;

import static android.os.VibrationEffect.DEFAULT_AMPLITUDE;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

public class Alert {
	private final MediaPlayer mediaPlayer;
	private final Vibrator vibrator;

	@SuppressWarnings("deprecation")
	@TargetApi(31)
	public Alert(Context context) {
		mediaPlayer = MediaPlayer.create(context, R.raw.sound_alert);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
			vibrator = vibratorManager.getDefaultVibrator();
		} else {
			vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
		}
	}

	@SuppressWarnings("deprecation")
	@TargetApi(26)
	public void trigger() {
		if (vibrator != null) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				vibrator.vibrate(VibrationEffect.createOneShot(1500L, DEFAULT_AMPLITUDE));
			} else {
				vibrator.vibrate(1500L);
			}
		}
		mediaPlayer.start();
	}
}
