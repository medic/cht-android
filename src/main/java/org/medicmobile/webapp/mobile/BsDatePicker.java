package org.medicmobile.webapp.mobile;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import bikramsambat.BsCalendar;
import bikramsambat.DevanagariDigitConverter;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static bikramsambat.BsCalendar.MONTH_NAMES;

// TODO this should be moved to its own project within the bikram-sambat repo
public class BsDatePicker {
	private final DevanagariDigitConverter dev = DevanagariDigitConverter.getInstance();
	private final BsCalendar bs = BsCalendar.getInstance();

	private final Context ctx;
	private final Dialog d;
	private final Listener listener;

	interface Listener {
		void onDateSet(BsDatePicker picker, int year, int month, int day);
	}

	public BsDatePicker(Context ctx, Listener listener) {
		this.ctx = ctx;
		this.listener = listener;

		d = new Dialog(ctx);

		initView();
	}

	public void show() {
		d.show();
	}

//> CUSTOM EVENT LISTENERS

//> PRIVATE HELPERS
	private void initView() {
		d.setContentView(R.layout.bs_date_picker);

		Button btnOk = (Button) d.findViewById(R.id.btnOk);
		btnOk.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) {
				listener.onDateSet(BsDatePicker.this, getYear(), getMonth(), getDay()); 
				d.dismiss();
			}
		});

		spnDay_updateFor(2074, 1); // TODO initialise to today's date

		OnItemSelectedListener changeListener = new OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				spnDay_updateFor(getYear(), getMonth());
			}
			public void onNothingSelected(AdapterView<?> parent) {}
		};

		Spinner spnMonth = spnMonth();
		adapt(spnMonth, MONTH_NAMES);

		Spinner spnYear = spnYear();
		adapt(spnYear, availableYears());

		spnMonth.setOnItemSelectedListener(changeListener);
		spnYear.setOnItemSelectedListener(changeListener);
	}

	private Spinner spnDay() { return (Spinner) d.findViewById(R.id.spnDay); }
	private Spinner spnMonth() { return (Spinner) d.findViewById(R.id.spnMonth); }
	private Spinner spnYear() { return (Spinner) d.findViewById(R.id.spnYear); }

	private int getYear() { return Integer.parseInt(spnYear().getSelectedItem().toString()); }
	private int getMonth() { return spnMonth().getSelectedItemPosition() + 1; }
	private int getDay() { return spnDay().getSelectedItemPosition() + 1; }

	private void spnDay_updateFor(int year, int month) {
		int selectedDay = getDay();
		int availableDays = bs.daysInMonth(year, month);

		Spinner spinner = spnDay();
		adapt(spinner, range(1, availableDays));

		spinner.setSelection(Math.min(selectedDay, availableDays) - 1);
	}

	private List<String> availableYears() {
		return range(2007, 2096); // TODO these values should come from the bikramsambat lib
	}

	private List<String> range(int start, int end) {
		ArrayList<String> numbers = new ArrayList<>(end - start + 1);
		for(int i=start; i<=end; ++i) {
			numbers.add(dev.toDev(i));
		}
		return numbers;
	}

	private void adapt(Spinner spinner, List<String> content) {
		ArrayAdapter<CharSequence> adapter = new ArrayAdapter(ctx, R.layout.bs_date_picker_item, content);
		adapter.setDropDownViewResource(R.layout.bs_date_picker_item);
		spinner.setAdapter(adapter);
	}
}
