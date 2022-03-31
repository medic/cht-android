package org.medicmobile.webapp.mobile;

import android.view.MotionEvent;

public class GestureHandler {

	private float finger1AxisX;
	private float finger2AxisX;
	private static final float MIN_SWIPE_LENGTH = 200;

	public GestureHandler(float finger1AxisX, float finger2AxisX) {
		this.finger1AxisX = finger1AxisX;
		this.finger2AxisX = finger2AxisX;
	}

	public boolean isSwipeRight(MotionEvent event) {
		float finger1diff = this.finger1AxisX - event.getX(0);
		float finger2diff = this.finger2AxisX - event.getX(1);

		return Math.abs(finger1diff) >= MIN_SWIPE_LENGTH
			&& Math.abs(finger2diff) >= MIN_SWIPE_LENGTH
			&& finger1diff < 0 && finger2diff < 0;
	}
}
