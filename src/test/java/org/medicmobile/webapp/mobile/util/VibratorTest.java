package org.medicmobile.webapp.mobile.util;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.VibratorManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
public class VibratorTest {
	@Rule
	public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

	private static final long MILLIS = 1500L;

	@Mock
	private Context mockContext;
	@Mock
	private VibratorManager mockVibratorManager;
	@Mock
	private android.os.Vibrator mockVibrator;

	private Vibrator vibrator;

	@Before
	public void setup() {
		vibrator = Vibrator.createInstance(mockContext);
	}

	@Test
	public void createInstance() {
		assertEquals(Vibrator.class, vibrator.getClass());
	}

	@Test
	@Config(sdk = 30)
	public void createInstance_RVibrator() {
		assertEquals(Vibrator.RVibrator.class, vibrator.getClass());
	}

	@Test
	@Config(sdk = 25)
	public void createInstance_NVibrator() {
		assertEquals(Vibrator.NVibrator.class, vibrator.getClass());
	}

	@Test
	public void vibrate() {
		when(mockContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)).thenReturn(mockVibratorManager);
		when(mockVibratorManager.getDefaultVibrator()).thenReturn(mockVibrator);

		vibrator.vibrate(MILLIS);

		VibrationEffect expectedEffect = VibrationEffect.createOneShot(MILLIS, VibrationEffect.DEFAULT_AMPLITUDE);
		verify(mockVibrator).vibrate(ArgumentMatchers.eq(expectedEffect));
	}

	@Test
	@Config(sdk = 30)
	public void vibrate_RVibrator() {
		when(mockContext.getSystemService(Context.VIBRATOR_SERVICE)).thenReturn(mockVibrator);

		vibrator.vibrate(MILLIS);

		VibrationEffect expectedEffect = VibrationEffect.createOneShot(MILLIS, VibrationEffect.DEFAULT_AMPLITUDE);
		verify(mockVibrator).vibrate(ArgumentMatchers.eq(expectedEffect));
	}

	@Test
	@Config(sdk = 25)
	public void vibrate_NVibrator() {
		when(mockContext.getSystemService(Context.VIBRATOR_SERVICE)).thenReturn(mockVibrator);

		vibrator.vibrate(MILLIS);

		verify(mockVibrator).vibrate(ArgumentMatchers.eq(MILLIS));
	}
}

