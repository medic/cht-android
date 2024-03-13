package org.medicmobile.webapp.mobile.components.settings_dialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mockStatic;

import org.junit.Test;
import org.medicmobile.webapp.mobile.MedicLog;
import org.medicmobile.webapp.mobile.SimpleJsonClient2;
import org.mockito.MockedStatic;

public class ServerMetadataTest {
	private final String NAME = "test_name";
	private final String URL = "https://test.url";
	private final String REDACTED_URL = "test.url";

	@Test
	public void construct_withUrl() {
		try (
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class);
			MockedStatic<SimpleJsonClient2> jsonClientMock = mockStatic(SimpleJsonClient2.class)
		) {
			jsonClientMock.when(() -> SimpleJsonClient2.redactUrl(URL)).thenReturn(REDACTED_URL);

			ServerMetadata metadata = new ServerMetadata(NAME, URL);

			assertEquals(NAME, metadata.name);
			assertEquals(URL, metadata.url);
			jsonClientMock.verify(() -> SimpleJsonClient2.redactUrl(URL));
			medicLogMock.verify(() -> MedicLog.trace(
				any(ServerMetadata.class),
				eq("ServerMetadata() :: name: %s, url: %s"),
				eq(NAME),
				eq(REDACTED_URL)
			));
		}
	}

	@Test
	public void construct_withoutUrl() {
		try (
			MockedStatic<MedicLog> medicLogMock = mockStatic(MedicLog.class);
			MockedStatic<SimpleJsonClient2> jsonClientMock = mockStatic(SimpleJsonClient2.class)
		) {
			ServerMetadata metadata = new ServerMetadata(NAME);

			assertEquals(NAME, metadata.name);
			assertNull(metadata.url);
			jsonClientMock.verify(() -> SimpleJsonClient2.redactUrl((String) null));
			medicLogMock.verify(() -> MedicLog.trace(
				any(ServerMetadata.class),
				eq("ServerMetadata() :: name: %s, url: %s"),
				eq(NAME),
				eq(null)
			));
		}
	}
}
