package org.medicmobile.webapp.mobile;

import android.app.ProgressDialog;
import android.content.Context;

public final class Utils {
	public static ProgressDialog showProgressDialog(Context ctx, String message) {
		ProgressDialog p = new ProgressDialog(ctx);
		p.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		if(message != null) p.setMessage(message);
		p.setIndeterminate(true);
		p.setCanceledOnTouchOutside(false);
		p.show();
		return p;
	}
}
