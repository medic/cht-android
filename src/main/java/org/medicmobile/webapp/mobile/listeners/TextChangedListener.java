package org.medicmobile.webapp.mobile.listeners;

import android.text.Editable;
import android.text.TextWatcher;

public abstract class TextChangedListener implements TextWatcher {
	@Override
	public abstract void onTextChanged(CharSequence s, int start, int before, int count);

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		// Unused
	}

	@Override
	public void afterTextChanged(Editable s) {
		// Unused
	}
}
