package com.example.finix.ui.savings;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finix.R;
import com.example.finix.data.SavingsGoal;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SavingsGoalsFragment extends Fragment {

    private SavingsGoalsViewModel viewModel;
    private SavingsGoalsAdapter adapter;

    // categoryId -> name
    private final Map<Integer, String> categoryMap = new HashMap<>();

    // ------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_savings_goals, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(SavingsGoalsViewModel.class);

        // RecyclerView
        RecyclerView rv = view.findViewById(R.id.recyclerGoals);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SavingsGoalsAdapter(
                catId -> categoryMap.getOrDefault(catId, "Unknown"),
                new SavingsGoalsAdapter.OnGoalActionListener() {
                    @Override public void onEdit(SavingsGoal goal) { showEditGoalDialog(goal); }
                    @Override public void onDelete(SavingsGoal goal) { showConfirmDelete(goal); }
                }
        );
        rv.setAdapter(adapter);

        // Observe data
        viewModel.getAllGoals().observe(getViewLifecycleOwner(), adapter::submitList);
        viewModel.getCategoryMapLive().observe(getViewLifecycleOwner(), map -> {
            categoryMap.clear();
            if (map != null) categoryMap.putAll(map);
            adapter.notifyDataSetChanged();
        });

        // FAB
        ImageButton btnAdd = view.findViewById(R.id.btnAddGoal);
        btnAdd.setOnClickListener(v -> showAddGoalDialog());
    }

    // ------------------------------------------------------------
    // ADD GOAL
    // ------------------------------------------------------------
    private void showAddGoalDialog() {
        View popup = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_goal, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(popup).create();

        // Category controls
        AutoCompleteTextView actCategory = popup.findViewById(R.id.etCategory);
        LinearLayout llAddCategory = popup.findViewById(R.id.llAddNewCategory);
        EditText etNewCategory = popup.findViewById(R.id.etNewCategory);
        Button btnSaveCategory = popup.findViewById(R.id.btnSaveCategory);
        Button btnBackCategory = popup.findViewById(R.id.btnCancelCategory);

        // Goal fields
        EditText etGoalName = popup.findViewById(R.id.etGoalName);
        EditText etGoalDescription = popup.findViewById(R.id.etGoalDescription);
        EditText etTargetAmount = popup.findViewById(R.id.etTargetAmount);
        EditText etTargetDate = popup.findViewById(R.id.etTargetDate);
        ImageButton btnPickDate = popup.findViewById(R.id.btnPickDate);
        Button btnSave = popup.findViewById(R.id.btnSaveGoal);

        etNewCategory.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});

        // Category dropdown setup
        List<String> categoriesList = new ArrayList<>();
        Map<String, Integer> nameToId = new HashMap<>();
        categoriesList.add("Add New Category");
        Map<Integer, String> current = viewModel.getCategoryMapLive().getValue();
        if (current != null) {
            for (Map.Entry<Integer, String> e : current.entrySet()) {
                categoriesList.add(e.getValue());
                nameToId.put(e.getValue(), e.getKey());
            }
        }
        ArrayAdapter<String> ddAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, categoriesList);
        actCategory.setAdapter(ddAdapter);
        actCategory.setThreshold(0);
        actCategory.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) { refreshCategories(ddAdapter, categoriesList, nameToId, actCategory); actCategory.showDropDown(); }
        });
        actCategory.setOnClickListener(v -> { refreshCategories(ddAdapter, categoriesList, nameToId, actCategory); actCategory.showDropDown(); });
        actCategory.setOnItemClickListener((parent, v, pos, id) -> {
            String selected = ddAdapter.getItem(pos);
            if ("Add New Category".equals(selected)) {
                llAddCategory.setVisibility(View.VISIBLE);
                actCategory.setVisibility(View.GONE);
            }
        });

        btnSaveCategory.setOnClickListener(v -> {
            String newCat = etNewCategory.getText().toString().trim();
            if (newCat.isEmpty()) {
                Toast.makeText(requireContext(), "Category cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.addCategory(newCat);
            Toast.makeText(requireContext(), "New category added!", Toast.LENGTH_SHORT).show();
            onCategoryAdded(newCat, ddAdapter, categoriesList, nameToId, actCategory, llAddCategory, etNewCategory);
        });
        btnBackCategory.setOnClickListener(v -> {
            llAddCategory.setVisibility(View.GONE);
            actCategory.setVisibility(View.VISIBLE);
        });

        // Date picker
        View.OnClickListener pickDate = v -> showDatePicker(date -> etTargetDate.setText(date));
        etTargetDate.setOnClickListener(pickDate);
        btnPickDate.setOnClickListener(pickDate);

        // Save goal
        btnSave.setOnClickListener(v -> {
            String catName = actCategory.getText().toString().trim();
            String goalName = etGoalName.getText().toString().trim();
            String desc = etGoalDescription.getText().toString().trim();
            String amountStr = etTargetAmount.getText().toString().trim();
            String dateStr = etTargetDate.getText().toString().trim();

            if (catName.isEmpty() || goalName.isEmpty() || amountStr.isEmpty() || dateStr.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!nameToId.containsKey(catName)) {
                Toast.makeText(requireContext(), "Invalid category!", Toast.LENGTH_SHORT).show();
                return;
            }

            int categoryId = nameToId.get(catName);
            double targetAmount;
            try { targetAmount = Double.parseDouble(amountStr); }
            catch (NumberFormatException e) { Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show(); return; }

            long targetDateMillis = parseDateToMillis(dateStr);
            if (targetDateMillis == -1) { Toast.makeText(requireContext(), "Invalid date", Toast.LENGTH_SHORT).show(); return; }

            SavingsGoal goal = new SavingsGoal(categoryId, goalName, desc, targetAmount, targetDateMillis);
            viewModel.insert(goal);
            Toast.makeText(requireContext(), "Goal saved!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    // ------------------------------------------------------------
    // EDIT GOAL
    // ------------------------------------------------------------
    private void showEditGoalDialog(@NonNull SavingsGoal existing) {
        View popup = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_goal, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(popup).create();

        AutoCompleteTextView actCategory = popup.findViewById(R.id.etCategory);
        LinearLayout llAddCategory = popup.findViewById(R.id.llAddNewCategory);
        EditText etNewCategory = popup.findViewById(R.id.etNewCategory);
        Button btnSaveCategory = popup.findViewById(R.id.btnSaveCategory);
        Button btnBackCategory = popup.findViewById(R.id.btnCancelCategory);

        EditText etGoalName = popup.findViewById(R.id.etGoalName);
        EditText etGoalDescription = popup.findViewById(R.id.etGoalDescription);
        EditText etTargetAmount = popup.findViewById(R.id.etTargetAmount);
        EditText etTargetDate = popup.findViewById(R.id.etTargetDate);
        ImageButton btnPickDate = popup.findViewById(R.id.btnPickDate);
        Button btnSave = popup.findViewById(R.id.btnSaveGoal);

        // Prefill
        etGoalName.setText(existing.getGoalName());
        etGoalDescription.setText(existing.getGoalDescription());
        etTargetAmount.setText(String.valueOf(existing.getTargetAmount()));
        etTargetDate.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(existing.getTargetDate())));

        // Category dropdown
        List<String> categoriesList = new ArrayList<>();
        Map<String, Integer> nameToId = new HashMap<>();
        categoriesList.add("Add New Category");
        Map<Integer, String> current = viewModel.getCategoryMapLive().getValue();
        if (current != null) {
            for (Map.Entry<Integer, String> e : current.entrySet()) {
                categoriesList.add(e.getValue());
                nameToId.put(e.getValue(), e.getKey());
            }
        }
        ArrayAdapter<String> ddAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, categoriesList);
        actCategory.setAdapter(ddAdapter);
        actCategory.setThreshold(0);

        // set current category name
        actCategory.setText(categoryMap.getOrDefault(existing.getCategoryId(), ""), false);

        actCategory.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) { refreshCategories(ddAdapter, categoriesList, nameToId, actCategory); actCategory.showDropDown(); }
        });
        actCategory.setOnClickListener(v -> { refreshCategories(ddAdapter, categoriesList, nameToId, actCategory); actCategory.showDropDown(); });
        actCategory.setOnItemClickListener((parent, v, pos, id) -> {
            String selected = ddAdapter.getItem(pos);
            if ("Add New Category".equals(selected)) {
                llAddCategory.setVisibility(View.VISIBLE);
                actCategory.setVisibility(View.GONE);
            }
        });

        btnSaveCategory.setOnClickListener(v -> {
            String newCat = etNewCategory.getText().toString().trim();
            if (newCat.isEmpty()) { Toast.makeText(requireContext(), "Category cannot be empty", Toast.LENGTH_SHORT).show(); return; }
            viewModel.addCategory(newCat);
            Toast.makeText(requireContext(), "New category added!", Toast.LENGTH_SHORT).show();
            onCategoryAdded(newCat, ddAdapter, categoriesList, nameToId, actCategory, llAddCategory, etNewCategory);
        });
        btnBackCategory.setOnClickListener(v -> {
            llAddCategory.setVisibility(View.GONE);
            actCategory.setVisibility(View.VISIBLE);
        });

        // Date picker
        View.OnClickListener pickDate = v -> showDatePicker(date -> etTargetDate.setText(date));
        etTargetDate.setOnClickListener(pickDate);
        btnPickDate.setOnClickListener(pickDate);

        btnSave.setText("UPDATE GOAL");
        btnSave.setOnClickListener(v -> {
            String catName = actCategory.getText().toString().trim();
            String goalName = etGoalName.getText().toString().trim();
            String desc = etGoalDescription.getText().toString().trim();
            String amountStr = etTargetAmount.getText().toString().trim();
            String dateStr = etTargetDate.getText().toString().trim();

            if (catName.isEmpty() || goalName.isEmpty() || amountStr.isEmpty() || dateStr.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!nameToId.containsKey(catName)) {
                Toast.makeText(requireContext(), "Invalid category!", Toast.LENGTH_SHORT).show();
                return;
            }

            int categoryId = nameToId.get(catName);
            double targetAmount;
            try { targetAmount = Double.parseDouble(amountStr); }
            catch (NumberFormatException e) { Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show(); return; }

            long targetDateMillis = parseDateToMillis(dateStr);
            if (targetDateMillis == -1) { Toast.makeText(requireContext(), "Invalid date", Toast.LENGTH_SHORT).show(); return; }

            // Build updated goal with same primary key
            SavingsGoal updated = new SavingsGoal(categoryId, goalName, desc, targetAmount, targetDateMillis);
            updated.setId(existing.getId());

            viewModel.update(updated);
            Toast.makeText(requireContext(), "Goal updated!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    // ------------------------------------------------------------
    // CONFIRM DELETE
    // ------------------------------------------------------------
    private void showConfirmDelete(SavingsGoal goal) {
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.delete_confirmation_popup, null, false);

        TextView tvMessage = v.findViewById(R.id.deleteMessage);
        Button btnCancel = v.findViewById(R.id.cancelDeleteBtn);
        Button btnDelete = v.findViewById(R.id.confirmDeleteBtn);

        String amount = String.format(Locale.getDefault(), "Rs. %,.2f", goal.getTargetAmount());
        String goalName = goal.getGoalName() == null ? "" : goal.getGoalName();

        tvMessage.setText("Are you sure you want to delete the goal of " + amount + " for '" + goalName + "'?");

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(v)
                .setCancelable(false)
                .create();

        btnCancel.setOnClickListener(x -> dialog.dismiss());
        btnDelete.setOnClickListener(x -> {
            viewModel.delete(goal);
            Toast.makeText(requireContext(), "Goal deleted", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }


    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------
    private interface OnDatePicked { void onPicked(String ddMMyyyy); }

    private void showDatePicker(OnDatePicked cb) {
        final Calendar calendar = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        DatePickerDialog dp = new DatePickerDialog(
                requireContext(),
                (view, y, m, d) -> {
                    calendar.set(y, m, d);
                    // 🕒 Add TimePicker after selecting date
                    android.app.TimePickerDialog tp = new android.app.TimePickerDialog(
                            requireContext(),
                            (timeView, h, min) -> {
                                calendar.set(Calendar.HOUR_OF_DAY, h);
                                calendar.set(Calendar.MINUTE, min);
                                String selectedDateTime = format.format(calendar.getTime());
                                cb.onPicked(selectedDateTime);
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true
                    );
                    tp.show();
                    tp.getButton(DialogInterface.BUTTON_POSITIVE)
                            .setTextColor(Color.parseColor("#00BFA5")); // teal OK
                    tp.getButton(DialogInterface.BUTTON_NEGATIVE)
                            .setTextColor(Color.parseColor("#FF5252")); // red CANCEL
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        dp.show();
        dp.getButton(DialogInterface.BUTTON_POSITIVE)
                .setTextColor(Color.parseColor("#00BFA5")); // teal OK
        dp.getButton(DialogInterface.BUTTON_NEGATIVE)
                .setTextColor(Color.parseColor("#FF5252")); // red CANCEL
    }



    private long parseDateToMillis(String ddMMyyyy) {
        try {
            Date parsed = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(ddMMyyyy);
            return (parsed != null) ? parsed.getTime() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private void onCategoryAdded(String newCat,
                                 ArrayAdapter<String> adapter,
                                 List<String> categoriesList,
                                 Map<String, Integer> nameToId,
                                 AutoCompleteTextView actCategory,
                                 LinearLayout llAddCategory,
                                 EditText etNewCategory) {

        Observer<Map<Integer, String>> oneTime = new Observer<Map<Integer, String>>() {
            @Override
            public void onChanged(Map<Integer, String> map) {
                if (map != null && map.containsValue(newCat)) {
                    categoriesList.clear();
                    nameToId.clear();
                    categoriesList.add("Add New Category");
                    for (Map.Entry<Integer, String> e : map.entrySet()) {
                        categoriesList.add(e.getValue());
                        nameToId.put(e.getValue(), e.getKey());
                    }
                    adapter.notifyDataSetChanged();

                    actCategory.post(() -> {
                        actCategory.setText(newCat, false);
                        actCategory.setVisibility(View.VISIBLE);
                        llAddCategory.setVisibility(View.GONE);
                        etNewCategory.setText("");
                    });

                    viewModel.getCategoryMapLive().removeObserver(this);
                }
            }
        };
        viewModel.getCategoryMapLive().observe(getViewLifecycleOwner(), oneTime);
        viewModel.fetchLatestCategoryMap();
    }

    private void refreshCategories(ArrayAdapter<String> adapter,
                                   List<String> categoriesList,
                                   Map<String, Integer> nameToId,
                                   AutoCompleteTextView actCategory) {

        viewModel.fetchLatestCategoryMap();
        Observer<Map<Integer, String>> obs = new Observer<Map<Integer, String>>() {
            @Override
            public void onChanged(Map<Integer, String> map) {
                if (map != null) {
                    categoriesList.clear();
                    nameToId.clear();
                    categoriesList.add("Add New Category");
                    for (Map.Entry<Integer, String> e : map.entrySet()) {
                        categoriesList.add(e.getValue());
                        nameToId.put(e.getValue(), e.getKey());
                    }
                    adapter.notifyDataSetChanged();
                    actCategory.post(actCategory::showDropDown);
                }
                viewModel.getCategoryMapLive().removeObserver(this);
            }
        };
        viewModel.getCategoryMapLive().observe(getViewLifecycleOwner(), obs);
    }
}