package org.medicmobile.webapp.mobile;

import android.content.*;
import android.media.*;

public class SoundAlert {
	private final MediaPlayer m;
	
	public SoundAlert(Context ctx) {
		m = MediaPlayer.create(ctx, R.raw.sound_alert);
	}

	public void trigger() {
		m.start();
	}
}
