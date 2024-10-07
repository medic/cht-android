package org.medicmobile.webapp.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import androidx.annotation.Nullable;

import java.time.Clock;

public class OpenSettingsDialogFragment extends Fragment {

	private int fingerTapCount = 0;
	private long lastTimeTap = 0;
	private GestureHandler swipeGesture;
	private static final int TIME_BETWEEN_TAPS = 500;
	private View mainView;
	private boolean isViewSetup = false;

	private final OnTouchListener onTouchListener = new OnTouchListener() {
		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouch(View view, MotionEvent event) {
			countTaps(event);
			onSwipe(event);
			return false;
		}
	};

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		setupViewWithRetry();
	}

	private boolean shouldSetupView() {
		return getActivity() != null && !isViewSetup;
	}

	private boolean setupView() {
		View view = getActivity().findViewById(R.id.wbvMain);
		if (view != null) {
			mainView = view;
			mainView.setOnTouchListener(onTouchListener);
			isViewSetup = true;
			return true;
		}

		return false;
	}

	private void setupViewWithRetry() {
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				if (shouldSetupView() && !setupView()) {
					new Handler(Looper.getMainLooper()).postDelayed(this, 100);
				}
			}
		});
	}

	private void countTaps(MotionEvent event) {
		if (event.getPointerCount() != 1 || event.getActionMasked() != MotionEvent.ACTION_DOWN) {
			return;
		}

		long currentTime = Clock.systemUTC().millis();
		fingerTapCount = lastTimeTap + TIME_BETWEEN_TAPS >= currentTime ? fingerTapCount + 1 : 1;
		lastTimeTap = currentTime;
	}

	private boolean hasTapEnough() {
		// 5 taps by the user + 1 for the swipe right.
		return fingerTapCount == 6;
	}

	private void onSwipe(MotionEvent event) {
		if (event.getPointerCount() != 2) {
			return;
		}

		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_POINTER_DOWN:
				swipeGesture = hasTapEnough() ? new GestureHandler(event) : null;
				return;
			case MotionEvent.ACTION_MOVE:
				if (swipeGesture != null && hasTapEnough() && swipeGesture.isSwipeRight(event)) {
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
