package org.medicmobile.webapp.mobile;

import android.content.*;

import java.util.*;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;

public class PreconfigProvider {
	public final List<PreconfigOption> options;

	public PreconfigProvider(Context ctx) {
		String[] strings = ctx.getResources()
				.getStringArray(R.array.preconfig_strings);
		options = Func.map(strings, new Func<String, PreconfigOption>() {
			public PreconfigOption apply(String s) {
				String[] parts = s.split("\\|", 2);
				return new PreconfigOption(parts[1], parts[0]);
			}
		});
	}

	public PreconfigOption with(String url) {
		return Func.find(options, withUrl(url));
	}

	public boolean existsFor(String url) {
		return Func.any(options, withUrl(url));
	}

	public int indexOf(String url) {
		return Func.indexOf(options, withUrl(url));
	}

	private Func<PreconfigOption, Boolean> withUrl(final String url) {
		return new Func<PreconfigOption, Boolean>() {
			public Boolean apply(PreconfigOption o) {
				if(DEBUG) log("Checking if %s==%s", o.url, url);
				return o.url.equals(url);
			}
		};
	}

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | PreconfigProvider :: " +
				String.format(message, extras));
	}
}

class PreconfigOption {
	public final String description;
	public final String url;

	public PreconfigOption(String description, String url) {
		this.description = description;
		this.url = url;
	}

	public String toString() {
		return this.description;
	}
}
