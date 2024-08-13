package org.medicmobile.webapp.mobile.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import org.medicmobile.webapp.mobile.R;

public class ConfirmServerSelectionDialog extends DialogFragment {
	private static final String TAG = "ConfirmServerSelectionDialog";

	private final String serverName;
	private final Runnable confirm;

	public ConfirmServerSelectionDialog(String serverName, Runnable confirm) {
		this.serverName = serverName;
		this.confirm = confirm;
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		return new AlertDialog.Builder(requireContext())
			.setMessage(String.format(getString(R.string.proceedToServer), serverName))
			.setPositiveButton(getString(R.string.btnContinue), (dialog, which) -> confirm.run())
			.setNegativeButton(getString(R.string.btnCancel), (dialog, which) -> { })
			.create();
	}

	public void show(FragmentManager manager) {
		this.show(manager, TAG);
	}
}
