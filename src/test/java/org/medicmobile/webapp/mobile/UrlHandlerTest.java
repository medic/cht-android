package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import androidx.browser.customtabs.CustomTabsIntent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedConstruction;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class UrlHandlerTest {
	private static final String APP_URL = "https://project-abc.medic.org";
	private EmbeddedBrowserActivity parentActivity;
	private SettingsStore settingsStore;
	private WebView webView;
	private WebResourceRequest webResourceRequest;
	private Context context;
	private UrlHandler handler;

	@Before
	public void setup() {
		parentActivity = mock(EmbeddedBrowserActivity.class);
		settingsStore = mock(SettingsStore.class);
		when(settingsStore.getAppUrl()).thenReturn(APP_URL);
		webView = mock(WebView.class);
		context = mock(Context.class);
		when(webView.getContext()).thenReturn(context);
		doNothing().when(context).startActivity(any());
		webResourceRequest = mock(WebResourceRequest.class);

		handler = new UrlHandler(parentActivity, settingsStore);
	}

	@Test
	public void shouldOverrideUrlLoading_withAppUrlSubPath() {
		when(webResourceRequest.getUrl()).thenReturn(Uri.parse(APP_URL + "/some-sub-path"));

		boolean result = handler.shouldOverrideUrlLoading(webView, webResourceRequest);

		assertFalse(result);
		verify(settingsStore, times(2)).getAppUrl();
		verify(webResourceRequest).getUrl();
		verify(webView, times(0)).getContext();
		verify(context, times(0)).startActivity(any());
	}

	@Test
	public void shouldOverrideUrlLoading_withExternalUrl() {
		when(webResourceRequest.getUrl()).thenReturn(Uri.parse("some-external-url.com"));

		boolean result = handler.shouldOverrideUrlLoading(webView, webResourceRequest);

		assertTrue(result);
		verify(settingsStore, times(2)).getAppUrl();
		verify(webResourceRequest).getUrl();
		verify(webView).getContext();
		verify(context).startActivity(any());
	}

	@Test
	public void shouldOverrideUrlLoading_withExternalOidcProviderUrl() {
		CustomTabsIntent intent = mock(CustomTabsIntent.class);
		Uri expectedUri = Uri.parse("some-external-url.com?redirect_uri=" + Uri.encode(APP_URL + "/medic/login/oidc"));
		try (MockedConstruction<CustomTabsIntent.Builder> mocked = mockConstruction(
			CustomTabsIntent.Builder.class,
			(mock, context) -> when(mock.build()).thenReturn(intent)
		)) {
			doNothing().when(intent).launchUrl(any(), any());
			when(webResourceRequest.getUrl()).thenReturn(expectedUri);

			boolean result = handler.shouldOverrideUrlLoading(webView, webResourceRequest);

			assertTrue(result);
			verify(settingsStore, times(2)).getAppUrl();
			verify(webResourceRequest).getUrl();
			verify(webView, times(0)).getContext();
			verify(context, times(0)).startActivity(any());
			assertEquals(1, mocked.constructed().size());
			verify(mocked.constructed().get(0), times(1)).build();
			verify(intent, times(1)).launchUrl(parentActivity, expectedUri);
		}
	}

	@Test
	public void shouldOverrideUrlLoading_withNullUrl() {
		when(webResourceRequest.getUrl()).thenReturn(null);

		boolean result = handler.shouldOverrideUrlLoading(webView, webResourceRequest);

		assertTrue(result);
		verify(settingsStore, times(2)).getAppUrl();
		verify(webResourceRequest).getUrl();
		verify(webView).getContext();
		verify(context).startActivity(any());
	}
}
