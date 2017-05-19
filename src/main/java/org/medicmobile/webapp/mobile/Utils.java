package org.medicmobile.webapp.mobile;

import org.json.JSONException;
import org.json.JSONObject;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;

final class Utils {
	private Utils() {}

	public static JSONObject json(Object... keyVals) throws JSONException {
		if(DEBUG && keyVals.length % 2 != 0) throw new AssertionError();
		JSONObject o = new JSONObject();
		for(int i=keyVals.length-1; i>0; i-=2) {
			o.put(keyVals[i-1].toString(), keyVals[i]);
		}
		return o;
	}
}
