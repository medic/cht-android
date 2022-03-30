package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class SettingsStoreTest {

	public SettingsStore setup(String appUrl, SharedPreferences sharedPreferences) {
		return new SettingsStore(sharedPreferences) {
			@Override
			public String getAppUrl() {
				return appUrl;
			}

			@Override
			public boolean hasWebappSettings() {
				return false;
			}

			@Override
			public boolean allowsConfiguration() {
				return false;
			}

			@Override
			public void update(SharedPreferences.Editor ed, WebappSettings s) {

			}
		};
	}

	@Test
	public void getAppUrl_withUrlSet_returnsUrlString() {
		//> GIVEN
		SettingsStore settingsStore = setup("https://project-abc.medic.org", null);

		//> WHEN
		String expected = settingsStore.getAppUrl();

		//> THEN
		assertEquals("https://project-abc.medic.org", expected);
	}

	@Test
	public void getAppUrl_withNullUrl_returnsNull() {
		//> GIVEN
		SettingsStore settingsStore = setup(null, null);

		//> WHEN
		String expected = settingsStore.getAppUrl();

		//> THEN
		assertNull(expected);
	}

	@Test
	public void getUrlToLoad_withValidUrl_returnsUrlString() {
		//> GIVEN
		SettingsStore settingsStore = setup("https://project-abc.medic.org", null);
		Uri uri = Uri.parse("https://project-zxy.medic.org");

		//> WHEN
		String expected = settingsStore.getUrlToLoad(uri);

		//> THEN
		assertEquals("https://project-zxy.medic.org", expected);
	}

	@Test
	public void getUrlToLoad_withInvalidUrl_returnsAppUrlString() {
		//> GIVEN
		SettingsStore settingsStore = setup("https://project-abc.medic.org", null);

		//> WHEN
		String expected = settingsStore.getUrlToLoad(null);

		//> THEN
		assertEquals("https://project-abc.medic.org", expected);
	}

	@Test
	public void isRootUrl_withAppUrl_returnsTrue() {
		//> GIVEN
		SettingsStore settingsStore = setup("https://project-abc.medic.org", null);

		//> WHEN
		boolean expected = settingsStore.isRootUrl("https://project-abc.medic.org");

		//> THEN
		assertTrue(expected);
	}

	@Test
	public void isRootUrl_withOtherUrl_returnsFalse() {
		//> GIVEN
		SettingsStore settingsStore = setup("https://project-abc.medic.org", null);

		//> WHEN
		boolean expected = settingsStore.isRootUrl("https://project.health-ministry.org");

		//> THEN
		assertFalse(expected);
	}

	@Test
	public void isRootUrl_withNullUrl_returnsFalse() {
		//> GIVEN
		SettingsStore settingsStore = setup("https://project-abc.medic.org", null);

		//> WHEN
		boolean expected = settingsStore.isRootUrl(null);

		//> THEN
		assertFalse(expected);
	}

	@Test
	public void get_withPrefSet_returnsValue() {
		//> GIVEN
		SharedPreferences sharedPreferences = mock(SharedPreferences.class);
		when(sharedPreferences.getString("a_setting", null)).thenReturn("a_value");
		SettingsStore settingsStore = setup("https://project-abc.medic.org", sharedPreferences);

		//> WHEN
		String expected = settingsStore.get("a_setting");

		//> THEN
		assertEquals("a_value", expected);
	}

	@Test
	public void get_withNoPrefSet_returnsNull() {
		//> GIVEN
		SharedPreferences sharedPreferences = mock(SharedPreferences.class);
		when(sharedPreferences.getString("a_setting", null)).thenReturn(null);
		SettingsStore settingsStore = setup("https://project-abc.medic.org", sharedPreferences);

		//> WHEN
		String expected = settingsStore.get("a_setting");

		//> THEN
		assertNull(expected);
	}

	@Test
	public void getUnlockCode_withPrefSet_returnsValue() {
		//> GIVEN
		SharedPreferences sharedPreferences = mock(SharedPreferences.class);
		when(sharedPreferences.getString("unlock-code", null)).thenReturn("a_value");
		SettingsStore settingsStore = setup("https://project-abc.medic.org", sharedPreferences);

		//> WHEN
		String expected = settingsStore.get("unlock-code");

		//> THEN
		assertEquals("a_value", expected);
	}

	@Test
	public void getUnlockCode_withNoPrefSet_returnsNull() {
		//> GIVEN
		SharedPreferences sharedPreferences = mock(SharedPreferences.class);
		when(sharedPreferences.getString("unlock-code", null)).thenReturn(null);
		SettingsStore settingsStore = setup("https://project-abc.medic.org", sharedPreferences);

		//> WHEN
		String expected = settingsStore.get("unlock-code");

		//> THEN
		assertNull(expected);
	}

	@Test
	public void hasUserDeniedGeolocation_withPrefSet_returnsValue() {
		//> GIVEN
		SharedPreferences sharedPreferences = mock(SharedPreferences.class);
		when(sharedPreferences.getBoolean("denied-geolocation", false)).thenReturn(true);
		SettingsStore settingsStore = setup("https://project-abc.medic.org", sharedPreferences);

		//> WHEN
		boolean expected = settingsStore.hasUserDeniedGeolocation();

		//> THEN
		assertTrue(expected);
	}

	@Test
	public void getUnlockCode_withNoPrefSet_returnsFalse() {
		//> GIVEN
		SharedPreferences sharedPreferences = mock(SharedPreferences.class);
		when(sharedPreferences.getBoolean("denied-geolocation", false)).thenReturn(true);
		SettingsStore settingsStore = setup("https://project-abc.medic.org", sharedPreferences);

		//> WHEN
		boolean expected = settingsStore.hasUserDeniedGeolocation();

		//> THEN
		assertTrue(expected);
	}

	@Test
	public void getLastUrl_withLastUrlTime_returnsLastUrl() {
		//> GIVEN
		SharedPreferences sharedPreferences = mock(SharedPreferences.class);
		when(sharedPreferences.getLong("last-url-time-ms", 0)).thenReturn(System.currentTimeMillis());
		when(sharedPreferences.getString("last-url", null)).thenReturn("https://project-abc.medic.org/#messages");
		SettingsStore settingsStore = setup("https://project-abc.medic.org", sharedPreferences);

		//> WHEN
		String expected = settingsStore.getLastUrl();

		//> THEN
		assertEquals("https://project-abc.medic.org/#messages", expected);
	}

	@Test
	public void getLastUrl_withTooOldLastUrlTime_returnsNull() {
		//> GIVEN
		SharedPreferences sharedPreferences = mock(SharedPreferences.class);
		when(sharedPreferences.getLong("last-url-time-ms", 0)).thenReturn(60L);
		when(sharedPreferences.getString("last-url", null)).thenReturn("https://project-abc.medic.org/#messages");
		SettingsStore settingsStore = setup("https://project-abc.medic.org", sharedPreferences);

		//> WHEN
		String expected = settingsStore.getLastUrl();

		//> THEN
		assertNull(expected);
	}
}
