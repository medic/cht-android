package org.medicmobile.webapp.mobile;

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
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class ChtExternalApp {

	private final String action;
	private final String category;
	private final String type;
	private final JSONObject extras;
	private final Uri uri;
	private final String packageName;
	private final Integer flags;

	private ChtExternalApp(Builder builder) {
		this.action = builder.action;
		this.category = builder.category;
		this.type = builder.type;
		this.extras = builder.extras;
		this.uri = builder.uri;
		this.packageName = builder.packageName;
		this.flags = builder.flags;
	}

	public Intent createIntent() {
		Intent intent = new Intent();

		if (this.action != null) {
			intent.setAction(this.action);
		}

		if (this.category != null) {
			intent.addCategory(this.category);
		}

		if (this.extras != null) {
			extras
					.keys()
					.forEachRemaining(key -> setIntentExtras(intent, key, extras));
		}

		if (this.packageName != null) {
			intent.setPackage(this.packageName);
		}

		if (this.uri != null) {
			intent.setDataAndNormalize(this.uri);
		}

		if (this.flags != null) {
			intent.setFlags(this.flags);
		}

		if (this.type != null) {
			intent.setType(this.type);
		}

		return intent;
	}

	//> PRIVATE

	private Serializable getSerializableValue(Object value) {
		// ODK does not have boolean data type
		if (value instanceof String strValue && ("true".equals(strValue) || "false".equals(strValue))) {
			return Boolean.parseBoolean(strValue);
		}
		return (Serializable) value;
	}

	private void setIntentExtras(Intent intent, String key, JSONObject data) {
		try {
			Object value = data.get(key);

			if (value instanceof JSONObject) {
				intent.putExtra(key, parseJsonToBundle((JSONObject) value));
				return;
			}

			if (value instanceof JSONArray) {
				intent.putExtra(key, parseJsonArrayToList((JSONArray) value));
				return;
			}

			intent.putExtra(key, getSerializableValue(value));

		} catch (Exception exception) {
			error(exception, "ChtExternalApp :: Problem setting intent extras. Key=%s, Data=%s", key, data);
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
			error(exception, "ChtExternalApp :: Problem converting from JSON to Bundle. Key=%s, JSON=%s", key, json);
		}
	}

	//> INTERNAL CLASSES

	public static class Builder {

		private String action;
		private String category;
		private String type;
		private JSONObject extras;
		private Uri uri;
		private String packageName;
		private Integer flags;

		public Builder() { }

		public Builder setAction(String action) {
			this.action = action;
			return this;
		}

		public Builder setCategory(String category) {
			this.category = category;
			return this;
		}

		public Builder setType(String type) {
			this.type = type;
			return this;
		}

		public Builder setExtras(JSONObject extras) {
			this.extras = extras;
			return this;
		}

		public Builder setUri(Uri uri) {
			this.uri = uri;
			return this;
		}

		public Builder setPackageName(String packageName) {
			this.packageName = packageName;
			return this;
		}

		public Builder setFlags(Integer flags) {
			this.flags = flags;
			return this;
		}

		public ChtExternalApp build() {
			return new ChtExternalApp(this);
		}
	}

	public static class Response {

		private final Intent intent;
		private final Activity context;

		public Response(Intent intent, Activity context) {
			this.intent = intent;
			this.context = context;
		}

		public Optional<JSONObject> getData() {
			if (intent == null || intent.getExtras() == null) {
				return Optional.empty();
			}

			JSONObject json = parseBundleToJson(intent.getExtras());
			if (json == null) {
				return Optional.empty();
			}

			return Optional.of(json);
		}

		//> PRIVATE

		private JSONObject parseBundleToJson(Bundle bundle) {
			try {
				JSONObject json = new JSONObject();
				bundle
						.keySet()
						.iterator()
						.forEachRemaining(key -> setValueInJson(key, json, bundle));
				return json;

			} catch (Exception exception) {
				error(exception, "ChtExternalApp :: Problem parsing bundle to json. Bundle=%s", bundle);
			}

			return null;
		}

		private void setValueInJson(String key, JSONObject json, Bundle bundle) {
			try {
				Object value = bundle.get(key);

				if (value instanceof Bitmap) {
					json.put(key, parseBitmapImageToBase64((Bitmap) value, false));
					return;
				}

				if (value instanceof Bundle) {
					json.put(key, parseBundleToJson((Bundle) value));
					return;
				}

				if (isBundleList(value)) {
					JSONArray jsonArray = ((List<Bundle>) value)
							.stream()
							.map(this::parseBundleToJson)
							.collect(Collector.of(JSONArray::new, JSONArray::put, JSONArray::put));

					json.put(key, jsonArray);
					return;
				}

				Optional<List<?>> primitiveListOpt = asPrimitiveList(value);
				if (primitiveListOpt.isPresent()) {
					// ODK/Enketo models a primitive multi-value list (e.g. a select_multiple question) as a
					// space-delimited string.
					String nodeList = primitiveListOpt
						.stream()
						.flatMap(List::stream)
						.map(Object::toString)
						.map(s -> s.replace(" ", "_"))
						.collect(Collectors.joining(" "));
					json.put(key, nodeList);
					return;
				}

				Optional<Uri> imagePath = getImageUri(value);
				if (imagePath.isPresent()) {
					boolean keepFullResolution = bundle.getBoolean("sampleImage", false);
					json.put(key, getImageFromStoragePath(imagePath.get(), keepFullResolution));
					return;
				}

				json.put(key, JSONObject.wrap(value));

			} catch (Exception exception) {
				error(exception, "ChtExternalApp :: Problem parsing bundle to json. Key=%s, Bundle=%s", key, bundle);
			}
		}

		private boolean isBundleList(Object value) {
			if (!(value instanceof List)) {
				return false;
			}

			List<?> list = (List<?>) value; // Avoid casting many times to same type.

			return !list.isEmpty() && list.get(0) instanceof Bundle;
		}

		private boolean isPrimitive(Object value) {
			return value instanceof String ||
				value instanceof Integer ||
				value instanceof Long ||
				value instanceof Double ||
				value instanceof Float ||
				value instanceof Boolean ||
				value instanceof Short ||
				value instanceof Character;
		}

		private Optional<List<?>> asPrimitiveList(Object value) {
			if (value != null && value.getClass().isArray()) {
				// Java utility methods are not great when mixing primitive and object arrays.
				// So, we manually convert any array to a List<Object>.
				int len = java.lang.reflect.Array.getLength(value);
				List<Object> list = new ArrayList<>(len);
				for (int i = 0; i < len; i++) {
					list.add(java.lang.reflect.Array.get(value, i));
				}
				value = list;
			}
			if (
				!(value instanceof List)
				|| ((List<?>) value).isEmpty()
				|| !isPrimitive(((List<?>) value).get(0))
			) {
				return Optional.empty();
			}

			return Optional.of((List<?>) value);
		}

		private Optional<Uri> getImageUri(Object value) {
			if (!(value instanceof String)) {
				return Optional.empty();
			}

			String path = (String) value; // Avoid casting many times to same type.

			if (!path.endsWith(".jpg") && !path.endsWith(".png")) {
				return Optional.empty();
			}

			return getUriFromFilePath(path);
		}

		private String getImageFromStoragePath(Uri filePath, boolean keepFullResolution) {
			trace(this, "ChtExternalApp :: Retrieving image from storage path.");

			try (
					ParcelFileDescriptor parcelFileDescriptor = this.context
							.getContentResolver()
							.openFileDescriptor(filePath, "r");
					InputStream file = new FileInputStream(parcelFileDescriptor.getFileDescriptor());
			){
				Bitmap imgBitmap = BitmapFactory.decodeStream(file);
				return parseBitmapImageToBase64(imgBitmap, keepFullResolution);

			} catch (Exception exception) {
				error(exception, "ChtExternalApp :: Failed to process image file from path: %s", filePath);
			}

			return null;
		}

		private String parseBitmapImageToBase64(Bitmap imgBitmap, boolean keepFullResolution) throws IOException {
			try (ByteArrayOutputStream outputFile = new ByteArrayOutputStream()) {
				trace(this, "ChtExternalApp :: Compressing image file.");
				int quality = keepFullResolution ? 100 : 75;
				imgBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputFile);

				trace(this, "ChtExternalApp :: Encoding image file to Base64.");
				byte[] imageBytes = outputFile.toByteArray();

				return Base64.encodeToString(imageBytes, Base64.NO_WRAP);
			}
		}
	}
}
