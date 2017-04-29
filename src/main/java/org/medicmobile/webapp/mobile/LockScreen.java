package org.medicmobile.webapp.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
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
import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

public class LockScreen extends Activity {

//> Activity OVERRIDES
	@Override public void onBackPressed() { /* DON'T go back to the previous Activity */ }

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(DEBUG) trace(this, "Starting...");

		setContentView(R.layout.lock_screen);

		Button btn = (Button) findViewById(R.id.btnConfirmPinEntry);
		btn.setOnClickListener(new PinEntryClickListener(this) {
			public void onClick(View v) {
				if(confirmCodeEnteredCorrectly()) dismissWithMessage(R.string.tstUnlock_success);
				else rejectCode(R.string.tstUnlock_codeRejected);
			}
		});

		setUpEventListeners(this);
	}

//> PRIVATE HELPERS

//> STATIC UTILS
	static boolean isCodeSet(Activity a) {
		String unlockCode = SettingsStore.in(a).getUnlockCode();
		return unlockCode != null && !unlockCode.isEmpty();
	}

	static void changeCode(final Activity a) {
		if(!isCodeSet(a)) {
			setNewCode(a);
			return;
		}

		final AlertDialog d = createPinEntryDialog(a, true, R.string.txtOldCodePrompt);

		setClickListener(d, new PinEntryClickListener(a, d) {
			@Override public void onClick(View v) {
				if(confirmCodeEnteredCorrectly()) {
					setNewCode(a);
					dismissWithMessage(R.string.tstUnlock_oldAccepted);
				} else rejectCode(R.string.tstUnlock_codeRejected);
			}
		});

		d.show();
	}

//> STATIC HELPERS
	private static void setNewCode(final Activity a) {
		final AlertDialog d = createPinEntryDialog(a, true, R.string.txtNewCodePrompt);

		setClickListener(d, new PinEntryClickListener(a, d) {
			@Override public void onClick(View v) {
				requestConfirmation(a, enteredText());
				dismissWithMessage(R.string.tstUnlock_newAccepted);
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
						dismissWithMessage(R.string.tstUnlock_codeChanged);
					} catch(SettingsException ex) {
						warn(ex, "Failed to save new unlock code.");
						toast(R.string.tstUnlock_failed);
					}
				} else rejectCode(R.string.tstUnlock_confirmFailed);
			}
		});

		d.show();
	}

	private static View findViewById(Object parent, int viewId) {
		if(parent instanceof Dialog) {
			return ((Dialog) parent).findViewById(viewId);
		} else return ((Activity) parent).findViewById(viewId);
	}

	private static void setUpEventListeners(Object d) { // TODO rename d to v
		final EditText txtPin = (EditText) findViewById(d, R.id.txtPinEntry);

		// Ignore all touches on the PIN field, but allow it to be syled as
		// if it were editable.
		txtPin.setOnTouchListener(new OnTouchListener() {
			@SuppressLint("ClickableViewAccessibility")
			@Override public boolean onTouch(View v, MotionEvent e) {
				return true;
			}
		});

		final ViewGroup group = (ViewGroup) findViewById(d, R.id.divButtons);
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

		Button btnBackspace = (Button) findViewById(d, R.id.btnBackspace);
		btnBackspace.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) {
				CharSequence text = txtPin.getText();
				if(text.length() > 0) text = text.subSequence(0, text.length()-1);
				txtPin.setText(text);
				txtPin.setSelection(text.length());
			}
		});
	}

	private static AlertDialog createPinEntryDialog(Activity a, boolean cancellable, int title) {
		return new AlertDialog.Builder(a)
				.setPositiveButton(R.string.btnOk, null)
				.setCancelable(cancellable)
				.setTitle(title)
				.setView(view(a, R.layout.pin_entry_dialog))
				.create();
	}

	private static void setClickListener(final AlertDialog d, final PinEntryClickListener positiveClickListener) {
		d.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override public void onShow(DialogInterface _d) {
				Button button = d.getButton(BUTTON_POSITIVE);
				button.setOnClickListener(positiveClickListener);

				setUpEventListeners(d);
			}
		});
	}

	private static View view(Activity a, int layoutId) {
		return a.getLayoutInflater().inflate(layoutId, null);
	}
}

abstract class LockableActivity extends Activity {
	private boolean finishing;

	@Override public void finish() {
		finishing = true;
		super.finish();
	}

	@Override public void onPause() {
		super.onPause();

		// Handle the lock screen here, as doing it in onResume() means
		// that the screen we're trying to block is displayed momentarily.
		//
		// N.B. if landscape mode is enabled for any activities, then
		// onPause() will be fired when the screen rotates.  We will have
		// to detect this in onConfigurationChanged() and ignore.

		if(finishing || !LockScreen.isCodeSet(this)) return;

		startActivity(new Intent(this, LockScreen.class));
	}
}

abstract class PinEntryClickListener implements OnClickListener {
	private final Activity activity;
	private final AlertDialog dialog;

	protected PinEntryClickListener(Activity activity) {
		this.activity = activity;
		this.dialog = null;
	}

	protected PinEntryClickListener(Activity parentActivity, AlertDialog dialog) {
		this.activity = parentActivity;
		this.dialog = dialog;
	}

	protected boolean confirmCodeEnteredCorrectly() {
		String unlockCode = SettingsStore.in(activity).getUnlockCode();
		return enteredText().equals(unlockCode);
	}

	protected String enteredText() {
		return txtPinEntry().getText().toString();
	}

	protected void rejectCode(int textId) {
		txtPinEntry().setText("");
		toast(textId);
	}

	protected void dismissWithMessage(int textId) {
		toast(textId);
		if(isDialog()) dialog.dismiss();
		else activity.finish();
	}

	protected void toast(int textId) { Toast.makeText(activity, textId, Toast.LENGTH_SHORT).show(); }

	private TextView txtPinEntry() {
		return (TextView) (isDialog() ? dialog.findViewById(R.id.txtPinEntry) :
				              activity.findViewById(R.id.txtPinEntry));
	}

	private boolean isDialog() {
		return dialog != null;
	}
}
