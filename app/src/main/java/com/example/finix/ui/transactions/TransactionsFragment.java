package com.example.finix.ui.transactions;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.finix.R;
import com.example.finix.databinding.FragmentTransactionsBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TransactionsFragment extends Fragment {

    private FragmentTransactionsBinding binding;
    private TransactionsViewModel transactionsViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        transactionsViewModel =
                new ViewModelProvider(this).get(TransactionsViewModel.class);

        binding = FragmentTransactionsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // --- Setup Add Button Click ---
        ImageButton addButton = root.findViewById(R.id.buttonAddTransaction);
        addButton.setOnClickListener(v -> showAddTransactionDialog());

        return root;
    }

    private void showAddTransactionDialog() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View popupView = inflater.inflate(R.layout.add_edit_transaction_popup, null);

        // --- Popup views ---
        EditText etAmount = popupView.findViewById(R.id.etAmount);
        EditText etDescription = popupView.findViewById(R.id.etDescription);
        Button btnSave = popupView.findViewById(R.id.btnSaveTransaction);
        ImageButton btnPickDateTime = popupView.findViewById(R.id.btnPickDateTime);
        TextView tvDateTime = popupView.findViewById(R.id.tvDateTime);

        AutoCompleteTextView actCategory = popupView.findViewById(R.id.actCategory);
        LinearLayout llAddCategory = popupView.findViewById(R.id.llAddNewCategory);
        EditText etNewCategory = popupView.findViewById(R.id.etNewCategory);
        Button btnSaveCategory = popupView.findViewById(R.id.btnSaveCategory);
        Button btnBackCategory = popupView.findViewById(R.id.btnCancelCategory);

        // --- Category adapter ---
        List<String> categoriesList = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_dropdown_item_1line, categoriesList);
        actCategory.setAdapter(adapter);
        actCategory.setThreshold(0); // show even without typing

        // --- Observe categories LiveData ---
        transactionsViewModel.getCategoriesLive().observe(getViewLifecycleOwner(), list -> {
            categoriesList.clear();
            categoriesList.addAll(list);

            // Always add "Add New Category" at the end
            if (!categoriesList.contains("Add New Category")) {
                categoriesList.add("Add New Category");
            }

            adapter.notifyDataSetChanged();
        });

        // --- Show dropdown with all categories when first clicked ---
        actCategory.setOnClickListener(v -> {
            if (!actCategory.isPopupShowing()) {
                actCategory.showDropDown();
            }
        });

        // --- Handle "Add New Category" selection ---
        actCategory.setOnItemClickListener((parent, view, position, id) -> {
            String selected = adapter.getItem(position);
            if ("Add New Category".equals(selected)) {
                llAddCategory.setVisibility(View.VISIBLE);
                actCategory.setVisibility(View.GONE);
            }
        });

        // --- Save new category ---
        btnSaveCategory.setOnClickListener(v -> {
            String newCat = etNewCategory.getText().toString().trim();
            if (!newCat.isEmpty()) {
                transactionsViewModel.addCategory(newCat); // save to DB
                actCategory.setText(newCat);
                llAddCategory.setVisibility(View.GONE);
                actCategory.setVisibility(View.VISIBLE);
            }
        });

        // --- Back button ---
        btnBackCategory.setOnClickListener(v -> {
            llAddCategory.setVisibility(View.GONE);
            actCategory.setVisibility(View.VISIBLE);
        });

        //Date and time picker
        btnPickDateTime.setOnClickListener(v -> {
            // Open a date picker first
            final Calendar calendar = Calendar.getInstance();

            DatePickerDialog datePicker = new DatePickerDialog(
                    getContext(),
                    (view, year, month, dayOfMonth) -> {
                        calendar.set(Calendar.YEAR, year);
                        calendar.set(Calendar.MONTH, month);
                        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                        // After picking date, open time picker
                        TimePickerDialog timePicker = new TimePickerDialog(
                                getContext(),
                                (timeView, hourOfDay, minute) -> {
                                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                    calendar.set(Calendar.MINUTE, minute);

                                    // Format and show date/time
                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                                    String formatted = sdf.format(calendar.getTime());
                                    tvDateTime.setText(formatted);
                                },
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                true
                        );
                        timePicker.show();
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );

            datePicker.show();
        });


        // --- Build AlertDialog ---
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(popupView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.9),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        // --- Save transaction ---
        btnSave.setOnClickListener(v -> {
            String amountText = etAmount.getText().toString().trim();
            String description = etDescription.getText().toString().trim();
            String category = actCategory.getText().toString().trim();
            String dateTimeText = tvDateTime.getText().toString().trim();

            if (amountText.isEmpty() || category.isEmpty() || dateTimeText.isEmpty()) {
                etAmount.setError("Please fill all fields");
                return;
            }

            double amount = Double.parseDouble(amountText);
            long dateTimeMillis;
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                dateTimeMillis = sdf.parse(dateTimeText).getTime();
            } catch (Exception e) {
                dateTimeMillis = System.currentTimeMillis();
            }

            transactionsViewModel.saveTransaction(amount, "Expense", category, dateTimeMillis, description);
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
