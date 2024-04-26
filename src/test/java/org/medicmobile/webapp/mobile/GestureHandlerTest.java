package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class GestureHandlerTest {

	@Test
	public void isSwipeRight_withLongSwipeRight_returnsTrue() {
		MotionEvent eventPointerDown = mock(MotionEvent.class);
		when(eventPointerDown.getX(0)).thenReturn((float) 261.81);
		when(eventPointerDown.getX(1)).thenReturn((float) 264.99);
		GestureHandler gestureHandler = new GestureHandler(eventPointerDown);

		MotionEvent eventMove = mock(MotionEvent.class);
		when(eventMove.getX(0)).thenReturn((float) 800.90);
		when(eventMove.getX(1)).thenReturn((float) 850.13);

		assertTrue(gestureHandler.isSwipeRight(eventMove));
	}

	@Test
	public void isSwipeRight_withSortSwipeRight_returnsFalse() {
		MotionEvent eventPointerDown = mock(MotionEvent.class);
		when(eventPointerDown.getX(0)).thenReturn((float) 261.81);
		when(eventPointerDown.getX(1)).thenReturn((float) 264.99);
		GestureHandler gestureHandler = new GestureHandler(eventPointerDown);

		MotionEvent eventMove = mock(MotionEvent.class);
		when(eventMove.getX(0)).thenReturn((float) 361.81);
		when(eventMove.getX(1)).thenReturn((float) 364.99);

		assertFalse(gestureHandler.isSwipeRight(eventMove));
	}

	@Test
	public void isSwipeRight_withSwipeLeft_returnsFalse() {
		MotionEvent eventPointerDown = mock(MotionEvent.class);
		when(eventPointerDown.getX(0)).thenReturn((float) 861.81);
		when(eventPointerDown.getX(1)).thenReturn((float) 864.99);
		GestureHandler gestureHandler = new GestureHandler(eventPointerDown);

		MotionEvent eventMove = mock(MotionEvent.class);
		when(eventMove.getX(0)).thenReturn((float) 161.81);
		when(eventMove.getX(1)).thenReturn((float) 164.99);

		assertFalse(gestureHandler.isSwipeRight(eventMove));
	}
}
