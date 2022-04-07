package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
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
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
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
			.useConstructor(view)
			.defaultAnswer(CALLS_REAL_METHODS);

		openSettingsDialogFragment = mock(OpenSettingsDialogFragment.class, fragmentSettings);
		when(openSettingsDialogFragment.getActivity()).thenReturn(activity);
		argsStartActivity = ArgumentCaptor.forClass(Intent.class);
		doNothing().when(openSettingsDialogFragment).startActivity(argsStartActivity.capture());

		openSettingsDialogFragment.onCreate(null);
	}

	private void tap(OnTouchListener onTouchListener, MotionEvent eventTab, int times) {
		for (int i = 0; i < times; i++) {
			onTouchListener.onTouch(null, eventTab);
		}
	}

	private void positionPointers(OnTouchListener onTouchListener, MotionEvent eventSwipe, float pointer1, float pointer2) {
		when(eventSwipe.getX(0)).thenReturn(pointer1);
		when(eventSwipe.getX(1)).thenReturn(pointer2);
		onTouchListener.onTouch(null, eventSwipe);
	}

	@Test
	public void onTouch_withRightGestures_opensSettingsDialog() {
		//> GIVEN
		MotionEvent eventTab = mock(MotionEvent.class);
		when(eventTab.getPointerCount()).thenReturn(1);
		when(eventTab.getActionMasked()).thenReturn(MotionEvent.ACTION_DOWN);

		MotionEvent eventSwipe = mock(MotionEvent.class);
		when(eventSwipe.getPointerCount()).thenReturn(2);

		//> WHEN
		OnTouchListener onTouchListener = argsOnTouch.getValue();
		tap(onTouchListener, eventTab, 6);

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
		MotionEvent eventTab = mock(MotionEvent.class);
		when(eventTab.getPointerCount()).thenReturn(1);
		when(eventTab.getActionMasked()).thenReturn(MotionEvent.ACTION_DOWN);

		MotionEvent eventSwipe = mock(MotionEvent.class);
		when(eventSwipe.getPointerCount()).thenReturn(2);

		//> WHEN
		OnTouchListener onTouchListener = argsOnTouch.getValue();
		tap(onTouchListener, eventTab, 6);

		when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
		positionPointers(onTouchListener, eventSwipe, (float) 261.81, (float) 264.99);
		when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_UP);

		//> THEN
		verify(openSettingsDialogFragment, never()).startActivity(any());
		verify(activity, never()).finish();
	}

	@Test
	public void onTouch_with1FingerSwipe_doesNotOpenSettingsDialog() {
		//> GIVEN
		MotionEvent eventTab = mock(MotionEvent.class);
		when(eventTab.getPointerCount()).thenReturn(1);
		when(eventTab.getActionMasked()).thenReturn(MotionEvent.ACTION_DOWN);

		MotionEvent eventSwipe = mock(MotionEvent.class);
		when(eventSwipe.getPointerCount()).thenReturn(1);

		//> WHEN
		OnTouchListener onTouchListener = argsOnTouch.getValue();
		tap(onTouchListener, eventTab, 6);

		when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
		positionPointers(onTouchListener, eventSwipe, (float) 261.81, (float) 264.99);

		when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
		positionPointers(onTouchListener, eventSwipe, (float) 800.90, (float) 850.13);

		//> THEN
		verify(openSettingsDialogFragment, never()).startActivity(any());
		verify(activity, never()).finish();
	}

	@Test
	public void onTouch_withNoEnoughTabs_doesNotOpenSettingsDialog() {
		//> GIVEN
		MotionEvent eventTab = mock(MotionEvent.class);
		when(eventTab.getPointerCount()).thenReturn(1);
		when(eventTab.getActionMasked()).thenReturn(MotionEvent.ACTION_DOWN);

		MotionEvent eventSwipe = mock(MotionEvent.class);
		when(eventSwipe.getPointerCount()).thenReturn(2);

		//> WHEN
		OnTouchListener onTouchListener = argsOnTouch.getValue();
		tap(onTouchListener, eventTab, 4);

		when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
		positionPointers(onTouchListener, eventSwipe, (float) 261.81, (float) 264.99);

		when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
		positionPointers(onTouchListener, eventSwipe, (float) 800.90, (float) 850.13);

		//> THEN
		verify(openSettingsDialogFragment, never()).startActivity(any());
		verify(activity, never()).finish();
	}

	@Test
	public void onTouch_with2FingerTabs_doesNotOpenSettingsDialog() {
		//> GIVEN
		MotionEvent eventTab = mock(MotionEvent.class);
		when(eventTab.getPointerCount()).thenReturn(2);
		when(eventTab.getActionMasked()).thenReturn(MotionEvent.ACTION_DOWN);

		MotionEvent eventSwipe = mock(MotionEvent.class);
		when(eventSwipe.getPointerCount()).thenReturn(2);

		//> WHEN
		OnTouchListener onTouchListener = argsOnTouch.getValue();
		tap(onTouchListener, eventTab, 6);

		when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_POINTER_DOWN);
		positionPointers(onTouchListener, eventSwipe, (float) 261.81, (float) 264.99);

		when(eventSwipe.getActionMasked()).thenReturn(MotionEvent.ACTION_MOVE);
		positionPointers(onTouchListener, eventSwipe, (float) 800.90, (float) 850.13);

		//> THEN
		verify(openSettingsDialogFragment, never()).startActivity(any());
		verify(activity, never()).finish();
	}
}
