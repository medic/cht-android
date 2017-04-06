package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import static android.app.AlertDialog.BUTTON_POSITIVE;
import static org.medicmobile.webapp.mobile.MedicLog.trace;

class LockScreen {
	private LockScreen() {}

	public static AlertDialog showFor(final Activity a) {
		final AlertDialog d = new AlertDialog.Builder(a)
				.setCancelable(false)
				.setTitle("Enter PIN to Unlock…")
				.setView(view(a, R.layout.pin_entry))
				.setPositiveButton("OK", null)
				.create();

		d.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override public void onShow(DialogInterface _d) {
				Button button = d.getButton(BUTTON_POSITIVE);
				button.setOnClickListener(new PinVerifier(d, a));

				final EditText txtPin = (EditText) d.findViewById(R.id.txtPinEntry);
				// Ignore all touches on the PIN field, but allow it to be syled as
				// if it were editable.
				txtPin.setOnTouchListener(new OnTouchListener() {
					@Override public boolean onTouch(View v, MotionEvent e) {
						return true;
					}
				});

				final ViewGroup group = (ViewGroup) d.findViewById(R.id.divButtons);
				int i = group.getChildCount();
				OnClickListener buttonListener = new OnClickListener() {
					@Override public void onClick(View v) {
						String newText = txtPin.getText() + ((Button) v).getText().toString();
						txtPin.setText(newText);
						txtPin.setSelection(newText.length());
					}
				};
				while(--i >= 0) {
					Button b = (Button) group.getChildAt(i);
					b.setOnClickListener(buttonListener);
				}

				Button btnBackspace = (Button) d.findViewById(R.id.btnBackspace);
				btnBackspace.setOnClickListener(new OnClickListener() {
					@Override public void onClick(View v) {
						CharSequence text = txtPin.getText();
						if(text.length() > 0) text = text.subSequence(0, text.length()-1);
						txtPin.setText(text);
					}
				});
			}
		});
		d.show();

		return d;
	}

	private static View view(Activity a, int layoutId) {
		return a.getLayoutInflater().inflate(layoutId, null);
	}
}

abstract class LockableActivity extends Activity {
	private AlertDialog unlockDialog;

	@Override public void onResume() {
		super.onResume();

		if(unlockDialog != null && unlockDialog.isShowing()) return;

		String unlockCode = SettingsStore.in(this).getUnlockCode();
		if(unlockCode == null || unlockCode.isEmpty()) return;

		unlockDialog = LockScreen.showFor(this);
		unlockDialog.setOnDismissListener(new OnDismissListener() {
			public void onDismiss(DialogInterface dialog) {
				LockableActivity.this.unlockDialog = null;
			}
		});
	}
}

class PinVerifier implements OnClickListener {
	private final AlertDialog dialog;
	private final Context ctx;

	PinVerifier(AlertDialog dialog, Context ctx) {
		this.dialog = dialog;
		this.ctx = ctx;
	}

	public void onClick(View v) {
		trace(this, "onClick() :: entered=%s", getEnteredText());
		String unlockCode = SettingsStore.in(ctx).getUnlockCode();
		if(unlockCode.equals(getEnteredText())) dialog.dismiss();
		else Toast.makeText(ctx, "Try again.", Toast.LENGTH_SHORT).show();
	}

	private String getEnteredText() {
		return ((TextView) dialog.findViewById(R.id.txtPinEntry)).getText().toString();
	}
}
