package org.medicmobile.webapp.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import androidx.annotation.Nullable;

@SuppressLint("ValidFragment")
public class OpenSettingsDialogFragment extends Fragment {

	private final View view;
	private int fingerTabCount = 0;
	private long lastTimeTab = 0;
	private GestureHandler swipeGesture;
	private static final int TIME_BETWEEN_TABS = 500;

	private final OnTouchListener swipeRightListener = new OnTouchListener() {
		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouch(View view, MotionEvent event) {
			countTabs(event);
			onSwipe(event);
			return false;
		}
	};

	public OpenSettingsDialogFragment(View view) {
		this.view = view;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		view.setOnTouchListener(swipeRightListener);
	}

	private void countTabs(MotionEvent event) {
		if (event.getPointerCount() != 1 || event.getActionMasked() != MotionEvent.ACTION_DOWN) {
			return;
		}

		long currentTime = System.currentTimeMillis();
		fingerTabCount = lastTimeTab + TIME_BETWEEN_TABS >= currentTime ? fingerTabCount + 1 : 1;
		lastTimeTab = currentTime;
	}

	private boolean hasTabEnough() {
		// 5 tabs by the user + 1 for the swipe right.
		return fingerTabCount == 6;
	}

	private void onSwipe(MotionEvent event) {
		if (event.getPointerCount() != 2) {
			return;
		}

		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_POINTER_DOWN:
				swipeGesture = hasTabEnough() ? new GestureHandler(event) : null;
				return;
			case MotionEvent.ACTION_MOVE:
				if (swipeGesture != null && hasTabEnough() && swipeGesture.isSwipeRight(event)) {
					openSettings();
				}
				return;
			case MotionEvent.ACTION_POINTER_UP:
				swipeGesture = null;
				return;
		}
	}

	private void openSettings() {
		Activity activity = getActivity();
		startActivity(new Intent(activity, SettingsDialogActivity.class));
		activity.finish();
	}
}
