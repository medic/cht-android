package org.medicmobile.webapp.mobile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import java.io.File;

import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

/*
 * Stolen from https://github.com/dpa99c/cordova-plugin-crosswalk-data-migration
 */
public class XWalkMigration {
	public static final String TAG = "Migration";

	private static final String xWalkPath = "app_xwalkcore/Default";

	// Root dir for system webview data used by Android 4.4+
	private static final String modernWebviewDir = "app_webview";

	// Root dir for system webview data used by Android 4.3 and below
	private static final String oldWebviewDir = "app_database";

	// Directory name for local storage files used by Android 4.4+ and XWalk
	private static final String modernLocalStorageDir = "Local Storage";

	// Directory name for local storage files used by Android 4.3 and below
	private static final String oldLocalStorageDir = "localstorage";

	// Storage directory names used by Android 4.4+ and XWalk
	private static final String[] modernAndroidStorage = {
			"Cache",
			"Cookies",
			"Cookies-journal",
			"IndexedDB",
			"databases"
	};

	@SuppressLint("ObsoleteSdkInt")
	private static final boolean isModernAndroid = Build.VERSION.SDK_INT >= 19;

	private boolean xWalkFound;
	private File appRoot;
	private File xWalkRoot;
	private File webviewRoot;

	public XWalkMigration(Context context) {
		xWalkFound = lookForXwalk(context.getFilesDir());
		if (!xWalkFound) {
			xWalkFound = lookForXwalk(context.getExternalFilesDir(null));
		}
	}

	/**
	 * Check whether the migration needs to be done.
	 */
	public boolean hasToMigrate() {
		return xWalkFound;	// if Crosswalk directory found => need to migrate
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

	/**
	 * Migrate the data from XWalk to Webview
	 */
	public void run() {
		if (!xWalkFound) return;

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
					trace(this, "Moved %s from XWalk to System Webview", dirName);
					hasMigratedData = true;
				}
			}
		}

		if (hasMigratedData) {
			deleteRecursive(xWalkRoot);
		}
	}

	private void moveDirFromXWalkToWebView(String dirName) {
		File xWalkLocalStorageDir = constructFilePaths(xWalkRoot, dirName);
		File webviewLocalStorageDir = constructFilePaths(webviewRoot, dirName);
		boolean renamed = xWalkLocalStorageDir.renameTo(webviewLocalStorageDir);

		if (!renamed) {
			warn(this, "XWalkMigration :: Cannot move directory from XWalk to WebView. Directory: %s", dirName);
		}
	}

	private void moveDirFromXWalkToWebView(String sourceDirName, String targetDirName) {
		File xWalkLocalStorageDir = constructFilePaths(xWalkRoot, sourceDirName);
		File webviewLocalStorageDir = constructFilePaths(webviewRoot, targetDirName);
		boolean renamed = xWalkLocalStorageDir.renameTo(webviewLocalStorageDir);

		if (!renamed) {
			warn(this, "XWalkMigration :: Cannot move directory from XWalk to WebView. XWalk directory: %s, WebView: %s", sourceDirName, targetDirName);
		}
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

	private boolean testFileExists(File root, String name) {
		boolean status = false;
		if (!name.isEmpty()) {
			File newPath = constructFilePaths(root.toString(), name);
			status = newPath.exists();
			trace(this, "testFileExists() :: '%s': %s", newPath.getAbsolutePath(), status);
		}
		return status;
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
		if (fileOrDirectory == null) {
			return;
		}

		if (fileOrDirectory.isDirectory()) {
			File[] files = fileOrDirectory.listFiles();
			if (files != null) {
				for (File child : files) {
					deleteRecursive(child);
				}
			}
		}

		boolean deleted = fileOrDirectory.delete();

		if (!deleted) {
			warn(this, "XWalkMigration :: Cannot delete file or directory: %s", fileOrDirectory.getPath());
		}
	}
}
