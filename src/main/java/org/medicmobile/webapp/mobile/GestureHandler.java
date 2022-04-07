package org.medicmobile.webapp.mobile;

import android.view.MotionEvent;

public class GestureHandler {

	private final float finger1AxisX;
	private final float finger2AxisX;
	private static final float MIN_SWIPE_LENGTH = 200;

	public GestureHandler(MotionEvent event) {
		this.finger1AxisX = event.getX(0);
		this.finger2AxisX = event.getX(1);
	}

	public boolean isSwipeRight(MotionEvent event) {
		float finger1diff = this.finger1AxisX - event.getX(0);
		float finger2diff = this.finger2AxisX - event.getX(1);

		return Math.abs(finger1diff) >= MIN_SWIPE_LENGTH
			&& Math.abs(finger2diff) >= MIN_SWIPE_LENGTH
			&& finger1diff < 0 && finger2diff < 0;
	}
}
