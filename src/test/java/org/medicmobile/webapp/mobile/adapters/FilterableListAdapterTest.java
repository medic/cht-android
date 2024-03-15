package org.medicmobile.webapp.mobile.adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@RunWith(RobolectricTestRunner.class)
public class FilterableListAdapterTest {
	private static final List<Map<String, ?>> SERVER_DATA = List.of(
		Map.of("name", "first server name", "url", "https://my.first.server")
	);
	private static final List<Map<String, ?>> MULTI_SERVER_DATA = List.of(
		Map.of("name", "first name", "url", "https://my.first.server"),
		Map.of("name", "second name", "url", "https://my.second.org"),
		Map.of("name", "third server name", "url", "https://my.third.com")
	);

	@Test
	public void count_empty() {
		assertEquals(0, getAdapter(Collections.emptyList()).getCount());
	}

	@Test
	public void count_single() {
		assertEquals(SERVER_DATA.size(), getAdapter(SERVER_DATA).getCount());
	}

	@Test
	public void count_multiple() {
		assertEquals(MULTI_SERVER_DATA.size(), getAdapter(MULTI_SERVER_DATA).getCount());
	}

	@Test
	public void getItem_validPosition() {
		assertEquals(MULTI_SERVER_DATA.get(1), getAdapter(MULTI_SERVER_DATA).getItem(1));
	}

	@Test
	public void getItem_invalidPosition() {
		FilterableListAdapter adapter = getAdapter(MULTI_SERVER_DATA);
		assertThrows(
			"Index 3 out of bounds for length 3",
			ArrayIndexOutOfBoundsException.class,
			() -> adapter.getItem(3)
		);
	}

	@Test
	public void getItemId() {
		FilterableListAdapter adapter = getAdapter(Collections.emptyList());
		Stream
			.of(1, 2, -1)
			.mapToInt(id -> id)
			.forEach(id -> assertEquals(id, adapter.getItemId(id)));
	}

	@Test
	public void filter_allMatch() {
		List<Map<String, ?>> filteredData = new ArrayList<>(MULTI_SERVER_DATA);
		getAdapter(filteredData, "name")
			.getFilter()
			.filter("name");
		assertEquals(MULTI_SERVER_DATA, filteredData);
	}

	@Test
	public void filter_noneMatch() {
		List<Map<String, ?>> filteredData = new ArrayList<>(MULTI_SERVER_DATA);
		getAdapter(filteredData, "name")
			.getFilter()
			.filter("nothing matches");
		assertEquals(Collections.emptyList(), filteredData);
	}

	@Test
	public void filter_someMatch() {
		List<Map<String, ?>> filteredData = new ArrayList<>(MULTI_SERVER_DATA);
		getAdapter(filteredData, "name")
			.getFilter()
			.filter("ir");
		assertEquals(List.of(MULTI_SERVER_DATA.get(0), MULTI_SERVER_DATA.get(2)), filteredData);
	}

	@Test
	public void filter_matchWithMultipleKeys() {
		List<Map<String, ?>> filteredData = new ArrayList<>(MULTI_SERVER_DATA);
		getAdapter(filteredData, "name", "url")
			.getFilter()
			.filter("server");
		// One has "server" in name and the other in url
		assertEquals(List.of(MULTI_SERVER_DATA.get(0), MULTI_SERVER_DATA.get(2)), filteredData);
	}

	@Test
	public void filter_emptyString() {
		List<Map<String, ?>> filteredData = new ArrayList<>(MULTI_SERVER_DATA);
		getAdapter(filteredData, "name")
			.getFilter()
			.filter("");
		assertEquals(MULTI_SERVER_DATA, filteredData);
	}

	@Test
	public void filter_caseInsensitive() {
		List<Map<String, ?>> filteredData = new ArrayList<>(MULTI_SERVER_DATA);
		getAdapter(filteredData, "name")
			.getFilter()
			.filter("NaMe");
		assertEquals(MULTI_SERVER_DATA, filteredData);
	}

	@Test
	public void filter_searchKeyNotInData() {
		List<Map<String, ?>> filteredData = new ArrayList<>(MULTI_SERVER_DATA);
		getAdapter(filteredData, "doesNotExist")
			.getFilter()
			.filter("name");
		assertEquals(Collections.emptyList(), filteredData);
	}

	@Test
	public void filter_someSearchKeysNotInData() {
		List<Map<String, ?>> filteredData = new ArrayList<>(MULTI_SERVER_DATA);
		getAdapter(filteredData, "name", "doesNotExist")
			.getFilter()
			.filter("name");
		assertEquals(MULTI_SERVER_DATA, filteredData);
	}

	private FilterableListAdapter getAdapter(List<Map<String, ?>> data) {
		return getAdapter(data, "name", "url");
	}

	private FilterableListAdapter getAdapter(List<Map<String, ?>> data, String... searchKeys) {
		int resourceId = 1;
		String[] fromKeys = new String[]{"name", "url"};
		int[] toIds = new int[]{2, 3};
		return new FilterableListAdapter(Mockito.mock(Context.class), data, resourceId, fromKeys, toIds, searchKeys);
	}
}
