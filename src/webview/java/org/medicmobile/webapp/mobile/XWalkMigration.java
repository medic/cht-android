package org.medicmobile.webapp.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Build;

import java.io.File;

import static org.medicmobile.webapp.mobile.MedicLog.trace;

/*
 * Stolen from https://github.com/dpa99c/cordova-plugin-crosswalk-data-migration
 */
public class XWalkMigration {
	public static final String TAG = "Migration";

	private static boolean hasRun = false;

	private static String xWalkPath = "app_xwalkcore/Default";

	// Root dir for system webview data used by Android 4.4+
	private static String modernWebviewDir = "app_webview";

	// Root dir for system webview data used by Android 4.3 and below
	private static String oldWebviewDir = "app_database";

	// Directory name for local storage files used by Android 4.4+ and XWalk
	private static String modernLocalStorageDir = "Local Storage";

	// Directory name for local storage files used by Android 4.3 and below
	private static String oldLocalStorageDir = "localstorage";

	// Storage directory names used by Android 4.4+ and XWalk
	private static String[] modernAndroidStorage = {
			"Cache",
			"Cookies",
			"Cookies-journal",
			"IndexedDB",
			"databases"
	};

	private Activity activity;
	private Context context;

	private boolean isModernAndroid;
	private File appRoot;
	private File xWalkRoot;
	private File webviewRoot;

	@SuppressLint("ObsoleteSdkInt")
	public XWalkMigration(Activity a) {
		activity = a;
		context = a.getApplicationContext();
		isModernAndroid = Build.VERSION.SDK_INT >= 19;
		trace(this, "Set up Crosswalk migration, %s, %s, %s", hasRun, activity, context);
	}

	public void run() {
		trace(this, "Running Crosswalk migration");

		boolean found = lookForXwalk(context.getFilesDir());
		if (!found) {
			lookForXwalk(context.getExternalFilesDir(null));
		}

		if (found) {
			migrateData();
		} else {
			trace(this, "Crosswalk directory not found - skipping migration");
		}
	}

	private boolean lookForXwalk(File filesPath) {
		File root = getStorageRootFromFiles(filesPath);
		boolean found = testFileExists(root, xWalkPath);
		if (found) {
			trace(this, "Crosswalk directory found: %s", filesPath);
			appRoot = root;
		} else {
			trace(this, "Crosswalk directory not found: %s", filesPath);
		}
		return found;
	}

	private void migrateData() {
		xWalkRoot = constructFilePaths(appRoot, xWalkPath);

		webviewRoot = constructFilePaths(appRoot, getWebviewPath());

		boolean hasMigratedData = false;

		if (testFileExists(xWalkRoot, modernLocalStorageDir)) {
			trace(this, "Local Storage data found");
			moveDirFromXWalkToWebView(modernLocalStorageDir, getWebviewLocalStoragePath());
			trace(this, "Moved Local Storage from XWalk to System Webview");
			hasMigratedData = true;
		}

		if (isModernAndroid) {
			for (String dirName : modernAndroidStorage) {
				if (testFileExists(xWalkRoot, dirName)) {
					moveDirFromXWalkToWebView(dirName);
					trace(this, "Moved " + dirName + " from XWalk to System Webview");
					hasMigratedData = true;
				}
			}
		}

		if (hasMigratedData) {
			deleteRecursive(xWalkRoot);
			restartCordova();
		}
	}

	private void moveDirFromXWalkToWebView(String dirName) {
		File xWalkLocalStorageDir = constructFilePaths(xWalkRoot, dirName);
		File webviewLocalStorageDir = constructFilePaths(webviewRoot, dirName);
		xWalkLocalStorageDir.renameTo(webviewLocalStorageDir);
	}

	private void moveDirFromXWalkToWebView(String sourceDirName, String targetDirName) {
		File xWalkLocalStorageDir = constructFilePaths(xWalkRoot, sourceDirName);
		File webviewLocalStorageDir = constructFilePaths(webviewRoot, targetDirName);
		xWalkLocalStorageDir.renameTo(webviewLocalStorageDir);
	}


	private String getWebviewPath() {
		if (isModernAndroid) {
			return modernWebviewDir;
		} else {
			return oldWebviewDir;
		}
	}

	private String getWebviewLocalStoragePath() {
		if (isModernAndroid) {
			return modernLocalStorageDir;
		} else {
			return oldLocalStorageDir;
		}
	}

	private void restartCordova() {
		trace(this, "restarting EmbeddedBrowserActivity");
		activity.recreate();
	}


	private boolean testFileExists(File root, String name) {
		boolean status = false;
		if (!name.equals("")) {
			File newPath = constructFilePaths(root.toString(), name);
			status = newPath.exists();
			trace(this, "exists '" + newPath.getAbsolutePath() + ": " + status);
		}
		return status;
	}

	private File constructFilePaths(File file1, File file2) {
		return constructFilePaths(file1.getAbsolutePath(), file2.getAbsolutePath());
	}

	private File constructFilePaths(File file1, String file2) {
		return constructFilePaths(file1.getAbsolutePath(), file2);
	}

	private File constructFilePaths(String file1, String file2) {
		File newPath;
		if (file2.startsWith(file1)) {
			newPath = new File(file2);
		} else {
			newPath = new File(file1 + "/" + file2);
		}
		return newPath;
	}

	private File getStorageRootFromFiles(File filesDir) {
		String filesPath = filesDir.getAbsolutePath();
		filesPath = filesPath.replaceAll("/files", "");
		return new File(filesPath);
	}

	private void deleteRecursive(File fileOrDirectory) {
		if (fileOrDirectory.isDirectory())
			for (File child : fileOrDirectory.listFiles())
				deleteRecursive(child);

		fileOrDirectory.delete();
	}
}
