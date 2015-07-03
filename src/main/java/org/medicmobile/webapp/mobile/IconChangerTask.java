package org.medicmobile.webapp.mobile;

import android.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.content.res.*;

import java.io.*;
import java.net.*;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static android.content.Intent.*;

public class IconChangerTask extends AsyncTask<Object, Void, Void> {
	private static final String CHANGER_IMP = BuildConfig.ICON_CHANGER;

	protected Void doInBackground(Object... args) {
		assert(args.length == 3);
		Context ctx = (Context) args[0];
		Settings oldSettings = (Settings) args[1];
		Settings newSettings = (Settings) args[2];

		if(oldSettings != null &&
				oldSettings.appUrl.equals(newSettings.appUrl)) {
			if(DEBUG) log("Not changing app icon, as URL has not changed.");
			return null;
		}

		IconChanger changer = createChanger(ctx, oldSettings, newSettings);
		if(changer == null) {
			if(DEBUG) log("Icon will not be changed - changer type not recognised/supported: %s", CHANGER_IMP);
			return null;
		}

		if(DEBUG) log("Changing app icon for '%s' with changer %s", newSettings.appUrl, changer.getClass());
		changer.change();

		return null;
	}

	private IconChanger createChanger(Context ctx, Settings oldSettings, Settings newSettings) {
		if(DEBUG) log("Attempting to load icon changer of type: %s...", CHANGER_IMP);
		if(CHANGER_IMP.equals("shortcut")) {
			return new ShortcutIconChanger(ctx, oldSettings, newSettings);
		}
		return new AliasIconChanger(ctx, oldSettings, newSettings);
	}

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | IconChangerTask :: " +
				String.format(message, extras));
	}
}

interface IconChanger {
	void change();
}

class AliasIconChanger implements IconChanger {
	private static final String PACKAGE = StartupActivity.class.getPackage().getName();
	private static final String STARTUP_ACTIVITY = StartupActivity.class.getName();

	private final Context ctx;
	private final Settings oldSettings;
	private final Settings newSettings;
	private final PreconfigProvider preconfig;

	public AliasIconChanger(Context ctx, Settings oldSettings, Settings newSettings) {
		this.ctx = ctx;
		this.oldSettings = oldSettings;
		this.newSettings = newSettings;
		this.preconfig = new PreconfigProvider(ctx);
	}

	public void change() {
		PackageManager packageManager = ctx.getPackageManager();
		packageManager.setComponentEnabledSetting(componentFor(oldSettings),
				PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
				PackageManager.DONT_KILL_APP);
		packageManager.setComponentEnabledSetting(componentFor(newSettings),
				PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
				PackageManager.DONT_KILL_APP);
	}

	private ComponentName componentFor(Settings s) {
		return new ComponentName(PACKAGE, activityNameFor(s));
	}

	private String activityNameFor(Settings s) {
		if(s == null) return STARTUP_ACTIVITY;

		PreconfigOption p = preconfig.with(s.appUrl);

		if(p == null) return STARTUP_ACTIVITY;

		return String.format("%s-%s", STARTUP_ACTIVITY, p.id);
	}

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | AliasIconChanger :: " +
				String.format(message, extras));
	}
}

class ShortcutIconChanger implements IconChanger {
	private static final String INSTALL_SHORTCUT =
			"com.android.launcher.action.INSTALL_SHORTCUT";
	private static final String UNINSTALL_SHORTCUT =
			"com.android.launcher.action.UNINSTALL_SHORTCUT";

	private final Context ctx;
	private final Settings oldSettings;
	private final Settings newSettings;
	private final PreconfigProvider preconfig;

	public ShortcutIconChanger(Context ctx, Settings oldSettings, Settings newSettings) {
		this.ctx = ctx;
		this.oldSettings = oldSettings;
		this.newSettings = newSettings;
		this.preconfig = new PreconfigProvider(ctx);
	}

	public void change() {
		if(oldSettings != null &&
				preconfig.existsFor(oldSettings.appUrl)) {
			deleteOldShortcut();
		} else {
			if(DEBUG) log("Not deleting icon for %s as no preconfig was found.", newSettings.appUrl);
		}

		if(preconfig.existsFor(newSettings.appUrl)) {
			createNewShortcut();
		} else {
			if(DEBUG) log("Not creating icon for %s as no preconfig was found.", newSettings.appUrl);
		}

	}

	private void deleteOldShortcut() {
		if(DEBUG) log("deleteOldShortcut() :: ENTRY");

		Intent delete = new Intent();
		delete.putExtra(EXTRA_SHORTCUT_INTENT, startupIntentFor(ctx));
		delete.putExtra(EXTRA_SHORTCUT_NAME, shortcutNameFor(oldSettings));
		delete.setAction(UNINSTALL_SHORTCUT);

		if(DEBUG) log("calling sendBroadcast() for intent with action %s...", UNINSTALL_SHORTCUT);
		ctx.sendBroadcast(delete);
	}

	private void createNewShortcut() {
		if(DEBUG) log("createNewShortcut() :: ENTRY");

		Intent install = new Intent();
		install.putExtra(EXTRA_SHORTCUT_INTENT, startupIntentFor(ctx));
		install.putExtra(EXTRA_SHORTCUT_NAME, shortcutNameFor(newSettings));
		install.putExtra(EXTRA_SHORTCUT_ICON_RESOURCE,
				iconResourceFor(newSettings));

		install.setAction(INSTALL_SHORTCUT);
		if(DEBUG) log("calling sendBroadcast() for intent with action %s...", INSTALL_SHORTCUT);
		ctx.sendBroadcast(install);
	}

	private Intent startupIntentFor(Context ctx) {
		Intent launch = new Intent(ctx, StartupActivity.class);
		launch.setAction(ACTION_MAIN);
		return launch;
	}

	private String shortcutNameFor(Settings s) {
		return preconfig.with(s.appUrl).description;
	}

	private Intent.ShortcutIconResource iconResourceFor(Settings s) {
		TypedArray icons = ctx.getResources().obtainTypedArray(
				R.array.preconfig_icons);
		// index is offset by 1 as there is no icon for the first preconfig
		// option - the "Custom URL"
		int id = icons.getResourceId(preconfig.indexOf(s.appUrl)-1, -1);
		return Intent.ShortcutIconResource.fromContext(ctx, id);
	}

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | ShortcutIconChanger :: " +
				String.format(message, extras));
	}
}
