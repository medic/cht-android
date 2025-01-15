package org.medicmobile.webapp.mobile.dialogs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;

import androidx.fragment.app.FragmentManager;

import org.junit.Before;
import org.junit.Test;
import org.medicmobile.webapp.mobile.R;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedConstruction;

public class ConfirmServerSelectionDialogTest {
	private boolean dialogConfirmed = false;
	private ConfirmServerSelectionDialog dialog;

	@Before
	public void before() {
		dialog = spy(new ConfirmServerSelectionDialog("My Server", () -> dialogConfirmed = true));
	}

	@Test
	public void show() {
		FragmentManager fragmentManagerMock = mock(FragmentManager.class);
		doNothing().when(dialog).show(any(FragmentManager.class), anyString());

		dialog.show(fragmentManagerMock);

		verify(dialog).show(fragmentManagerMock, "ConfirmServerSelectionDialog");
		assertFalse(dialogConfirmed);
	}

	@Test
	public void onCreateDialog() {
		doReturn("Login to %s?").when(dialog).getString(R.string.proceedToServer);
		doReturn("Continue").when(dialog).getString(R.string.btnContinue);
		doReturn("Cancel").when(dialog).getString(R.string.btnCancel);
		doReturn(mock(Context.class)).when(dialog).requireContext();

		AlertDialog dialogMock = mock(AlertDialog.class);
		try (
			MockedConstruction<AlertDialog.Builder> dialogBuilderConstructorMock = mockConstruction(
				AlertDialog.Builder.class,
				(mock, context) -> {
					doReturn(mock).when(mock).setMessage(anyString());
					doReturn(mock).when(mock).setPositiveButton(anyString(), any(DialogInterface.OnClickListener.class));
					doReturn(mock).when(mock).setNegativeButton(anyString(), any(DialogInterface.OnClickListener.class));
					doReturn(dialogMock).when(mock).create();
				}
			)
		) {
			Dialog alertDialog = dialog.onCreateDialog(null);

			assertFalse(dialogConfirmed);
			assertEquals(dialogMock, alertDialog);
			assertEquals(1, dialogBuilderConstructorMock.constructed().size());
			AlertDialog.Builder builderMock = dialogBuilderConstructorMock.constructed().get(0);
			verify(builderMock).setMessage("Login to My Server?");
			ArgumentCaptor<DialogInterface.OnClickListener> onClickArgCaptor = ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
			verify(builderMock).setNegativeButton(ArgumentMatchers.eq("Cancel"), onClickArgCaptor.capture());
			onClickArgCaptor.getValue().onClick(null, 0);
			// Cancelling dialog should not run the confirm action
			assertFalse(dialogConfirmed);
			verify(builderMock).setPositiveButton(ArgumentMatchers.eq("Continue"), onClickArgCaptor.capture());
			onClickArgCaptor.getValue().onClick(null, 0);
			// Continuing dialog should run the confirm action
			assertTrue(dialogConfirmed);
		}
	}
}
