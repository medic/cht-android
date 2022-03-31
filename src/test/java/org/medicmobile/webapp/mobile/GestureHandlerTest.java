package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class GestureHandlerTest {

	@Test
	public void isSwipeRight_withLongSwipeRight_returnsTrue() {
		//> GIVEN
		MotionEvent event = mock(MotionEvent.class);
		when(event.getX(0)).thenReturn((float) 800.90);
		when(event.getX(1)).thenReturn((float) 850.13);
		GestureHandler gestureHandler = new GestureHandler((float) 261.81, (float) 264.99);

		//> THEN
		assertTrue(gestureHandler.isSwipeRight(event));
	}

	@Test
	public void isSwipeRight_withSortSwipeRight_returnsFalse() {
		//> GIVEN
		MotionEvent event = mock(MotionEvent.class);
		when(event.getX(0)).thenReturn((float) 361.81);
		when(event.getX(1)).thenReturn((float) 364.99);
		GestureHandler gestureHandler = new GestureHandler((float) 261.81, (float) 264.99);

		//> THEN
		assertFalse(gestureHandler.isSwipeRight(event));
	}

	@Test
	public void isSwipeRight_withSwipeLeft_returnsFalse() {
		//> GIVEN
		MotionEvent event = mock(MotionEvent.class);
		when(event.getX(0)).thenReturn((float) 161.81);
		when(event.getX(1)).thenReturn((float) 164.99);
		GestureHandler gestureHandler = new GestureHandler((float) 861.81, (float) 864.99);

		//> THEN
		assertFalse(gestureHandler.isSwipeRight(event));
	}
}
