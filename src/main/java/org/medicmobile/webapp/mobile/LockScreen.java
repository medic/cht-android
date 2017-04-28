package org.medicmobile.webapp.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
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
import static org.medicmobile.webapp.mobile.MedicLog.warn;

class LockScreen {
	private LockScreen() {}

	static boolean isCodeSet(Activity a) {
		String unlockCode = SettingsStore.in(a).getUnlockCode();
		return unlockCode != null && !unlockCode.isEmpty();
	}

	static void showFor(final Activity a, OnDismissListener onDismiss) {
		final AlertDialog d = createPinEntryDialog(a, false, R.string.txtUnlockPrompt);

		setClickListener(d, new PinEntryClickListener(a, d) {
			public void onClick(View v) {
				if(getCurrentCode().equals(enteredText())) dismiss();
				else toast("Try again.");
			}
		});

		d.setOnDismissListener(onDismiss);

		d.show();
	}

	static void changeCode(final Activity a) {
		if(!isCodeSet(a)) {
			setNewCode(a);
			return;
		}

		final AlertDialog d = createPinEntryDialog(a, true, R.string.txtOldCodePrompt);

		setClickListener(d, new PinEntryClickListener(a, d) {
			@Override public void onClick(View v) {
				if(getCurrentCode().equals(enteredText())) {
					setNewCode(a);
					dismiss();
				} else toast("Try again.");
			}
		});

		d.show();
	}

	private static void setNewCode(final Activity a) {
		final AlertDialog d = createPinEntryDialog(a, true, R.string.txtNewCodePrompt);

		setClickListener(d, new PinEntryClickListener(a, d) {
			@Override public void onClick(View v) {
				requestConfirmation(a, enteredText());
				dismiss();
			}
		});

		d.show();
	}

	private static void requestConfirmation(final Activity a, final String firstEntry) {
		final AlertDialog d = createPinEntryDialog(a, true, R.string.txtNewCodeConfirmPrompt);

		setClickListener(d, new PinEntryClickListener(a, d) {
			@Override public void onClick(View v) {
				if(firstEntry.equals(enteredText())) {
					try {
						SettingsStore.in(a).updateWithUnlockCode(firstEntry);
						toast("New code successfully set.");
						dismiss();
					} catch(SettingsException ex) {
						warn(ex, "Failed to save new unlock code.");
						toast("Failed to save unlock code.");
					}
				} else toast("Code does not match.  Try again.");
			}
		});

		d.show();
	}

	private static AlertDialog createPinEntryDialog(Activity a, boolean cancellable, int title) {
		return new AlertDialog.Builder(a)
				.setPositiveButton(R.string.btnOk, null)
				.setCancelable(cancellable)
				.setTitle(title)
				.setView(view(a, R.layout.pin_entry))
				.create();
	}

	private static void setClickListener(final AlertDialog d, final PinEntryClickListener positiveClickListener) {
		d.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override public void onShow(DialogInterface _d) {
				Button button = d.getButton(BUTTON_POSITIVE);
				button.setOnClickListener(positiveClickListener);

				final EditText txtPin = (EditText) d.findViewById(R.id.txtPinEntry);

				// Ignore all touches on the PIN field, but allow it to be syled as
				// if it were editable.
				txtPin.setOnTouchListener(new OnTouchListener() {
					@SuppressLint("ClickableViewAccessibility")
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
						txtPin.setSelection(text.length());
					}
				});
			}
		});
	}

	private static View view(Activity a, int layoutId) {
		return a.getLayoutInflater().inflate(layoutId, null);
	}
}

abstract class LockableActivity extends Activity {
	private boolean justCreated = true;
	private AlertDialog unlockDialog;

	// TODO to stop the screen from being displayed momentarily on-resume,
	// put an overlay immediately as part of on-sleep
	@Override public void onPause() {
		super.onPause();
	}

	@Override public void onResume() {
		super.onResume();

		if(justCreated) {
			justCreated = false;
			return;
		}

		if(unlockDialog != null && unlockDialog.isShowing()) return;

		if(!LockScreen.isCodeSet(this)) return;

		LockScreen.showFor(this, new OnDismissListener() {
			public void onDismiss(DialogInterface dialog) {
				LockableActivity.this.unlockDialog = null;
			}
		});
	}
}

abstract class PinEntryClickListener implements OnClickListener {
	private final Activity parentActivity;
	private final AlertDialog dialog;

	protected PinEntryClickListener(Activity parentActivity, AlertDialog dialog) {
		this.parentActivity = parentActivity;
		this.dialog = dialog;
	}

	protected String enteredText() {
		return ((TextView) dialog.findViewById(R.id.txtPinEntry)).getText().toString();
	}

	protected String getCurrentCode() {
		String unlockCode = SettingsStore.in(parentActivity).getUnlockCode();
		return unlockCode == null ? "" : unlockCode;
	}

	protected void dismiss() { dialog.dismiss(); }
	protected void toast(String message) { Toast.makeText(parentActivity, message, Toast.LENGTH_SHORT).show(); }
}
