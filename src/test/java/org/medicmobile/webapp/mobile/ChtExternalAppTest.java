package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class ChtExternalAppTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void createIntent_withAllAttributesReceived_returnsIntentCorrectly() throws JSONException {
		//> GIVEN
		Set<String> expectedCategoryUri = new HashSet<>();
		expectedCategoryUri.add("a.category");

		Set<String> expectedCategoryType = new HashSet<>();
		expectedCategoryType.add("a.different.category");

		Uri uri = Uri.parse("example://some:action");
		ChtExternalApp chtExternalAppUri = new ChtExternalApp
				.Builder()
				.setAction("an.action")
				.setCategory("a.category")
				.setType(null) // When sending Uri, automatically type is cleared.
				.setExtras(new JSONObject("{ \"name\": \"Eric\", \"id\": 1234 }"))
				.setUri(uri)
				.setPackageName("org.example")
				.setFlags(5)
				.build();
		ChtExternalApp chtExternalAppType = new ChtExternalApp
				.Builder()
				.setAction("an.different.action")
				.setCategory("a.different.category")
				.setType("a.type")
				.setExtras(new JSONObject("{ \"name\": \"Anna\", \"details\": { \"phone\": \"999 999 9999\" } }"))
				.setUri(null) // When sending type, automatically data (Uri) is cleared.
				.setPackageName("org.another.example")
				.setFlags(0)
				.build();

		//> WHEN
		Intent intentUri = chtExternalAppUri.createIntent();
		Intent intentType = chtExternalAppType.createIntent();

		//> THEN
		assertEquals("an.action", intentUri.getAction());
		assertEquals(expectedCategoryUri, intentUri.getCategories());
		assertEquals("Eric", intentUri.getStringExtra("name"));
		assertEquals(1234, intentUri.getIntExtra("id", 0));
		assertEquals(uri, intentUri.getData());
		assertEquals("org.example", intentUri.getPackage());
		assertEquals(5, intentUri.getFlags());
		assertNull(intentUri.getType());

		assertEquals("an.different.action", intentType.getAction());
		assertEquals(expectedCategoryType, intentType.getCategories());
		assertEquals("Anna", intentType.getStringExtra("name"));
		assertEquals("999 999 9999", intentType.getBundleExtra("details").get("phone"));
		assertNull(intentType.getData());
		assertEquals("org.another.example", intentType.getPackage());
		assertEquals(0, intentType.getFlags());
		assertEquals("a.type", intentType.getType());
	}

	@Test
	public void createIntent_withNullAttributes_returnsDefaultIntentCorrectly() {
		//> GIVEN
		ChtExternalApp chtExternalApp = new ChtExternalApp
				.Builder()
				.build();

		//> WHEN
		Intent intentUri = chtExternalApp.createIntent();

		//> THEN
		assertNull(intentUri.getAction());
		assertNull(intentUri.getCategories());
		assertNull(intentUri.getExtras());
		assertNull(intentUri.getData());
		assertNull(intentUri.getPackage());
		assertEquals(0, intentUri.getFlags());
		assertNull(intentUri.getType());
	}

	@Test
	public void createIntent_withSimpleData_setExtrasCorrectly() throws JSONException {
		//> GIVEN
		String singlesJson = "{" +
				"\"an.int\": 5," +
				"\"a.long\": 2147483649," +
				"\"a.double\": 2.8," +
				"\"a.boolean\": \"true\"," +
				"\"a.string\": \"some text\"" +
				"}";
		String arraysJson = "{" +
				"\"an.int.array\": [5, 9]," +
				"\"a.long.array\": [2147483649, 2147483700]," +
				"\"a.double.array\": [2.8, 5.5]," +
				"\"a.boolean.array\": [true, false, true]," +
				"\"a.string.array\": [\"some text\", \"another text\"]" +
				"}";
		JSONObject singlesExtras = new JSONObject(singlesJson);
		JSONObject arraysExtras = new JSONObject(arraysJson);

		ChtExternalApp chtExternalAppSingles = new ChtExternalApp
				.Builder()
				.setExtras(singlesExtras)
				.build();
		ChtExternalApp chtExternalAppArrays = new ChtExternalApp
				.Builder()
				.setExtras(arraysExtras)
				.build();

		//> WHEN
		Intent singlesIntent = chtExternalAppSingles.createIntent();
		Intent arraysIntent = chtExternalAppArrays.createIntent();

		//> THEN
		Bundle singleResult = singlesIntent.getExtras();
		assertEquals(5, singleResult.get("an.int"));
		assertEquals(2147483649L, singleResult.get("a.long"));
		assertEquals(2.8, singleResult.get("a.double"));
		assertTrue(singleResult.getBoolean("a.boolean"));
		assertEquals("some text", singleResult.get("a.string"));

		Bundle arraysResult = arraysIntent.getExtras();
		assertEquals(Arrays.asList(5, 9), arraysResult.get("an.int.array"));
		assertEquals(Arrays.asList(2147483649L, 2147483700L), arraysResult.get("a.long.array"));
		assertEquals(Arrays.asList(2.8, 5.5), arraysResult.get("a.double.array"));
		assertEquals(Arrays.asList(true, false, true), arraysResult.get("a.boolean.array"));
		assertEquals(Arrays.asList("some text", "another text"), arraysResult.get("a.string.array"));
	}

	@Test
	public void createIntent_withNestedObjects_setExtrasCorrectly() throws JSONException {
		//> GIVEN
		String json = "{" +
				"\"stats\": {" +
					"\"an.int\": 5," +
					"\"a.long\": 2147483649," +
					"\"a.double\": 2.8," +
					"\"a.boolean\": true," +
					"\"a.string\": \"some text\"," +
					"\"a.null\": null," +
					"\"more.details\": {" +
						"\"a.double.array\": [2.8, 5.5]," +
						"\"a.boolean.array\": [true, false, true]," +
						"\"a.null\": null," +
						"\"a.description\": \"some awesome data\"" +
					"}" +
				"}," +
				"\"people\": [" +
					"{" +
						"\"name\": \"Anna\"," +
						"\"relatives\": [\"Pepe\", \"John\", \"Matt\"]" +
					"}," +
					"{" +
						"\"name\": \"Florentino\"," +
						"\"relatives\": []" +
					"}" +
				"]" +
				"}";
		JSONObject extras = new JSONObject(json);

		ChtExternalApp chtExternalApp = new ChtExternalApp
				.Builder()
				.setExtras(extras)
				.build();

		//> WHEN
		Intent intent = chtExternalApp.createIntent();

		//> THEN
		Bundle result = intent.getExtras();
		Bundle stats = result.getBundle("stats");
		assertEquals(5, stats.get("an.int"));
		assertEquals(2147483649L, stats.get("a.long"));
		assertEquals(2.8, stats.get("a.double"));
		assertTrue(stats.getBoolean("a.boolean"));
		assertEquals("some text", stats.get("a.string"));
		assertNull(stats.get("a.null"));

		Bundle statsDetails = stats.getBundle("more.details");
		assertEquals(Arrays.asList(2.8, 5.5), statsDetails.get("a.double.array"));
		assertEquals(Arrays.asList(true, false, true), statsDetails.get("a.boolean.array"));
		assertNull(statsDetails.get("a.null"));
		assertEquals("some awesome data", statsDetails.get("a.description"));

		ArrayList<Bundle> people = result.getParcelableArrayList("people");
		assertEquals("Anna", people.get(0).get("name"));
		assertEquals(Arrays.asList("Pepe", "John", "Matt"), people.get(0).get("relatives"));
		assertEquals("Florentino", people.get(1).get("name"));
		assertEquals(new ArrayList<>(), people.get(1).get("relatives"));
	}

	@Test
	public void createIntent_withEmptyData_setExtrasCorrectly() {
		//> GIVEN
		JSONObject extras = new JSONObject();
		ChtExternalApp chtExternalApp = new ChtExternalApp
				.Builder()
				.setExtras(extras)
				.build();

		//> WHEN
		Intent intent = chtExternalApp.createIntent();

		//> THEN
		Bundle result = intent.getExtras();
		assertNull(result);
	}

	@Test
	public void processResponse_withSimpleData_buildJsonCorrectly() {
		//> GIVEN
		Activity context = mock(Activity.class);
		Intent intent = new Intent();
		intent.putExtra("an.int", 5);
		intent.putExtra("a.long", 2147483649L);
		intent.putExtra("a.double", 2.8);
		intent.putExtra("a.boolean", true);
		intent.putExtra("a.string", "some text");

		intent.putExtra("an.int.array", new int[]{5, 9});
		intent.putExtra("a.long.array", new long[]{2147483649L, 2147483700L});
		intent.putExtra("a.double.array", new double[]{2.8, 5.5});
		intent.putExtra("a.boolean.array", new boolean[]{true, false, true});
		intent.putExtra("a.string.array", new String[]{"some text", "another text"});

		String expectedJsonData = "{" +
				"\"an.int.array\":\"5 9\"," +
				"\"a.double\":2.8," +
				"\"a.long\":2147483649," +
				"\"a.string\":\"some text\"," +
				"\"an.int\":5," +
				"\"a.boolean.array\":\"true false true\"," +
				"\"a.boolean\":true," +
				"\"a.string.array\":\"some_text another_text\"," +
				"\"a.long.array\":\"2147483649 2147483700\"," +
				"\"a.double.array\":\"2.8 5.5\"" +
				"}";

		//> WHEN
		Optional<JSONObject> json = new ChtExternalApp
				.Response(intent, context)
				.getData();

		//> THEN
		assertTrue(json.isPresent());
		assertEquals(expectedJsonData, json.get().toString());
	}

	@Test
	public void processResponse_withNestedObjects_buildJsonCorrectly() {
		//> GIVEN
		Activity context = mock(Activity.class);
		Intent intent = new Intent();

		Bundle stats = new Bundle();
		stats.putInt("an.int", 5);
		stats.putLong("a.long", 2147483649L);
		stats.putDouble("a.double", 2.8);
		stats.putBoolean("a.boolean", true);
		stats.putString("a.string", "some text");
		stats.putString("a.null", null);

		Bundle moreDetails = new Bundle();
		moreDetails.putDoubleArray("a.double.array", new double[]{2.8, 5.5});
		moreDetails.putBooleanArray("a.boolean.array", new boolean[]{true, false, true});
		moreDetails.putString("a.null", null);
		moreDetails.putString("a.description", "some awesome data");

		Bundle anna = new Bundle();
		anna.putString("name", "Anna");
		anna.putStringArray("relatives", new String[]{"Pepe", "John", "Matt"});

		Bundle florentino = new Bundle();
		florentino.putString("name", "Florentino");
		florentino.putStringArray("relatives", new String[]{});

		List<Bundle> people = new ArrayList<>();
		people.add(anna);
		people.add(florentino);

		stats.putBundle("more.details", moreDetails);
		intent.putExtra("stats", stats);
		intent.putExtra("people", (Serializable) people);

		String expectedJsonData = "{" +
				"\"people\":[" +
					"{" +
						"\"relatives\":\"Pepe John Matt\"," +
						"\"name\":\"Anna\"" +
					"}," +
					"{" +
						"\"relatives\":[]," +
						"\"name\":\"Florentino\"" +
					"}" +
				"]," +
				"\"stats\":{" +
					"\"a.double\":2.8," +
					"\"a.long\":2147483649," +
					"\"a.null\":null," +
					"\"a.string\":\"some text\"," +
					"\"an.int\":5," +
					"\"a.boolean\":true," +
					"\"more.details\":{" +
						"\"a.null\":null," +
						"\"a.boolean.array\":\"true false true\"," +
						"\"a.description\":\"some awesome data\"," +
						"\"a.double.array\":\"2.8 5.5\"" +
					"}" +
				"}" +
				"}";

		//> WHEN
		Optional<JSONObject> json = new ChtExternalApp
				.Response(intent, context)
				.getData();

		//> THEN
		assertTrue(json.isPresent());
		assertEquals(expectedJsonData, json.get().toString());
	}

	@Test
	public void processResponse_withNoData_returnsEmpty() {
		//> GIVEN
		Activity context = mock(Activity.class);
		Intent intent = new Intent();

		//> WHEN
		Optional<JSONObject> json = new ChtExternalApp
				.Response(intent, context)
				.getData();

		//> THEN
		assertFalse(json.isPresent());
	}

	@Test
	public void processResponse_withEmptyData_buildJsonCorrectly() {
		//> GIVEN
		Activity context = mock(Activity.class);
		Intent intentEmptyObj = new Intent();
		intentEmptyObj.putExtra("stats", new Bundle());

		Intent intentEmptyArray = new Intent();
		intentEmptyArray.putExtra("stats", new ArrayList<>());

		String expectedEmptyObj = "{\"stats\":{}}";
		String expectedEmptyArray = "{\"stats\":[]}";

		//> WHEN
		Optional<JSONObject> jsonEmptyObj = new ChtExternalApp
				.Response(intentEmptyObj, context)
				.getData();
		Optional<JSONObject> jsonEmptyArray = new ChtExternalApp
				.Response(intentEmptyArray, context)
				.getData();

		//> THEN
		assertTrue(jsonEmptyObj.isPresent());
		assertEquals(expectedEmptyObj, jsonEmptyObj.get().toString());
		assertTrue(jsonEmptyArray.isPresent());
		assertEquals(expectedEmptyArray, jsonEmptyArray.get().toString());
	}

	@Test
	public void processResponse_withBitmapImages_buildJsonCorrectly() {
		//> GIVEN
		Activity context = mock(Activity.class);
		Bitmap bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);

		Intent intent = new Intent();
		intent.putExtra("image", bitmap);

		String expectedJson = "{\"image\":\"\\/9j\\/4AAQSkZJRgABAgAAAQABAAD\\/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBk" +
				"SEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL\\/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyM" +
				"jIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL\\/wAARCAAoACgDASIAAhEBAxEB\\/8QAHwAAAQUBA" +
				"QEBAQEAAAAAAAAAAAECAwQFBgcICQoL\\/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0K" +
				"xwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipK" +
				"TlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6\\/8QAHwEAA" +
				"wEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL\\/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEK" +
				"RobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYa" +
				"HiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6\\/9oAD" +
				"AMBAAIRAxEAPwD5\\/ooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigD\\/\\/Z\"}";

		//> WHEN
		Optional<JSONObject> json = new ChtExternalApp
				.Response(intent, context)
				.getData();

		//> THEN
		assertTrue(json.isPresent());
		assertEquals(expectedJson, json.get().toString());
	}

	@Test
	public void processResponse_withUriImagePath_buildJsonCorrectly() throws IOException {
		//> GIVEN
		try (MockedStatic<BitmapFactory> bitmapFactoryMock = mockStatic(BitmapFactory.class)) {
			File fileJpg = temporaryFolder.newFile("image.jpg");
			File filePng = temporaryFolder.newFile("image.png");

			Bitmap bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);
			bitmapFactoryMock.when(() -> BitmapFactory.decodeStream(any())).thenReturn(bitmap);

			ParcelFileDescriptor parcelFileDescriptor = mock(ParcelFileDescriptor.class);
			when(parcelFileDescriptor.getFileDescriptor()).thenReturn(mock(FileDescriptor.class));

			ContentResolver contentResolver = mock(ContentResolver.class);
			when(contentResolver.openFileDescriptor(any(), any())).thenReturn(parcelFileDescriptor);

			Activity context = mock(Activity.class);
			when(context.getContentResolver()).thenReturn(contentResolver);

			Intent intent = new Intent();
			intent.putExtra("file.jpg", fileJpg.getAbsolutePath());
			intent.putExtra("file.png", filePng.getAbsolutePath());
			intent.putExtra("content.jpg", "content://some/content/location.jpg");
			intent.putExtra("content.png", "content://some/content/location.png");
			intent.putExtra("just.text", "Some normal text.");
			intent.putExtra("no.supported.file", "/storage/file/location.txt");

			String base64 = "\"\\/9j\\/4AAQSkZJRgABAgAAAQABAAD\\/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBk" +
					"SEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL\\/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyM" +
					"jIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL\\/wAARCAAoACgDASIAAhEBAxEB\\/8QAHwAAAQUBA" +
					"QEBAQEAAAAAAAAAAAECAwQFBgcICQoL\\/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0K" +
					"xwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipK" +
					"TlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6\\/8QAHwEAA" +
					"wEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL\\/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEK" +
					"RobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYa" +
					"HiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6\\/9oAD" +
					"AMBAAIRAxEAPwD5\\/ooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigD\\/\\/Z\"";
			String expectedJson = "{" +
						"\"file.jpg\":" + base64 + "," +
						"\"file.png\":" + base64 + "," +
						"\"content.jpg\":" + base64 + "," +
						"\"content.png\":" + base64 + "," +
						"\"no.supported.file\":\"\\/storage\\/file\\/location.txt\"," +
						"\"just.text\":\"Some normal text.\"" +
					"}";

			//> WHEN
			Optional<JSONObject> json = new ChtExternalApp
					.Response(intent, context)
					.getData();

			//> THEN
			assertTrue(json.isPresent());
			assertEquals(expectedJson, json.get().toString());
		}
	}

}
