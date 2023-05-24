package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.content.SharedPreferences;
import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockSettings;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class SettingsStoreTest {

	private SharedPreferences sharedPreferences;
	private SettingsStore settingsStore;
	private static final String APP_URL = "https://project-abc.medic.org";

	@Before
	public void setup() {
		sharedPreferences = mock(SharedPreferences.class);

		MockSettings mockSettings = withSettings()
			.useConstructor(sharedPreferences)
			.defaultAnswer(CALLS_REAL_METHODS);

		settingsStore = mock(SettingsStore.class, mockSettings);
		when(settingsStore.getAppUrl()).thenReturn(APP_URL);
	}

	@Test
	public void getUrlToLoad_withValidUrl_returnsUrlString() {
		Uri uri = Uri.parse("https://project-zxy.medic.org");

		assertEquals("https://project-zxy.medic.org", settingsStore.getUrlToLoad(uri));
	}

	@Test
	public void getUrlToLoad_withInvalidUrl_returnsAppUrlString() {
		assertEquals(APP_URL, settingsStore.getUrlToLoad(null));
	}

	@Test
	public void isRootUrl_withAppUrl_returnsTrue() {
		assertTrue(settingsStore.isRootUrl(APP_URL));
	}

	@Test
	public void isRootUrl_withAppUrlEndingInSlash_returnsTrue() {
		assertTrue(settingsStore.isRootUrl(APP_URL + "/"));
	}

	@Test
	public void isRootUrl_withOtherUrl_returnsFalse() {
		assertFalse(settingsStore.isRootUrl("https://project.health-ministry.org"));
	}

	@Test
	public void isRootUrl_withNullUrl_returnsFalse() {
		assertFalse(settingsStore.isRootUrl(null));
	}

	@Test
	public void get_withPrefSet_returnsValue() {
		when(sharedPreferences.getString("a_setting", null)).thenReturn("a_value");

		assertEquals("a_value", settingsStore.get("a_setting"));
	}

	@Test
	public void get_withNoPrefSet_returnsNull() {
		when(sharedPreferences.getString("a_setting", null)).thenReturn(null);

		assertNull(settingsStore.get("a_setting"));
	}

	@Test
	public void getLastUrl_withLastUrlTime_returnsLastUrl() {
		when(sharedPreferences.getLong("last-url-time-ms", 0)).thenReturn(System.currentTimeMillis());
		when(sharedPreferences.getString("last-url", null)).thenReturn("https://project-abc.medic.org/#messages");

		assertEquals("https://project-abc.medic.org/#messages", settingsStore.getLastUrl());
	}

	@Test
	public void getLastUrl_withTooOldLastUrlTime_returnsNull() {
		when(sharedPreferences.getLong("last-url-time-ms", 0)).thenReturn(60L);

		assertNull(settingsStore.getLastUrl());
	}
}
