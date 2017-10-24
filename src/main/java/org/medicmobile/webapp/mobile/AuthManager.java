package org.medicmobile.webapp.mobile;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;

import java.net.HttpCookie;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.xwalk.core.XWalkCookieManager;
import org.xwalk.core.XWalkView;
import org.xwalk.core.XWalkWebResourceResponse;

import static android.content.Context.MODE_PRIVATE;
import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;
import static org.medicmobile.webapp.mobile.Utils.urlDecode;

class AuthManager {
	/** account name -> auth cookies */
	private final Map<String, Map<String, String>> cookieStash;
	private final EmbeddedBrowserActivity parent;
	private final XWalkView container;
	private final XWalkCookieManager monster;

	private String currentAccount;

	AuthManager(EmbeddedBrowserActivity parent, XWalkView container) {
		this.parent = parent;
		this.container = container;

		monster = new XWalkCookieManager();
		cookieStash = loadStashFromPersistentStore();
	}

	Collection<String> getAuthedAccountNames() {
		List<String> names = new LinkedList<>();
		for(String name : cookieStash.keySet()) {
			Map<String, String> c = cookieStash.get(name);
			if(c == null) continue;

			String authSessionCookie = c.get("AuthSession");
			if(authSessionCookie == null) continue;
			if(authSessionCookie.length() == 0) continue;

			names.add(name);
		}
		return names;
	}

	boolean switchTo(String account) {
		trace(this, "switchTo() :: requested change to account: %s", account);

		final Map<String, String> accountCookies = cookieStash.get(account);

		if(accountCookies == null) {
			trace(this, "switchTo() :: account '%s' not found", account);
			return false;
		} else {
			trace(this, "switchTo() :: setting cookies for account '%s' to: %s", account, accountCookies);

			container.post(new Runnable() {
				public void run() {
					try {
						monster.removeAllCookie();

						String url = container.getUrl();
						trace(this, "switchTo() :: url=%s", url);

						Uri uri = Uri.parse(url);
						String path = uri.getPath();
						trace(this, "switchTo() :: uri=%s, path=%s", uri, uri.getPath());

						for(String cookieName : accountCookies.keySet()) {
							String rawValue = accountCookies.get(cookieName);
							String cookieString = String.format("%s=%s", cookieName, rawValue);
							trace(this, "switchTo() :: setting cookie: %s", cookieString);
							monster.setCookie(url, cookieString);
						}

						// TODO we don't need to do the reload here if we're coming from the login page.
						// on the other hand, if we're already in the application we should only need
						// to set the cookies and then reload the page - no need to change the URL.
						if("/medic/login".equals(uri.getPath())) {
							parent.evaluateJavascript("window.location.href = '/medic/_design/medic/_rewrite/#/about';");
						} else {
							parent.evaluateJavascript("window.location.reload();");
						}
					} catch(Exception ex) {
						warn(ex, "switchTo()");
					}
				}
			});
			return true;
		}
	}

	void loginNew() {
		try {
			container.post(new Runnable() {
				public void run() {
					try {
						monster.removeAllCookie();
						parent.evaluateJavascript("window.location.href = '/medic/login'");
					} catch(Exception ex) {
						warn(ex, "loginNew()");
					}
				}
			});
		} catch(Exception ex) {
			warn(ex, "loginNew()");
		}
	}

	void stealCookies(XWalkWebResourceResponse response) {
		Map<String, String> headers = response.getResponseHeaders();
		List<String> cookiesForAccount = null;

		// XWalk maps response cookies via case-sensitive keys.  For
		// adherence to the HTTP spec, we ideally want to treat headers
		// case-insensitively.  Hence this horrible map look-up.
		for(String key : headers.keySet()) {
			if(key.equalsIgnoreCase("set-cookie")) {
				String fullValue = headers.get(key);
				trace(this, "onReceivedResponseHeaders() :: fullValue=%s", fullValue);
				if(fullValue != null) {
					if(cookiesForAccount == null) cookiesForAccount = new LinkedList<>();
					for(String cookie : cutCookies(fullValue)) {
						cookiesForAccount.add(cookie);
					}
				}
			}
		}

		if(cookiesForAccount != null) {
			stash(cookiesForAccount);
			trace(this, "onReceivedResponseHeaders() :: set-cookie=%s (%s cookies)", cookiesForAccount, cookiesForAccount.size());
		}
	}

	private synchronized void stash(Collection<String> cookies) {
		Map<String, String> map = asMap(cookies);

		String accountName = getAccountName(map);
		if(accountName == null) return;

		cookieStash.put(accountName, map);

		storePersistently(accountName, map);
	}

	private String getAccountName(Map<String, String> cookies) {
		try {
			String encodedString = cookies.get("userCtx");
			trace(this, "getAccountName() :: encodedString=%s", encodedString);

			if(encodedString == null) return null;

			String jsonString = urlDecode(encodedString);
			trace(this, "getAccountName() :: jsonString=%s", jsonString);

			if(jsonString == null || jsonString.length() == 0) return null;

			JSONObject json = new JSONObject(jsonString);
			trace(this, "getAccountName() :: json=%s", json);

			String name = json.optString("name");
			trace(this, "getAccountName() :: name=%s", name);

			return name;
		} catch(JSONException ex) {
			warn(ex, "getAccountName()");
			return null;
		}
	}

	private Map<String, String> asMap(Collection<String> cookies) {
		Map<String, String> map = new TreeMap<>();

		for(String cookie : cookies) {
			String[] parts = cookie.split("=", 2);
			String key = parts[0].trim();
			//String value = parts.length > 1 ? urlDecode(parts[1].trim()) : null;
			String value = parts.length > 1 ? parts[1].trim() : null;
			map.put(key, value);
		}

		return map;
	}

//> PERSISTENT STORAGE
	private Map<String, Map<String, String>> loadStashFromPersistentStore() {
		Map<String, ?> persisted = getStore().getAll();
		Map<String, Map<String, String>> cookieStash = new TreeMap<>();

		for(String accountName : persisted.keySet()) {
			Object stored = persisted.get(accountName);
			if(!(stored instanceof Set)) {
				trace(this, "loadStashFromPersistentStore() :: could not read stored cookie value from object of type %s", stored.getClass());
				continue;
			}

			Set<String> storedSet = (Set) stored;
			Map<String, String> cookiesForAccount = new TreeMap<>();

			for(String storedString : storedSet) {
				String[] parts = storedString.split("=", 2);
				if(parts.length != 2) {
					trace(this, "loadStashFromPersistentStore() :: could not read stored cookie value from %s", stored);
					continue;
				}

				cookiesForAccount.put(parts[0], parts[1]);
			}

			cookieStash.put(accountName, cookiesForAccount);
		}

		log("AuthManager.loadStashFromPersistentStore() :: loaded for accounts: %s", cookieStash.keySet());

		return cookieStash;
	}

	private void storePersistently(String accountName, Map<String, String> cookies) {
		Editor ed = getStore().edit();

		Set<String> prefs = new HashSet<>();
		for(Map.Entry e : cookies.entrySet()) {
			prefs.add(String.format("%s=%s", e.getKey(), e.getValue()));
		}

		ed.putStringSet(accountName, prefs);

		if(ed.commit()) {
			trace(this, "storePersistently() :: succeeded for account %s", accountName);
		} else {
			log("AuthManager.storePersistently() :: failed for account %s", accountName);
		}
	}

	private SharedPreferences getStore() {
		return parent.getSharedPreferences("AuthManager", MODE_PRIVATE);
	}

//> STATIC HELPERS
	/**
	 * Decompose the Set-Cookies header as supplied by
	 * {@code XWalkWebResourceResponse}.
	 *
	 * Unhelpfully, {@code XWalkWebResourceResponse.getHeaders()} joins all
	 * {@code Set-Cookie} headers into a single {@code String} value,
	 * separated by commas.  We have to manually decode this, while
	 * preserving settings/flags set on each cookie.  Although commas are
	 * illegal in cookie values, they may be present in the Expires flag,
	 * hence the bizarre splitting logic.
	 *
	 * We assume that all cookies are separated by commas (as per the XWalk
	 * implementation), rather than by semicolon (as per RFC 2109 and RFC
	 * 2965), as this behaviour is discouraged in RFC 6265.
	 */
	private static Collection<String> cutCookies(String setCookieString) {
		String[] parts = setCookieString.split(",");

		List<String> strings = new LinkedList<>();

		for(int i=parts.length-1; i>=0; --i) {
			String headerPart = parts[i];
			System.out.println("Decoding header part: " + headerPart);

			boolean decoded = false;
			do {
				try {
					HttpCookie.parse(headerPart);
					// we assume that there's only one cookie per part.  Don't keep
					// the resulting HttpCookie object so that we preserve flags etc.
					strings.add(headerPart);
					decoded = true;
				} catch(IllegalArgumentException ex) {
					headerPart = parts[--i] + headerPart;
				}
			} while(!decoded && i>=0);

			System.out.println("Decoded to: " + decoded);
		}

		return strings;
	}
}
