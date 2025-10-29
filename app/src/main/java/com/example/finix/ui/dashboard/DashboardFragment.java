package com.example.finix.ui.dashboard;

import android.app.Application;
import android.graphics.Color;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import com.example.finix.R;
import com.example.finix.data.CategoryDAO;
import com.example.finix.data.FinixDatabase;
import com.example.finix.data.Transaction;
import com.example.finix.databinding.FragmentDashboardBinding;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private DashboardViewModel viewModel;

    private List<String> distinctMonths = new ArrayList<>();
    private String currentMonthYearFilter = null;
    private Map<Integer, String> categoryMap = new HashMap<>(); // Local copy of category map

    // Store the current month's total for easy reset on chart de-selection
    private double currentIncomeTotal = 0.0;
    private double currentExpenseTotal = 0.0;

    public DashboardFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Retrieve the dependencies
        Application application = requireActivity().getApplication();
        // Assuming FinixDatabase is a Singleton or static accessor:
        CategoryDAO categoryDao = FinixDatabase.getDatabase(application).categoryDao();

        // 2. Instantiate the Custom Factory
        DashboardViewModelFactory factory = new DashboardViewModelFactory(application, categoryDao);

        // 3. Get the ViewModel using the Custom Factory
        viewModel = new ViewModelProvider(this, factory).get(DashboardViewModel.class);

        setupMonthYearSpinnerListener();

        // Setup Chart views initially and set the listener
        setupChart(binding.incomeChart);
        setupChart(binding.expenseChart);

        // --- 1. Observe Distinct Months ---
        viewModel.getDistinctMonthsLive().observe(getViewLifecycleOwner(), this::updateMonthYearSpinner);

        // --- 2. Observe Category Map (Must be loaded first for chart labels) ---
        viewModel.getCategoryMapLive().observe(getViewLifecycleOwner(), map -> {
            categoryMap = map;
            // When map updates, force a re-draw of the charts if data is already present
            if (viewModel.monthlyIncomeTransactionsLive.getValue() != null) {
                updatePieChart(binding.incomeChart, viewModel.monthlyIncomeTransactionsLive.getValue(), categoryMap, "Income");
            }
            if (viewModel.monthlyExpenseTransactionsLive.getValue() != null) {
                updatePieChart(binding.expenseChart, viewModel.monthlyExpenseTransactionsLive.getValue(), categoryMap, "Expense");
            }
        });

        // --- 3. Observe LiveData for UI Updates (Cards) ---
        viewModel.incomeTotalLive.observe(getViewLifecycleOwner(), total -> {
            currentIncomeTotal = total != null ? total : 0.0; // Store total
            binding.incomeAmount.setText(String.format(Locale.getDefault(), "Rs. %.2f", currentIncomeTotal));

            // Safely check if the chart is highlighted before accessing array length
            if (binding.incomeChart.getData() != null) {
                Highlight[] highlights = binding.incomeChart.getHighlighted();
                if (highlights == null || highlights.length == 0) {
                    updateChartCenterTextToTotal(binding.incomeChart, currentIncomeTotal, "Income");
                }
            }
        });

        viewModel.incomeComparisonLive.observe(getViewLifecycleOwner(), comparison -> {
            if (comparison != null) {
                String[] parts = comparison.split("\\|", 2);
                binding.incomeChange.setText(parts.length > 1 ? parts[1] : parts[0]);
                try {
                    binding.incomeChange.setTextColor(Color.parseColor(parts[0]));
                } catch (IllegalArgumentException e) { /* Fallback */ }
            } else { binding.incomeChange.setText(""); }
        });

        viewModel.expenseTotalLive.observe(getViewLifecycleOwner(), total -> {
            currentExpenseTotal = total != null ? total : 0.0; // Store total
            binding.expenseAmount.setText(String.format(Locale.getDefault(), "Rs. %.2f", currentExpenseTotal));

            // Safely check if the chart is highlighted before accessing array length
            if (binding.expenseChart.getData() != null) {
                Highlight[] highlights = binding.expenseChart.getHighlighted();
                if (highlights == null || highlights.length == 0) {
                    updateChartCenterTextToTotal(binding.expenseChart, currentExpenseTotal, "Expense");
                }
            }
        });

        viewModel.expenseComparisonLive.observe(getViewLifecycleOwner(), comparison -> {
            if (comparison != null) {
                String[] parts = comparison.split("\\|", 2);
                binding.expenseChange.setText(parts.length > 1 ? parts[1] : parts[0]);
                try {
                    binding.expenseChange.setTextColor(Color.parseColor(parts[0]));
                } catch (IllegalArgumentException e) { /* Fallback */ }
            } else { binding.expenseChange.setText(""); }
        });


        // --- 4. Observe Chart Data (Draw the graphs) ---
        viewModel.monthlyIncomeTransactionsLive.observe(getViewLifecycleOwner(), incomeList -> {
            updatePieChart(binding.incomeChart, incomeList, categoryMap, "Income");

            // ⭐ FIX: Stop refresh indicator here. This acts as a safety measure
            // in case the expense list observer doesn't fire or takes too long.
            if (binding.swipeRefreshLayout.isRefreshing()) {
                binding.swipeRefreshLayout.setRefreshing(false);
            }
        });

        viewModel.monthlyExpenseTransactionsLive.observe(getViewLifecycleOwner(), expenseList -> {
            updatePieChart(binding.expenseChart, expenseList, categoryMap, "Expense");

            // ⭐ Keep: Stop refresh indicator here too, as it was originally intended to be the last step.
            if (binding.swipeRefreshLayout.isRefreshing()) {
                binding.swipeRefreshLayout.setRefreshing(false);
            }
        });

        // ⭐ Setup Swipe to Refresh Listener
        binding.swipeRefreshLayout.setOnRefreshListener(this::refreshData);
    }

    // ⭐ Method to handle the refresh logic
    private void refreshData() {
        if (currentMonthYearFilter != null) {
            // Re-select the current month to force the ViewModel's LiveData to reload/re-evaluate
            viewModel.setSelectedMonth(currentMonthYearFilter);
            // The indicator is dismissed by the LiveData observers once data arrives.
        } else {
            // If no filter is set (e.g., initial load is still pending), dismiss immediately
            binding.swipeRefreshLayout.setRefreshing(false);
        }
    }

    // --- Chart Drawing Methods ---

    /**
     * Helper method to set the center text to the total amount.
     */
    private void updateChartCenterTextToTotal(PieChart chart, double totalAmount, String type) {
        String centerAmount = String.format(Locale.getDefault(), "Rs. %.0f", totalAmount);
        chart.setCenterText(centerAmount + "\n" + type);
        chart.setCenterTextColor(Color.WHITE);
        chart.highlightValue(null); // Clear any highlight
    }

    /**
     * Initial setup for the PieChart style.
     */
    private void setupChart(PieChart chart) {
        chart.setUsePercentValues(true);
        chart.getDescription().setEnabled(false);
        chart.setExtraOffsets(5, 5, 5, 5);

        chart.setDrawHoleEnabled(true);
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setHoleRadius(50f);
        chart.setTransparentCircleRadius(55f);
        chart.setDrawCenterText(true);
        chart.setRotationEnabled(false);
        chart.setHighlightPerTapEnabled(true);
        chart.setCenterTextSize(10f);

        // Set the center text color to White
        chart.setCenterTextColor(Color.WHITE);

        Legend l = chart.getLegend();
        l.setEnabled(false); // Hide legend since slices are small, full legend is not needed

        // FIX: Set an anonymous listener directly on the chart instance.
        chart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                if (e instanceof PieEntry) {
                    PieEntry entry = (PieEntry) e;
                    String categoryName = entry.getLabel();
                    double categoryTotal = entry.getValue();

                    // Update center text to show Category Total
                    String formattedAmount = String.format(Locale.getDefault(), "Rs. %.2f", categoryTotal);

                    // 'chart' is accessible here
                    chart.setCenterText(formattedAmount + "\n" + categoryName);
                    chart.setCenterTextColor(Color.WHITE);
                }
            }

            @Override
            public void onNothingSelected() {
                // Determine which stored total to use for the reset
                if (chart == binding.incomeChart) {
                    updateChartCenterTextToTotal(chart, currentIncomeTotal, "Income");
                } else if (chart == binding.expenseChart) {
                    updateChartCenterTextToTotal(chart, currentExpenseTotal, "Expense");
                }
            }
        });
    }

    /**
     * Processes transactions, sets up data entries, and draws the PieChart.
     */
    private void updatePieChart(PieChart chart, List<Transaction> transactions, Map<Integer, String> categoryMap, String type) {
        // Use Type: Income / Expense for center text

        if (transactions == null || transactions.isEmpty() || categoryMap.isEmpty()) {
            chart.clear();
            chart.setCenterText("No " + type + " Data");
            chart.invalidate();
            return;
        }

        // 1. Aggregate amounts by category ID (Requires min API 24 for Collectors.summingDouble)
        Map<Integer, Double> aggregatedData = transactions.stream()
                .collect(Collectors.groupingBy(
                        Transaction::getCategoryId,
                        Collectors.summingDouble(Transaction::getAmount)
                ));

        // 2. Create Pie Entries
        ArrayList<PieEntry> entries = new ArrayList<>();
        double totalAmount = 0;

        for (Map.Entry<Integer, Double> entry : aggregatedData.entrySet()) {
            totalAmount += entry.getValue();
            String categoryName = categoryMap.getOrDefault(entry.getKey(), "Unknown");
            // PieEntry(value, label) - value is the amount, label is the category name
            entries.add(new PieEntry(entry.getValue().floatValue(), categoryName));
        }

        // 3. Create PieDataSet
        PieDataSet dataSet = new PieDataSet(entries, "");

        // Updated darker, ash-toned palette
        final int[] CHART_COLORS = {
                Color.parseColor("#00695C"), // Dark Teal (Rich and deep)
                Color.parseColor("#455A64"), // Dark Blue Grey (Strong, neutral contrast)
                Color.parseColor("#00897B"), // Medium Teal (Primary application color)
                Color.parseColor("#78909C")  // Medium Blue Grey (Softer contrast)
        };

        ArrayList<Integer> colors = new ArrayList<>();
        for (int color : CHART_COLORS) {
            colors.add(color);
        }
        dataSet.setColors(colors);
        dataSet.setSliceSpace(2f);
        dataSet.setSelectionShift(5f);
        dataSet.setDrawValues(false); // Don't show values on the small slices

        // 4. Create PieData and set on Chart
        PieData data = new PieData(dataSet);
        data.setValueTextSize(11f);

        chart.setData(data);

        // Set center text using the helper function for consistency
        updateChartCenterTextToTotal(chart, totalAmount, type);

        chart.animateY(1000);
        chart.invalidate(); // Refresh chart
    }

    // --- Spinner Logic ---
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
            public void onNothingSelected(AdapterView<?> parent) { /* Do nothing */ }
        });
    }

    private void updateMonthYearSpinner(List<String> months) {
        distinctMonths.clear();
        if (months != null) distinctMonths.addAll(months);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, distinctMonths);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerMonthYear.setAdapter(adapter);

        if (!distinctMonths.isEmpty() && currentMonthYearFilter == null) {
            final int LATEST_MONTH_INDEX = 0;
            String defaultSelection = distinctMonths.get(LATEST_MONTH_INDEX);
            binding.spinnerMonthYear.setSelection(LATEST_MONTH_INDEX);
            viewModel.setSelectedMonth(defaultSelection);
            currentMonthYearFilter = defaultSelection;
        } else if (currentMonthYearFilter != null) {
            int index = distinctMonths.indexOf(currentMonthYearFilter);
            if (index != -1) binding.spinnerMonthYear.setSelection(index);
        }

        binding.spinnerMonthYear.setVisibility(distinctMonths.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
