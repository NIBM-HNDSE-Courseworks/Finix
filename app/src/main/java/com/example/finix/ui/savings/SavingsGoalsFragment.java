package com.example.finix.ui.savings;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.finix.R;
import com.example.finix.data.SavingsGoal;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SavingsGoalsFragment extends Fragment {

    private SavingsGoalsViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_savings_goals, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(SavingsGoalsViewModel.class);

        // Floating Action Button (top-right)
        FloatingActionButton btnAddGoal = view.findViewById(R.id.btnAddGoal);
        btnAddGoal.setOnClickListener(v -> showAddGoalDialog());
    }

    // ------------------------------------------------------------
    // Show Add Goal Popup Dialog
    // ------------------------------------------------------------
    private void showAddGoalDialog() {

        // Inflate the dialog layout
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_goal, null);

        // Create dialog
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        // Initialize input fields
        AutoCompleteTextView etCategory = dialogView.findViewById(R.id.etCategory);
        EditText etGoalName = dialogView.findViewById(R.id.etGoalName);
        EditText etGoalDescription = dialogView.findViewById(R.id.etGoalDescription);
        EditText etTargetAmount = dialogView.findViewById(R.id.etTargetAmount);
        EditText etTargetDate = dialogView.findViewById(R.id.etTargetDate);
        Button btnSave = dialogView.findViewById(R.id.btnSaveGoal);

        // --------------------------------------------------------
        // Setup Category Dropdown
        // (Later, this can come from your Room Category table)
        // --------------------------------------------------------
        List<String> categoryNames = Arrays.asList(
                "Housing",
                "Education",
                "Savings",
                "Vacation",
                "Emergency Fund"
        );

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                categoryNames
        );
        etCategory.setAdapter(adapter);

        // --------------------------------------------------------
        // Date Picker
        // --------------------------------------------------------
        etTargetDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            DatePickerDialog dp = new DatePickerDialog(requireContext(),
                    (view, year, month, day) -> {
                        String date = day + "/" + (month + 1) + "/" + year;
                        etTargetDate.setText(date);
                    },
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH));
            dp.show();
        });

        // --------------------------------------------------------
        // Save Button Logic
        // --------------------------------------------------------
        btnSave.setOnClickListener(v -> {

            String categoryName = etCategory.getText().toString().trim();
            String goalName = etGoalName.getText().toString().trim();
            String desc = etGoalDescription.getText().toString().trim();
            String targetAmountStr = etTargetAmount.getText().toString().trim();
            String targetDateStr = etTargetDate.getText().toString().trim();

            // Validation
            if (categoryName.isEmpty() || goalName.isEmpty() || targetAmountStr.isEmpty() || targetDateStr.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all required fields", Toast.LENGTH_SHORT).show();
                return;
            }

            double targetAmount;
            try {
                targetAmount = Double.parseDouble(targetAmountStr);
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "Invalid target amount", Toast.LENGTH_SHORT).show();
                return;
            }

            // Convert date string ("dd/MM/yyyy") → timestamp
            long targetDateMillis = 0;
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                Date parsedDate = sdf.parse(targetDateStr);
                if (parsedDate != null) {
                    targetDateMillis = parsedDate.getTime();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // --------------------------------------------------------
            // Temporary: assign categoryId by index
            // Later, replace with categoryDao lookup
            // --------------------------------------------------------
            int categoryId = categoryNames.indexOf(categoryName) + 1; // simple fake ID

            // Create and insert new goal
            SavingsGoal goal = new SavingsGoal(categoryId, goalName, desc, targetAmount, targetDateMillis);
            viewModel.insert(goal);

            Toast.makeText(requireContext(), "Goal saved successfully!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        // Show dialog full width
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }
}
