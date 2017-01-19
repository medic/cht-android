package org.medicmobile.webapp.mobile.migrate2crosswalk;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

final class JSON {
	static final JSONObject obj(Object... args) throws JSONException {
		assert(args.length % 2 == 0): "Must supply an even number of args.";

		JSONObject o = new JSONObject();

		for(int i=0; i<args.length; i+=2) {
			String key = (String) args[i];
			Object val = args[i+1];
			o.put(key, val);
		}

		return o;
	}

	static final JSONArray array(Object... contents) throws JSONException {
		JSONArray a = new JSONArray();
		for(Object o : contents) a.put(o);
		return a;
	}
}

class JsonEntity {
	private Object entity;
	private JsonEntity(Object entity) { this.entity = entity; }
	static JsonEntity of(JSONObject obj) { return new JsonEntity(obj); }
	static JsonEntity of(JSONArray arr) { return new JsonEntity(arr); }
	public JSONArray asArray() { return (JSONArray) entity; }
	public JSONObject asObject() { return (JSONObject) entity; }
	public String toString() { return entity.toString(); }
}
