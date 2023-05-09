package org.medicmobile.webapp.mobile.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.VibratorManager;

public class Vibrator {
	protected final Context context;

	protected Vibrator(Context context) {
		this.context = context;
	}

	public static Vibrator createInstance(Context context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
			return new NVibrator(context);
		} else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
			return new RVibrator(context);
		}
		return new Vibrator(context);
	}

	@TargetApi(26)
	public void vibrate(long milliseconds) {
		getVibrator().vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
	}

	@TargetApi(31)
	protected android.os.Vibrator getVibrator() {
		VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
		return vibratorManager.getDefaultVibrator();
	}

	static class RVibrator extends Vibrator {
		protected RVibrator(Context context) {
			super(context);
		}

		@Override
		protected android.os.Vibrator getVibrator() {
			return (android.os.Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
		}
	}

	static class NVibrator extends RVibrator {
		protected NVibrator(Context context) {
			super(context);
		}

		@Override
		public void vibrate(long milliseconds) {
			getVibrator().vibrate(milliseconds);
		}
	}
}
