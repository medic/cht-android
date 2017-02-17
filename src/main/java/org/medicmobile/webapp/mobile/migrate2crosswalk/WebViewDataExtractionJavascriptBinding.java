package org.medicmobile.webapp.mobile.migrate2crosswalk;

public class WebViewDataExtractionJavascriptBinding {
	private final StandardWebViewDataExtractionActivity webView;

	WebViewDataExtractionJavascriptBinding(StandardWebViewDataExtractionActivity webView) {
		this.webView = webView;
	}

	@android.webkit.JavascriptInterface
	public void replicationComplete() {
		webView.replicationComplete();
	}

	@android.webkit.JavascriptInterface
	public void replicateToLocal() {
		webView.evaluateJavascript(
				"var localCouch = angular.element('body').injector().get('DB')();" +
				"PouchDB.replicate(localCouch, 'http://localhost:8000/medic')" +
				"  .then(function() {" +
				"    console.log('REPLICATION COMPLETED OK!');" +
				"    medicmobile_webview_data_extraction.replicationComplete();" +
				"  })" +
				"  .catch(function(err) {" +
				"    console.log('ERROR REPLICATING!', err);" +
				"  });");
	}
}
