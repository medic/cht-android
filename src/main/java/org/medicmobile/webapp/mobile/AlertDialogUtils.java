package org.medicmobile.webapp.mobile;

import android.app.AlertDialog;
import android.content.Context;

public abstract class AlertDialogUtils {

	/**
	 * Display a simple dialog window with a title, a message, and
	 * and a "OK" button.
	 */
	public static void show(
			Context context,
			String title,
			String message
	) {
		show(context, title, message, context.getResources().getString(R.string.btnOk));
	}

	/**
	 * Display a simple dialog window with a title, a message, and
	 * and a confirmation button.
	 */
	public static void show(
		Context context,
		String title,
		String message,
		String okLabel
	) {
		new AlertDialog.Builder(context)
			.setTitle(title)
			.setMessage(message)
			.setNeutralButton(okLabel, (dialog, which) -> dialog.dismiss())
			.create().show();
	}
}
