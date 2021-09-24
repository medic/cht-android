package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.*;
import org.robolectric.*;
import org.robolectric.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class UtilsTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void isUrlRelated_goodNormalUrls() {
		final String[] goodUrls = {
			"https://example.com/medic/_design/medic/_rewrite",
		};

		for(String goodUrl : goodUrls) {
			assertTrue("Expected URL to be accepted, but it wasn't: " + goodUrl,
					Utils.isUrlRelated("https://example.com", Uri.parse(goodUrl)));
		}
	}

	@Test
	public void isUrlRelated_goodBlobs() {
		final String[] goodBlobUrls = {
			"blob:https://example.com/medic/_design/medic/_rewrite",
		};

		for(String goodBlobUrl : goodBlobUrls) {
			assertTrue("Expected URL to be accepted, but it wasn't: " + goodBlobUrl,
					Utils.isUrlRelated("https://example.com", Uri.parse(goodBlobUrl)));
		}
	}

	@Test
	public void isUrlRelated_badUrls() {
		final String[] badUrls = {
			"https://bad.domain/medic/_design/medic/_rewrite",
			"blob:https://bad.domain/medic/_design/medic/_rewrite",
			"tel:0040755458697",
			"sms:0040733898569&body=Thisisthesmsbody",
			"sms:0040733898569,0040788963214&body=Thisisthesmsbody",
		};

		for(String badUrl : badUrls) {
			assertFalse("Expected URL to be rejected, but it wasn't: " + badUrl,
					Utils.isUrlRelated("https://example.com", Uri.parse(badUrl)));
		}
	}

	@Test
	public void getUriFromFilePath_withInvalidPath_returnsNull() {
		//> WHEN
		Optional<Uri> uriWithNullPath = Utils.getUriFromFilePath(null);
		Optional<Uri> uriWithEmptyPath = Utils.getUriFromFilePath("");
		Optional<Uri> uriMissingFile = Utils.getUriFromFilePath("/storage/file.txt");

		//> THEN
		assertFalse(uriWithNullPath.isPresent());
		assertFalse(uriWithEmptyPath.isPresent());
		assertFalse(uriMissingFile.isPresent());
	}

	@Test
	public void getUriFromFilePath_withContentSchema_returnsUri() {
		//> WHEN
		Optional<Uri> uriOptional = Utils.getUriFromFilePath("content://folder/file.png");

		//> THEN
		assertTrue(uriOptional.isPresent());
		Uri uri = uriOptional.get();
		assertEquals("content", uri.getScheme());
		assertEquals("/file.png", uri.getPath());
	}

	@Test
	public void getUriFromFilePath_withFileSchema_returnsUri() throws IOException {
		//> GIVEN
		File file = temporaryFolder.newFile("some_file.txt");
		String filePath = file.getPath();

		//> WHEN
		Optional<Uri> uriOptional = Utils.getUriFromFilePath(filePath);

		//> THEN
		assertTrue(uriOptional.isPresent());
		Uri uri = uriOptional.get();
		assertEquals("file", uri.getScheme());
		assertEquals(filePath, uri.getPath());
	}

	@Test
	public void validNavigationUrl() {
		assertTrue(Utils.isValidNavigationUrl(
				"https://gamma-cht.dev.medicmobile.org",
				"https://gamma-cht.dev.medicmobile.org/some/tab"));
	}

	@Test
	public void validNavigationUrlWithBashUri() {
		assertTrue(Utils.isValidNavigationUrl(
				"https://gamma-cht.dev.medicmobile.org/",
				"https://gamma-cht.dev.medicmobile.org/#/reports"));
	}

	@Test
	public void notValidNullNavigationUrl() {
		assertFalse(Utils.isValidNavigationUrl(
				"https://gamma-cht.dev.medicmobile.org",
				null));
	}

	@Test
	public void notValidNavigationUrl() {
		assertFalse(Utils.isValidNavigationUrl(
				"https://gamma-cht.dev.medicmobile.org",
				"https://example.com"));
	}

	@Test
	public void notValidMalformedAppUrl() {
		assertFalse(Utils.isValidNavigationUrl(
				"not-valid-url",
				"https://not-valid-url.com/res"));
	}

	@Test
	public void notValidNavigationUri() {
		assertFalse(Utils.isValidNavigationUrl(
				"https://gamma-cht.dev.medicmobile.org",
				"/resource/without/base"));
	}

	@Test
	public void notValidNavigationLoginUri() {
		assertFalse(Utils.isValidNavigationUrl(
				"https://gamma-cht.dev.medicmobile.org",
				"https://gamma-cht.dev.medicmobile.org/medic/login?"));
	}

	@Test
	public void notValidNavigationRewriteUri() {
		assertFalse(Utils.isValidNavigationUrl(
				"https://gamma-cht.dev.medicmobile.org",
				"https://gamma-cht.dev.medicmobile.org/medic/_rewrite"));
	}
}

