package org.medicmobile.webapp.mobile.adapters;

import android.content.Context;

import org.medicmobile.webapp.mobile.R;
import org.medicmobile.webapp.mobile.components.settings_dialog.ServerMetadata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ServerMetadataAdapter extends FilterableListAdapter {
	private ServerMetadataAdapter(Context context, List<Map<String, ?>> data) {
		super(
			context,
			data,
			R.layout.server_list_item,
			new String[]{ "name", "url" },
			new int[]{ R.id.txtName, R.id.txtUrl },
			"name", "url"
		);
	}

	public static ServerMetadataAdapter createInstance(Context context, List<ServerMetadata> servers) {
		return new ServerMetadataAdapter(context, adapt(servers));
	}

	@SuppressWarnings("unchecked")
	public ServerMetadata getServerMetadata(int position) {
		Map<String, String> dataMap = (Map<String, String>) this.getItem(position);
		return new ServerMetadata(dataMap.get("name"), dataMap.get("url"));
	}

	private static List<Map<String, ?>> adapt(List<ServerMetadata> servers) {
		return servers
			.stream()
			.map(server -> {
				Map<String, String> serverProperties = new HashMap<>();
				serverProperties.put("name", server.name);
				serverProperties.put("url", server.url);
				return serverProperties;
			})
			.collect(Collectors.toList());
	}
}
