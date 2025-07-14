package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class UrlHandlerTest {
	private static final String APP_URL = "https://project-abc.medic.org";
	private SettingsStore settingsStore;
	private WebView webView;
	private WebResourceRequest webResourceRequest;
	private Context context;
	private UrlHandler handler;

	@Before
	public void setup() {
		settingsStore = mock(SettingsStore.class);
		when(settingsStore.getAppUrl()).thenReturn(APP_URL);
		webView = mock(WebView.class);
		context = mock(Context.class);
		when(webView.getContext()).thenReturn(context);
		doNothing().when(context).startActivity(any());
		webResourceRequest = mock(WebResourceRequest.class);

		handler = new UrlHandler(null, settingsStore);
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
		when(webResourceRequest.getUrl()).thenReturn(
			Uri.parse("some-external-url.com?redirect_uri=" + Uri.encode(APP_URL + "/medic/login/oidc"))
		);

		boolean result = handler.shouldOverrideUrlLoading(webView, webResourceRequest);

		assertFalse(result);
		verify(settingsStore, times(2)).getAppUrl();
		verify(webResourceRequest).getUrl();
		verify(webView, times(0)).getContext();
		verify(context, times(0)).startActivity(any());
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
