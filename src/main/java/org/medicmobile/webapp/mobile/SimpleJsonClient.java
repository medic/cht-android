package org.medicmobile.webapp.mobile;

import java.io.*;

import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.params.BasicHttpParams;

import org.json.*;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;

public class SimpleJsonClient {
	public JSONObject get(String url) throws JSONException, IOException {
		if(DEBUG) traceMethod("get", "url", url);
		DefaultHttpClient httpclient = new DefaultHttpClient(new BasicHttpParams());
		HttpGet getter = new HttpGet(url);
		getter.setHeader("Content-type", "application/json");
		InputStream inputStream = null;
		try {
			HttpResponse response = httpclient.execute(getter);
			HttpEntity entity = response.getEntity();

			inputStream = entity.getContent();
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
			StringBuilder bob = new StringBuilder();

			String line = null;
			while((line = reader.readLine()) != null) {
				bob.append(line + "\n");
			}
			String jsonString = bob.toString();
			if(DEBUG) log("get", "Retrieved JSON: " + jsonString);
			return new JSONObject(jsonString);
		} catch (JSONException | IOException ex) {
			throw ex;
		} finally {
			if(inputStream != null) try {
				inputStream.close();
			} catch(Exception ex) {
				if(DEBUG) ex.printStackTrace();
			}
		}
	}

	private static void traceMethod(String methodName, String...args) {
		StringBuilder bob = new StringBuilder();
		for(int i=0; i<args.length; i+=2) {
			bob.append(args[i]);
			bob.append("=");
			bob.append(args[i+1]);
			bob.append(";");
		}
		log(methodName, bob.toString());
	}

	private static void log(String methodName, String message) {
		if(DEBUG) System.err.println("LOG | SimpleJsonClient." +
				methodName + "()" +
				message);
	}
}

