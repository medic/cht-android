package org.medicmobile.webapp.mobile.adapters;

import android.content.Context;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.SimpleAdapter;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class FilterableListAdapter extends SimpleAdapter implements Filterable {
	private final List<Map<String, ?>> originalData;
	private final List<Map<String, ?>> filteredData;
	private final List<String> searchKeys;
	private final SearchKeyFilter filter = new SearchKeyFilter();

	public FilterableListAdapter(
		Context context,
		List<Map<String, ?>> data,
		@LayoutRes int resource,
		String[] from,
		@IdRes int[] to,
		String... searchKeys
	) {
		super(context, data, resource, from, to);
		this.filteredData = data;
		this.originalData = Collections.unmodifiableList(new ArrayList<>(data));
		this.searchKeys = Arrays.asList(searchKeys);
	}

	@Override
	public int getCount() {
		return filteredData.size();
	}

	@Override
	public Object getItem(int position) {
		return filteredData.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public Filter getFilter() {
		return filter;
	}

	private class SearchKeyFilter extends Filter {
		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			String filterString = constraint.toString().toLowerCase(Locale.getDefault());
			List<Map<String, ?>> filteredList = originalData
				.stream()
				.filter(listEntry -> hasSearchKeyMatch(listEntry, filterString))
				.collect(Collectors.toList());

			FilterResults results = new FilterResults();
			results.values = filteredList;
			results.count = filteredList.size();
			return results;
		}

		@Override
		@SuppressWarnings("unchecked")
		protected void publishResults(CharSequence constraint, FilterResults results) {
			filteredData.clear();
			filteredData.addAll((List<Map<String, ?>>) results.values);
			notifyDataSetChanged();
		}

		private boolean hasSearchKeyMatch(Map<String, ?> listEntry, String filterString) {
			return searchKeys
				.stream()
				.map(searchKey -> getSearchKeyValue(listEntry, searchKey))
				.filter(Objects::nonNull)
				.map(String::toLowerCase)
				.anyMatch(value -> value.contains(filterString));
		}

		private String getSearchKeyValue(Map<String, ?> listEntry, String searchKey) {
			Object value = listEntry.get(searchKey);
			if (value instanceof String) {
				return (String) value;
			}
			return null;
		}
	}
}
