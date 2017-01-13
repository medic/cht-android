package org.medicmobile.webapp.mobile.migrate2crosswalk;

public class WebViewDataExtractionJavascriptBinding {
	private final StandardWebViewDataExtractionActivity webView;

	WebViewDataExtractionJavascriptBinding(StandardWebViewDataExtractionActivity webView) {
		this.webView = webView;
	}

	@android.webkit.JavascriptInterface
	public void disableServerComms() {
		webView.disableServerComms();
	}

	@android.webkit.JavascriptInterface
	public void enableServerComms() {
		webView.enableServerComms();
	}
}
