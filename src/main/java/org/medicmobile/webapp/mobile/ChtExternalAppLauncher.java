package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.JavascriptUtils.safeFormat;
import static org.medicmobile.webapp.mobile.MedicLog.error;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.Utils.getUriFromFilePath;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collector;

public class ChtExternalAppLauncher {

	private ChtExternalAppLauncher() { }

	static Intent createIntent(ChtExternalApp chtExternalApp) {
		ChtExternalAppIntentBuilder builder = new ChtExternalAppIntentBuilder();

		return builder
				.setAction(chtExternalApp.getAction())
				.setCategory(chtExternalApp.getCategory())
				.setExtras(chtExternalApp.getExtras())
				.setPackageName(chtExternalApp.getPackageName())
				.setUri(chtExternalApp.getUri())
				.setFlags(chtExternalApp.getFlags())
				.setType(chtExternalApp.getType())
				.build();
	}

	static String processResponse(Intent intent, Activity context) {
		try {
			if (intent == null || intent.getExtras() == null) {
				return makeJavaScript(null);
			}

			JSONObject json = parseBundleToJson(intent.getExtras(), context);
			return makeJavaScript(json.toString());

		} catch (Exception exception) {
			error(exception, "ChtExternalAppLauncher :: Problem serialising the intent response");
			return safeFormat("console.error('Problem serialising the intent response: %s')", exception);
		}
	}

	//> PRIVATE

	private static String makeJavaScript(String response) {
		String javaScript = "try {" +
				"const api = window.CHTCore.AndroidApi;" +
				"if (api && api.v1 && api.v1.resolveCHTExternalAppResponse) {" +
				"  api.v1.resolveCHTExternalAppResponse(%s);" +
				"}" +
				"} catch (error) { " +
				"  console.error('ChtExternalAppLauncher :: Error on sending intent response to CHT-Core webapp', error);" +
				"}";

		return safeFormat(javaScript, response);
	}

	private static JSONObject parseBundleToJson(Bundle bundle, Activity context) {
		try {
			JSONObject json = new JSONObject();
			bundle
					.keySet()
					.iterator()
					.forEachRemaining(key -> setValueInJson(key, json, bundle, context));
			return json;

		} catch (Exception exception) {
			error(exception, "ChtExternalAppLauncher :: Problem parsing bundle to json. Bundle=%s", bundle);
		}

		return null;
	}

	private static void setValueInJson(String key, JSONObject json, Bundle bundle, Activity context) {
		try {
			Object value = bundle.get(key);

			if (value instanceof Bitmap) {
				json.put(key, parseBitmapImageToBase64((Bitmap) value, context));
				return;
			}

			if (value instanceof Bundle) {
				json.put(key, parseBundleToJson((Bundle) value, context));
				return;
			}

			if (isBundleList(value)) {
				JSONArray jsonArray = ((List<Bundle>) value)
						.stream()
						.map(item -> parseBundleToJson(item, context))
						.collect(Collector.of(JSONArray::new, JSONArray::put, JSONArray::put));

				json.put(key, jsonArray);
				return;
			}

			Optional<Uri> imagePath = getImageUri(value);
			if (imagePath.isPresent()) {
				json.put(key, getImageFromStoragePath(imagePath.get(), context));
				return;
			}

			json.put(key, JSONObject.wrap(value));

		} catch (Exception exception) {
			error(exception, "ChtExternalAppLauncher :: Problem parsing bundle to json. Key=%s, Bundle=%s", key, bundle);
		}
	}

	private static boolean isBundleList(Object value) {
		if (!(value instanceof List)) {
			return false;
		}

		List<?> list = (List<?>) value; // Avoid casting many times to same type.

		return !list.isEmpty() && list.get(0) instanceof Bundle;
	}

	private static Optional<Uri> getImageUri(Object value) {
		if (!(value instanceof String)) {
			return Optional.empty();
		}

		String path = (String) value; // Avoid casting many times to same type.

		if (!path.endsWith(".jpg") && !path.endsWith(".png")) {
			return Optional.empty();
		}

		return getUriFromFilePath(path);
	}

	private static String getImageFromStoragePath(Uri filePath, Activity context) {
		trace(context, "ChtExternalAppLauncher :: Retrieving image from storage path.");

		try (
			ParcelFileDescriptor parcelFileDescriptor = context
				.getContentResolver()
				.openFileDescriptor(filePath, "r");
			InputStream file = new FileInputStream(parcelFileDescriptor.getFileDescriptor());
		){
			Bitmap imgBitmap = BitmapFactory.decodeStream(file);
			return parseBitmapImageToBase64(imgBitmap, context);

		} catch (Exception exception) {
			error(exception, "ChtExternalAppLauncher :: Failed to process image file from path: %s", filePath);
		}

		return null;
	}

	private static String parseBitmapImageToBase64(Bitmap imgBitmap, Activity context) {
		trace(context, "ChtExternalAppLauncher :: Compressing image file.");
		ByteArrayOutputStream outputFile = new ByteArrayOutputStream();
		imgBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputFile);

		trace(context, "ChtExternalAppLauncher :: Encoding image file to Base64.");
		byte[] imageBytes = outputFile.toByteArray();

		return Base64.encodeToString(imageBytes, Base64.NO_WRAP);
	}

	private static class ChtExternalAppIntentBuilder {
		private final Intent intent = new Intent();

		//> PUBLIC

		public Intent build() {
			return this.intent;
		}

		public ChtExternalAppIntentBuilder setAction(String action) {
			if (action != null) {
				this.intent.setAction(action);
			}

			return this;
		}

		public ChtExternalAppIntentBuilder setCategory(String category) {
			if (category != null) {
				this.intent.addCategory(category);
			}

			return this;
		}

		public ChtExternalAppIntentBuilder setExtras(JSONObject extras) {
			if (extras != null) {
				extras
						.keys()
						.forEachRemaining(key -> setIntentExtras(key, extras));
			}

			return this;
		}

		public ChtExternalAppIntentBuilder setUri(Uri uri) {
			if (uri != null) {
				this.intent.setDataAndNormalize(uri);
			}

			return this;
		}

		public ChtExternalAppIntentBuilder setPackageName(String packageName) {
			if (packageName != null) {
				this.intent.setPackage(packageName);
			}

			return this;
		}

		public ChtExternalAppIntentBuilder setType(String type) {
			if (type != null) {
				this.intent.setType(type);
			}

			return this;
		}

		public ChtExternalAppIntentBuilder setFlags(Integer flags) {
			if (flags != null) {
				this.intent.setFlags(flags);
			}

			return this;
		}

		//> PRIVATE

		private void setIntentExtras(String key, JSONObject data) {
			try {
				Object value = data.get(key);

				if (value instanceof JSONObject) {
					this.intent.putExtra(key, parseJsonToBundle((JSONObject) value));
					return;
				}

				if (value instanceof JSONArray) {
					this.intent.putExtra(key, parseJsonArrayToList((JSONArray) value));
					return;
				}

				this.intent.putExtra(key, (Serializable) value);

			} catch (Exception exception) {
				error(exception, "ChtExternalAppLauncher :: Problem setting intent extras. Key=%s, Data=%s", key, data);
			}
		}

		private Bundle parseJsonToBundle(JSONObject json) {
			Bundle bundle = new Bundle();
			json
					.keys()
					.forEachRemaining(key -> setBundleAttribute(key, json, bundle));

			return bundle;
		}

		private Serializable parseJsonArrayToList(JSONArray jsonArray) throws JSONException {
			if (jsonArray.length() > 0) {
				return jsonArray.get(0) instanceof JSONObject
						? parseJsonArrayToBundleList(jsonArray) : parseJsonArrayToSerializableList(jsonArray);
			}

			return new ArrayList<>();
		}

		private ArrayList<Bundle> parseJsonArrayToBundleList(JSONArray jsonArray) throws JSONException {
			ArrayList<Bundle> list = new ArrayList<>(jsonArray.length());

			for (int i = 0; i < jsonArray.length(); i++) {
				list.add(parseJsonToBundle(jsonArray.getJSONObject(i)));
			}

			return list;
		}

		private ArrayList<Serializable> parseJsonArrayToSerializableList(JSONArray jsonArray) throws JSONException {
			ArrayList<Serializable> list = new ArrayList<>(jsonArray.length());

			for (int i = 0; i < jsonArray.length(); i++) {
				list.add((Serializable) jsonArray.get(i));
			}

			return list;
		}

		private void setBundleAttribute(String key, JSONObject json, Bundle bundle) {
			try {
				Object value = json.get(key);

				if (value instanceof JSONObject) {
					bundle.putBundle(key, parseJsonToBundle((JSONObject) value));
					return;
				}

				if (value instanceof JSONArray) {
					bundle.putSerializable(key, parseJsonArrayToList((JSONArray) value));
					return;
				}

				bundle.putSerializable(key, (Serializable) value);

			} catch (Exception exception) {
				error(exception, "ChtExternalAppIntentBuilder :: Problem converting from JSON to Bundle. Key=%s, JSON=%s", key, json);
			}
		}
	}
}
