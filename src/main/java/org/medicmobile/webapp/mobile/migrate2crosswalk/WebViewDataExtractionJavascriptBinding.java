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
				"$('.bootstrap-layer').html('<div class=\"loader\"></div><p>Upgrading...</p><p><span class=\"doc-count\">0</span> docs processed (<span class=\"percent\">0</span>%)</p>').show();" +
				"var localCouch = angular.element('body').injector().get('DB')();" +
				"localCouch.info().then(function(dbInfo) {" +
				"  console.log('DB info:', dbInfo);" +
				"  var totalDocs = Math.max(dbInfo.doc_count, dbInfo.update_seq);" +
				"  var replication = PouchDB.replicate(localCouch, 'http://localhost:8000/medic');" +
				"  replication" +
				"    .on('change', function(changeInfo) {" +
				"      console.log('progress!', changeInfo);" +
				"      var docsRead = changeInfo.docs_read;" +
				"      $('.bootstrap-layer .doc-count').text(docsRead);" +
				"      $('.bootstrap-layer .percent').text(Math.round(docsRead * 100 / totalDocs));" +
				"    });" +
				"  replication" +
				"    .then(function() {" +
				"      console.log('REPLICATION COMPLETED OK!');" +
				"      medicmobile_webview_data_extraction.replicationComplete();" +
				"    })" +
				"    .catch(function(err) {" +
				"      console.log('ERROR REPLICATING!', err);" +
				"      $('.bootstrap-layer').html('<p>Error upgrading.</p><a class=\"btn btn-primary\" href=\"#\" onclick=\"$(\\'.bootstrap-layer\\').hide()\">Close</a>');" +
				"    });" +
				"});");
	}
}
