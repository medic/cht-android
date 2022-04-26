package org.medicmobile.webapp.mobile;

import android.content.Context;
import android.media.MediaPlayer;

import org.medicmobile.webapp.mobile.util.Vibrator;

public class Alert {
	private final MediaPlayer mediaPlayer;
	private final Vibrator vibrator;

	public Alert(Context context) {
		mediaPlayer = MediaPlayer.create(context, R.raw.sound_alert);
		vibrator = Vibrator.createInstance(context);
	}

	public void trigger() {
		vibrator.vibrate(1500L);
		mediaPlayer.start();
	}
}
