package org.medicmobile.webapp.mobile;

import android.Manifest.permission;
import android.app.Activity;

import medic.android.PromptForPermissionsActivity;

public class MmPromptForPermissionsActivity extends PromptForPermissionsActivity {
	private static final Object[][] PERMISSIONS_REQUESTS = {
		/* location */ { R.string.txtPermissionsPrompt_location, new String[] { permission.ACCESS_COARSE_LOCATION, permission.ACCESS_FINE_LOCATION } },
	};

	@Override protected boolean refuseToFunctionWithoutPermissions() { return true; }

	@Override protected Object[][] getPermissionRequests() { return PERMISSIONS_REQUESTS; }

	@Override protected Class<? extends Activity> getNextActivityClass() { return EmbeddedBrowserActivity.class; }

	public static void startPermissionsRequestChainFrom(Activity from) {
		new MmPromptForPermissionsActivity().startPermissionsRequestChain(from);
	}
}
