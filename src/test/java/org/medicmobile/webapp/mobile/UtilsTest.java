package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.verify.domain.DomainVerificationManager;
import android.content.pm.verify.domain.DomainVerificationUserState;
import android.net.Uri;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RunWith(RobolectricTestRunner.class)
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
	public void validNavigationUrls() {
		final String[] goodBlobUrls = {
			"https://gamma-cht.dev.medicmobile.org/some/tab",
			"https://gamma-cht.dev.medicmobile.org/#/reports",
			"blob:https://gamma-cht.dev.medicmobile.org/#/reports"
		};

		for(String goodBlobUrl : goodBlobUrls) {
			assertTrue("Expected URL to be accepted, but it wasn't: " + goodBlobUrl,
				Utils.isValidNavigationUrl("https://gamma-cht.dev.medicmobile.org", goodBlobUrl));
		}
	}

	@Test
	public void nullUrlsNotValid() {
		final String[][] nullUrls = {
			{null, null},
			{"https://gamma-cht.dev.medicmobile.org", null},
			{null, "https://gamma-cht.dev.medicmobile.org"},
			{"", ""},
		};

		for(String[] nullUrlPair : nullUrls) {
			assertFalse("Not expected URLs to be accepted, but they were: " + nullUrlPair[0] + " , " + nullUrlPair[1],
				Utils.isValidNavigationUrl(nullUrlPair[0], nullUrlPair[1]));
		}
	}

	@Test
	public void noMismatchNavigationUrl() {
		assertFalse(Utils.isValidNavigationUrl(
			"https://gamma-cht.dev.medicmobile.org",
			"https://example.com/path"));
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

	@Test
	public void testAllDomainsVerified() throws PackageManager.NameNotFoundException {
		Context mockContext = Mockito.mock(Context.class);

		DomainVerificationManager mockManager = Mockito.mock(DomainVerificationManager.class);
		DomainVerificationUserState mockUserState = Mockito.mock(DomainVerificationUserState.class);
		Map<String, Integer> mockHostToStateMap = new HashMap<>();
		mockHostToStateMap.put("domain1.com", DomainVerificationUserState.DOMAIN_STATE_VERIFIED);
		mockHostToStateMap.put("domain2.com", DomainVerificationUserState.DOMAIN_STATE_VERIFIED);
		Mockito.when(mockUserState.getHostToStateMap()).thenReturn(mockHostToStateMap);
		Mockito.when(mockManager.getDomainVerificationUserState(Mockito.anyString())).thenReturn(mockUserState);
		Mockito.when(mockContext.getSystemService(DomainVerificationManager.class)).thenReturn(mockManager);
		Mockito.when(mockContext.getPackageName()).thenReturn(Mockito.anyString());

		boolean isVerified = Utils.checkIfDomainsAreVerified(mockContext);

		assertTrue(isVerified);
		Mockito.verify(mockManager).getDomainVerificationUserState(Mockito.anyString());
		Mockito.verify(mockUserState).getHostToStateMap();
	}

	@Test
	public void testSomeDomainNotVerified() throws PackageManager.NameNotFoundException {
		Context mockContext = Mockito.mock(Context.class);

		DomainVerificationManager mockManager = Mockito.mock(DomainVerificationManager.class);
		DomainVerificationUserState mockUserState = Mockito.mock(DomainVerificationUserState.class);
		Map<String, Integer> mockHostToStateMap = new HashMap<>();
		mockHostToStateMap.put("domain1.com", DomainVerificationUserState.DOMAIN_STATE_VERIFIED);
		mockHostToStateMap.put("domain2.com", DomainVerificationUserState.DOMAIN_STATE_NONE);
		Mockito.when(mockUserState.getHostToStateMap()).thenReturn(mockHostToStateMap);
		Mockito.when(mockManager.getDomainVerificationUserState(Mockito.anyString())).thenReturn(mockUserState);
		Mockito.when(mockContext.getSystemService(DomainVerificationManager.class)).thenReturn(mockManager);
		Mockito.when(mockContext.getPackageName()).thenReturn(Mockito.anyString());

		boolean isVerified = Utils.checkIfDomainsAreVerified(mockContext);

		assertFalse(isVerified);
		Mockito.verify(mockManager).getDomainVerificationUserState(Mockito.anyString());
		Mockito.verify(mockUserState).getHostToStateMap();
	}

	@Test
	public void testNoDomains() throws PackageManager.NameNotFoundException {
		// Mock Context
		Context mockContext = Mockito.mock(Context.class);

		// Mock DomainVerificationManager and UserState
		DomainVerificationManager mockManager = Mockito.mock(DomainVerificationManager.class);
		DomainVerificationUserState mockUserState = Mockito.mock(DomainVerificationUserState.class);
		Map<String, Integer> mockHostToStateMap = new HashMap<>();
		Mockito.when(mockUserState.getHostToStateMap()).thenReturn(mockHostToStateMap);
		Mockito.when(mockManager.getDomainVerificationUserState(Mockito.anyString())).thenReturn(mockUserState);

		// Mock Context methods
		Mockito.when(mockContext.getSystemService(DomainVerificationManager.class)).thenReturn(mockManager);
		Mockito.when(mockContext.getPackageName()).thenReturn(Mockito.anyString());

		// Call the method
		boolean isVerified = Utils.checkIfDomainsAreVerified(mockContext);

		assertTrue(isVerified);
		Mockito.verify(mockManager).getDomainVerificationUserState(Mockito.anyString());
		Mockito.verify(mockUserState).getHostToStateMap();
	}

	@Test
	public void testPackageManagerException() throws PackageManager.NameNotFoundException {
		try (MockedStatic<MedicLog> mockMedicLog = Mockito.mockStatic(MedicLog.class)) {
			// Mock Context
			Context mockContext = Mockito.mock(Context.class);

			// Mock DomainVerificationManager and UserState
			DomainVerificationManager mockManager = Mockito.mock(DomainVerificationManager.class);
			DomainVerificationUserState mockUserState = Mockito.mock(DomainVerificationUserState.class);
			Mockito.when(mockManager.getDomainVerificationUserState(Mockito.anyString())).thenThrow(new PackageManager.NameNotFoundException());

			// Mock Context methods
			Mockito.when(mockContext.getSystemService(DomainVerificationManager.class)).thenReturn(mockManager);
			Mockito.when(mockContext.getPackageName()).thenReturn(Mockito.anyString());
			// Call the method
			boolean isVerified = Utils.checkIfDomainsAreVerified(mockContext);

			assertTrue(isVerified);
			Mockito.verify(mockManager).getDomainVerificationUserState(Mockito.anyString());
			mockMedicLog.verify(() -> MedicLog.warn(
				any(Exception.class),
				eq("Error while getting package name")
			));
		}
	}
}
