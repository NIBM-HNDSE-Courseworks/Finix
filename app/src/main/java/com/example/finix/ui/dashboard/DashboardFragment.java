package com.example.finix.ui.dashboard;

import android.graphics.Color;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import com.example.finix.R;
import com.example.finix.data.Transaction;
// Assuming a binding class based on fragment_dashboard.xml
import com.example.finix.databinding.FragmentDashboardBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private DashboardViewModel viewModel;
    private List<String> distinctMonths = new ArrayList<>();
    private String currentMonthYearFilter = null; // To prevent initial redundant firing

    public DashboardFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment using ViewBinding
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        // --- 1. Setup Spinner Listener ---
        setupMonthYearSpinnerListener();

        // --- 2. Observe Distinct Months (Spinner population and default selection) ---
        viewModel.getDistinctMonthsLive().observe(getViewLifecycleOwner(), months -> {
            updateMonthYearSpinner(months);
        });

        // --- 3. Observe LiveData for UI Updates ---

        // Observe Income Total
        viewModel.incomeTotalLive.observe(getViewLifecycleOwner(), total -> {
            String amountText = String.format(Locale.getDefault(), "$%.2f", total != null ? total : 0.0);
            // ✅ CORRECTED: Using binding.incomeAmount
            binding.incomeAmount.setText(amountText);
        });

        // Observe Income Comparison Text
        viewModel.incomeComparisonLive.observe(getViewLifecycleOwner(), comparison -> {
            if (comparison != null) {
                // Format: "COLOR_HEX|Comparison Text"
                String[] parts = comparison.split("\\|", 2);
                String colorHex = parts[0];
                String text = parts.length > 1 ? parts[1] : parts[0];

                // ✅ CORRECTED: Using binding.incomeChange
                binding.incomeChange.setText(text);
                try {
                    binding.incomeChange.setTextColor(Color.parseColor(colorHex));
                } catch (IllegalArgumentException e) {
                    // Fallback color if parsing fails
                    binding.incomeChange.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                }
            } else {
                binding.incomeChange.setText("");
            }
        });

        // Observe Expense Total
        viewModel.expenseTotalLive.observe(getViewLifecycleOwner(), total -> {
            String amountText = String.format(Locale.getDefault(), "$%.2f", total != null ? total : 0.0);
            // ✅ CORRECTED: Using binding.expenseAmount
            binding.expenseAmount.setText(amountText);
        });

        // Observe Expense Comparison Text
        viewModel.expenseComparisonLive.observe(getViewLifecycleOwner(), comparison -> {
            if (comparison != null) {
                // Format: "COLOR_HEX|Comparison Text"
                String[] parts = comparison.split("\\|", 2);
                String colorHex = parts[0];
                String text = parts.length > 1 ? parts[1] : parts[0];

                // ✅ CORRECTED: Using binding.expenseChange
                binding.expenseChange.setText(text);
                try {
                    binding.expenseChange.setTextColor(Color.parseColor(colorHex));
                } catch (IllegalArgumentException e) {
                    // Fallback color if parsing fails
                    binding.expenseChange.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                }
            } else {
                binding.expenseChange.setText("");
            }
        });

        // --- 4. Observe Chart Data (Placeholder for Chart Drawing) ---
        viewModel.monthlyIncomeTransactionsLive.observe(getViewLifecycleOwner(), incomeList -> {
            // TODO: Implement chart drawing logic here.
            // You will use binding.incomeChartPlaceholder to reference the chart view
            Log.d("Dashboard", "New Income data for chart: " + incomeList.size() + " transactions.");
        });

        viewModel.monthlyExpenseTransactionsLive.observe(getViewLifecycleOwner(), expenseList -> {
            // TODO: Implement chart drawing logic here.
            // You will use binding.expenseChartPlaceholder to reference the chart view
            Log.d("Dashboard", "New Expense data for chart: " + expenseList.size() + " transactions.");
        });
    }

    // --- Spinner Logic (Adapted from TransactionsFragment) ---

    private void setupMonthYearSpinnerListener() {
        binding.spinnerMonthYear.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedMonthYear = distinctMonths.get(position);
                if (!selectedMonthYear.equals(currentMonthYearFilter)) {
                    viewModel.setSelectedMonth(selectedMonthYear);
                    currentMonthYearFilter = selectedMonthYear;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }

    private void updateMonthYearSpinner(List<String> months) {
        distinctMonths.clear();
        if (months != null && !months.isEmpty()) {
            // ASSUMPTION: 'months' list is sorted in descending chronological order (newest month first).
            distinctMonths.addAll(months);
        }

        // Create and set the adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                distinctMonths);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerMonthYear.setAdapter(adapter);

        // Set the default selection: latest month (index 0)
        if (!distinctMonths.isEmpty() && currentMonthYearFilter == null) {
            // Only set default if no month is currently selected (initial load)
            final int LATEST_MONTH_INDEX = 0;
            String defaultSelection = distinctMonths.get(LATEST_MONTH_INDEX);
            binding.spinnerMonthYear.setSelection(LATEST_MONTH_INDEX);

            // Manually set the value in ViewModel since initial setSelection may not trigger listener
            viewModel.setSelectedMonth(defaultSelection);
            currentMonthYearFilter = defaultSelection;
        } else if (currentMonthYearFilter != null) {
            // Maintain the current selection
            int index = distinctMonths.indexOf(currentMonthYearFilter);
            if (index != -1) {
                binding.spinnerMonthYear.setSelection(index);
            } else if (!distinctMonths.isEmpty()) {
                // Filter disappeared, default to the latest month
                binding.spinnerMonthYear.setSelection(0);
                viewModel.setSelectedMonth(distinctMonths.get(0));
                currentMonthYearFilter = distinctMonths.get(0);
            }
        }

        // Hide the spinner if no transactions exist
        binding.spinnerMonthYear.setVisibility(distinctMonths.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}