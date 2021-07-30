package org.medicmobile.webapp.mobile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import junit.framework.TestCase;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ChtExternalAppLauncherTest extends TestCase {

	@Test
	public void createIntent_withAllAttributesReceived_returnsIntentCorrectly() throws JSONException {
		//> GIVEN
		Set<String> expectedCategoryUri = new HashSet<>();
		expectedCategoryUri.add("a.category");

		Set<String> expectedCategoryType = new HashSet<>();
		expectedCategoryType.add("a.different.category");

		Uri uri = Uri.parse("example://some:action");
		ChtExternalApp chtExternalAppUri = new ChtExternalApp(
				"an.action",
				"a.category",
				null, // When sending Uri, automatically type is cleared.
				new JSONObject("{ \"name\": \"Eric\", \"id\": 1234 }"),
				uri,
				"org.example",
				5
		);
		ChtExternalApp chtExternalAppType = new ChtExternalApp(
				"an.different.action",
				"a.different.category",
				"a.type",
				new JSONObject("{ \"name\": \"Anna\", \"details\": { \"phone\": \"999 999 9999\" } }"),
				null, // When sending type, automatically data (Uri) is cleared.
				"org.another.example",
				0
		);

		//> WHEN
		Intent intentUri = ChtExternalAppLauncher.createIntent(chtExternalAppUri);
		Intent intentType = ChtExternalAppLauncher.createIntent(chtExternalAppType);

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
		ChtExternalApp chtExternalApp = new ChtExternalApp(null, null, null, null, null, null, null);

		//> WHEN
		Intent intentUri = ChtExternalAppLauncher.createIntent(chtExternalApp);

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
				"\"a.boolean\": true," +
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

		ChtExternalApp chtExternalAppSingles = new ChtExternalApp(null, null, null, singlesExtras, null, null, null);
		ChtExternalApp chtExternalAppArrays = new ChtExternalApp(null, null, null, arraysExtras, null, null, null);

		//> WHEN
		Intent singlesIntent = ChtExternalAppLauncher.createIntent(chtExternalAppSingles);
		Intent arraysIntent = ChtExternalAppLauncher.createIntent(chtExternalAppArrays);

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

		ChtExternalApp chtExternalApp = new ChtExternalApp(null, null, null, extras, null, null, null);

		//> WHEN
		Intent intent = ChtExternalAppLauncher.createIntent(chtExternalApp);

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
		ChtExternalApp chtExternalApp = new ChtExternalApp(null, null, null, extras, null, null, null);

		//> WHEN
		Intent intent = ChtExternalAppLauncher.createIntent(chtExternalApp);

		//> THEN
		Bundle result = intent.getExtras();
		assertNull(result);
	}

	@Test
	public void processResponse_withSimpleData_buildScriptCorrectly() {
		//> GIVEN
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
				"\"an.int.array\":[5,9]," +
				"\"a.double\":2.8," +
				"\"a.long\":2147483649," +
				"\"a.string\":\"some text\"," +
				"\"an.int\":5," +
				"\"a.boolean.array\":[true,false,true]," +
				"\"a.boolean\":true," +
				"\"a.string.array\":[\"some text\",\"another text\"]," +
				"\"a.long.array\":[2147483649,2147483700]," +
				"\"a.double.array\":[2.8,5.5]" +
				"}";

		String expectedScript = "try {" +
				"const api = window.CHTCore.AndroidApi;" +
				"if (api && api.resolveCHTExternalAppResponse) {" +
				"  api.resolveCHTExternalAppResponse(" + expectedJsonData + ");" +
				"}" +
				"} catch (error) { " +
				"  console.error('ChtExternalAppLauncher :: Error on sending intent response to CHT-Core webapp', error);" +
				"}";

		//> WHEN
		String script = ChtExternalAppLauncher.processResponse(intent);

		//> THEN
		assertEquals(expectedScript, script);
	}

	@Test
	public void processResponse_withNestedObjects_buildScriptCorrectly() {
		//> GIVEN
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
						"\"relatives\":[\"Pepe\",\"John\",\"Matt\"]," +
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
						"\"a.boolean.array\":[true,false,true]," +
						"\"a.description\":\"some awesome data\"," +
						"\"a.double.array\":[2.8,5.5]" +
					"}" +
				"}" +
				"}";

		String expectedScript = "try {" +
				"const api = window.CHTCore.AndroidApi;" +
				"if (api && api.resolveCHTExternalAppResponse) {" +
				"  api.resolveCHTExternalAppResponse(" + expectedJsonData + ");" +
				"}" +
				"} catch (error) { " +
				"  console.error('ChtExternalAppLauncher :: Error on sending intent response to CHT-Core webapp', error);" +
				"}";

		//> WHEN
		String script = ChtExternalAppLauncher.processResponse(intent);

		//> THEN
		assertEquals(expectedScript, script);
	}

	@Test
	public void processResponse_withNoData_buildScriptCorrectly() {
		//> GIVEN
		Intent intent = new Intent();

		String expectedScript = "try {" +
				"const api = window.CHTCore.AndroidApi;" +
				"if (api && api.resolveCHTExternalAppResponse) {" +
				"  api.resolveCHTExternalAppResponse(null);" +
				"}" +
				"} catch (error) { " +
				"  console.error('ChtExternalAppLauncher :: Error on sending intent response to CHT-Core webapp', error);" +
				"}";

		//> WHEN
		String script = ChtExternalAppLauncher.processResponse(intent);

		//> THEN
		assertEquals(expectedScript, script);
	}

	@Test
	public void processResponse_withEmptyData_buildScriptCorrectly() {
		//> GIVEN
		Intent intentEmptyObj = new Intent();
		intentEmptyObj.putExtra("stats", new Bundle());

		Intent intentEmptyArray = new Intent();
		intentEmptyArray.putExtra("stats", new ArrayList<>());

		String expectedEmptyObj = "{\"stats\":{}}";
		String expectedEmptyArray = "{\"stats\":[]}";

		String expectedScriptEmptyObj = "try {" +
				"const api = window.CHTCore.AndroidApi;" +
				"if (api && api.resolveCHTExternalAppResponse) {" +
				"  api.resolveCHTExternalAppResponse(" + expectedEmptyObj + ");" +
				"}" +
				"} catch (error) { " +
				"  console.error('ChtExternalAppLauncher :: Error on sending intent response to CHT-Core webapp', error);" +
				"}";

		String expectedScriptEmptyArray = "try {" +
				"const api = window.CHTCore.AndroidApi;" +
				"if (api && api.resolveCHTExternalAppResponse) {" +
				"  api.resolveCHTExternalAppResponse(" + expectedEmptyArray + ");" +
				"}" +
				"} catch (error) { " +
				"  console.error('ChtExternalAppLauncher :: Error on sending intent response to CHT-Core webapp', error);" +
				"}";

		//> WHEN
		String scriptEmptyObj = ChtExternalAppLauncher.processResponse(intentEmptyObj);
		String scriptEmptyArray = ChtExternalAppLauncher.processResponse(intentEmptyArray);

		//> THEN
		assertEquals(expectedScriptEmptyObj, scriptEmptyObj);
		assertEquals(expectedScriptEmptyArray, scriptEmptyArray);
	}
}
