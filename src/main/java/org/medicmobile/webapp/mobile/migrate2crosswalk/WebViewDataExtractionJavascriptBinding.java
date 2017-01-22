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

	@android.webkit.JavascriptInterface
	public void replicationComplete() {
		webView.replicationComplete();
	}

	@android.webkit.JavascriptInterface
	public void replicateToLocal() {
		webView.evaluateJavascript(
				"var localDb = angular.element('body').injector().get('DB')();" +
				"PouchDB.replicate(localDb, 'http://localhost:8000/medic')" +
				"  .then(function() {" +
				"    console.log('REPLICATION COMPLETED OK!');" +
				"    medicmobile_webview_data_extraction.replicationComplete();" +
				"  })" +
				"  .catch(function(err) {" +
				"    console.log('ERROR REPLICATING!', err);" +
				"  });");
	}
}
