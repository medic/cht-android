package org.medicmobile.webapp.mobile;

import android.content.*;
import android.media.*;
import android.os.*;

/**
 * Native sound + vibration alert.
 */
public class Alert {
	private final MediaPlayer m;
	private final Vibrator v;

	public Alert(Context ctx) {
		m = MediaPlayer.create(ctx, R.raw.sound_alert);
		v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
	}

	public void trigger() {
		if(v != null) v.vibrate(1500L);
		m.start();
	}
}
