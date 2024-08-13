package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.app.Activity;
import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockSettings;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.IntStream;

@RunWith(RobolectricTestRunner.class)
public class OpenSettingsDialogFragmentTest {

	private OpenSettingsDialogFragment openSettingsDialogFragment;
	private Activity activity;
	private ArgumentCaptor<OnTouchListener> argsOnTouch;
	private ArgumentCaptor<Intent> argsStartActivity;

	@Before
	public void setup() {
		activity = mock(Activity.class, RETURNS_SMART_NULLS);
		doNothing().when(activity).finish();

		View view = mock(View.class);
		argsOnTouch = ArgumentCaptor.forClass(OnTouchListener.class);
		doNothing().when(view).setOnTouchListener(argsOnTouch.capture());

		MockSettings fragmentSettings = withSettings()
			.useConstructor()
			.defaultAnswer(CALLS_REAL_METHODS);

		openSettingsDialogFragment = mock(OpenSettingsDialogFragment.class, fragmentSettings);
		when(openSettingsDialogFragment.getActivity()).thenReturn(activity);
		when(openSettingsDialogFragment.getActivity().findViewById(R.id.wbvMain)).thenReturn(view);
		argsStartActivity = ArgumentCaptor.forClass(Intent.class);
		doNothing().when(openSettingsDialogFragment).startActivity(argsStartActivity.capture());

		openSettingsDialogFragment.onCreate(null);
	}

	private void tap(OnTouchListener onTouchListener, MotionEvent eventTap, int times) {
		IntStream
			.range(0, times)
			.forEach(i -> onTouchListener.onTouch(null, eventTap));
	}

	private void positionPointers(OnTouchListener onTouchListener, MotionEvent eventSwipe, float pointer1, float pointer2) {
		when(eventSwipe.getX(0)).thenReturn(pointer1);
		when(eventSwipe.getX(1)).thenReturn(pointer2);
		onTouchListener.onTouch(null, eventSwipe);
	}

	@Test
	public void onTouch_withRightGestures_opensSettingsDialog() {
		//> GIVEN
		MotionEvent eventTap = mock(MotionEvent.class);
		when(eventTap.getPointerCount()).thenReturn(1);
		when(eventTap.getActionMasked()).thenReturn(MotionEvent.ACTION_DOWN);

		MotionEvent eventSwipe = mock(MotionEvent.class);
		when(eventSwipe.getPointerCount()).thenReturn(2);

		//> WHEN
		OnTouchListener onTouchListener = argsOnTouch.getValue();
		tap(onTouchListener, eventTap, 6);

		when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
		positionPointers(onTouchListener, eventSwipe, (float) 261.81, (float) 264.99);

		when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
		positionPointers(onTouchListener, eventSwipe, (float) 800.90, (float) 850.13);

		//> THEN
		Intent intent = argsStartActivity.getValue();
		assertEquals(SettingsDialogActivity.class.getName(), intent.getComponent().getClassName());
		verify(activity).finish();
	}

	@Test
	public void onTouch_withNoSwipe_doesNotOpenSettingsDialog() {
		//> GIVEN
		MotionEvent eventTap = mock(MotionEvent.class);
		when(eventTap.getPointerCount()).thenReturn(1);
		when(eventTap.getActionMasked()).thenReturn(MotionEvent.ACTION_DOWN);

		MotionEvent eventSwipe = mock(MotionEvent.class);
		when(eventSwipe.getPointerCount()).thenReturn(2);

		//> WHEN
		OnTouchListener onTouchListener = argsOnTouch.getValue();
		tap(onTouchListener, eventTap, 6);

		when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
		positionPointers(onTouchListener, eventSwipe, (float) 261.81, (float) 264.99);

		//> THEN
		verify(openSettingsDialogFragment, never()).startActivity(any());
		verify(activity, never()).finish();
	}

	@Test
	public void onTouch_with1FingerSwipe_doesNotOpenSettingsDialog() {
		//> GIVEN
		MotionEvent eventTap = mock(MotionEvent.class);
		when(eventTap.getPointerCount()).thenReturn(1);
		when(eventTap.getActionMasked()).thenReturn(MotionEvent.ACTION_DOWN);

		MotionEvent eventSwipe = mock(MotionEvent.class);
		when(eventSwipe.getPointerCount()).thenReturn(1);

		//> WHEN
		OnTouchListener onTouchListener = argsOnTouch.getValue();
		tap(onTouchListener, eventTap, 6);

		when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
		positionPointers(onTouchListener, eventSwipe, (float) 261.81, (float) 264.99);

		when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
		positionPointers(onTouchListener, eventSwipe, (float) 800.90, (float) 850.13);

		//> THEN
		verify(openSettingsDialogFragment, never()).startActivity(any());
		verify(activity, never()).finish();
	}

	@Test
	public void onTouch_withNoEnoughTaps_doesNotOpenSettingsDialog() {
		//> GIVEN
		MotionEvent eventTap = mock(MotionEvent.class);
		when(eventTap.getPointerCount()).thenReturn(1);
		when(eventTap.getActionMasked()).thenReturn(MotionEvent.ACTION_DOWN);

		MotionEvent eventSwipe = mock(MotionEvent.class);
		when(eventSwipe.getPointerCount()).thenReturn(2);

		//> WHEN
		OnTouchListener onTouchListener = argsOnTouch.getValue();
		tap(onTouchListener, eventTap, 4);

		when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
		positionPointers(onTouchListener, eventSwipe, (float) 261.81, (float) 264.99);

		when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
		positionPointers(onTouchListener, eventSwipe, (float) 800.90, (float) 850.13);

		//> THEN
		verify(openSettingsDialogFragment, never()).startActivity(any());
		verify(activity, never()).finish();
	}

	@Test
	public void onTouch_with2FingerTaps_doesNotOpenSettingsDialog() {
		//> GIVEN
		MotionEvent eventTap = mock(MotionEvent.class);
		when(eventTap.getPointerCount()).thenReturn(2);
		when(eventTap.getActionMasked()).thenReturn(MotionEvent.ACTION_DOWN);

		MotionEvent eventSwipe = mock(MotionEvent.class);
		when(eventSwipe.getPointerCount()).thenReturn(2);

		//> WHEN
		OnTouchListener onTouchListener = argsOnTouch.getValue();
		tap(onTouchListener, eventTap, 6);

		when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
		positionPointers(onTouchListener, eventSwipe, (float) 261.81, (float) 264.99);

		when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
		positionPointers(onTouchListener, eventSwipe, (float) 800.90, (float) 850.13);

		//> THEN
		verify(openSettingsDialogFragment, never()).startActivity(any());
		verify(activity, never()).finish();
	}

	@Test
	public void onTouch_withTapTimeout_doesNotOpenSettingsDialog() {
		Clock startTime = Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC);
		Clock otherTapsTime = Clock.fixed(Instant.ofEpochMilli(1501), ZoneOffset.UTC);

		try (MockedStatic<Clock> mockClock = mockStatic(Clock.class)) {
			//> GIVEN
			MotionEvent eventTap = mock(MotionEvent.class);
			when(eventTap.getPointerCount()).thenReturn(1);
			when(eventTap.getActionMasked()).thenReturn(MotionEvent.ACTION_DOWN);

			MotionEvent eventSwipe = mock(MotionEvent.class);
			when(eventSwipe.getPointerCount()).thenReturn(2);

			mockClock.when(Clock::systemUTC).thenReturn(startTime);

			//> WHEN
			OnTouchListener onTouchListener = argsOnTouch.getValue();
			tap(onTouchListener, eventTap, 2);
			mockClock.when(Clock::systemUTC).thenReturn(otherTapsTime);
			tap(onTouchListener, eventTap, 4);

			when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
			positionPointers(onTouchListener, eventSwipe, (float) 261.81, (float) 264.99);

			when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
			positionPointers(onTouchListener, eventSwipe, (float) 800.90, (float) 850.13);

			//> THEN
			verify(openSettingsDialogFragment, never()).startActivity(any());
			verify(activity, never()).finish();
		}
	}
}
