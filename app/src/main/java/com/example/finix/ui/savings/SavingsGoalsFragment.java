package com.example.finix.ui.savings;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.CountDownTimer; // <-- NEW IMPORT
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

    // 💡 NEW: Placeholder views for empty state
    private ImageView imageNoGoals;
    private TextView textNoGoals;
    private RecyclerView recyclerView; // Added for easy access

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

        // RecyclerView setup
        recyclerView = view.findViewById(R.id.recyclerGoals); // Assign to member variable
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SavingsGoalsAdapter(
                catId -> categoryMap.getOrDefault(catId, "Unknown"),
                new SavingsGoalsAdapter.OnGoalActionListener() {
                    @Override public void onEdit(SavingsGoal goal) { showEditGoalDialog(goal); }
                    @Override public void onDelete(SavingsGoal goal) { showConfirmDelete(goal); }

                    @Override
                    public void onGoalCompleted() {
                        // This was called from the adapter!
                        // Now we refresh the whole UI to update all progress bars.
                        refreshData();
                    }
                }
        );
        recyclerView.setAdapter(adapter);

        // 💡 NEW: Initialize placeholder views
        imageNoGoals = view.findViewById(R.id.imageNoGoals);
        textNoGoals = view.findViewById(R.id.textNoGoals);
        // ------------------------------------

        // Observe data
        viewModel.getAllGoals().observe(getViewLifecycleOwner(), goals -> {
            // 💡 UPDATED: Visibility toggle logic
            if (goals == null || goals.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                imageNoGoals.setVisibility(View.VISIBLE);
                textNoGoals.setVisibility(View.VISIBLE);
            } else {
                recyclerView.setVisibility(View.VISIBLE);
                imageNoGoals.setVisibility(View.GONE);
                textNoGoals.setVisibility(View.GONE);
                adapter.submitList(goals);
            }
        });

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

        if (dialog.getWindow() != null) {
            // CRITICAL FIX 1: Force Full Width and Full Height
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);

            // CRITICAL FIX 2: Ensure dialog resizes when keyboard appears
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

            // CRITICAL FIX 3: Remove default AlertDialog padding/insets for true full screen
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

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

        // Set character limits
        etNewCategory.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});
        // New limit for Category Name
        actCategory.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});
        // New limit for Goal Name
        etGoalName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});
        // New limit for Target Amount
        etTargetAmount.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});

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

                // ✅ Auto-focus & show keyboard
                etNewCategory.requestFocus();
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etNewCategory, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        btnSaveCategory.setOnClickListener(v -> {
            String newCat = etNewCategory.getText().toString().trim();
            if (newCat.isEmpty()) {
                // 📝 Changed to showCustomToast and removed prefix
                showCustomToast("Category cannot be empty");
                return;
            }
            viewModel.addCategoryWithSync(newCat);
            // 📝 Changed to showCustomToast and removed prefix
            showCustomToast("New category added!");
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
                // 📝 Changed to showCustomToast and removed prefix
                showCustomToast("Please fill all fields");
                return;
            }
            if (!nameToId.containsKey(catName)) {
                // 📝 Changed to showCustomToast and removed prefix
                showCustomToast("Invalid category!");
                return;
            }

            int categoryId = nameToId.get(catName);
            double targetAmount;
            try { targetAmount = Double.parseDouble(amountStr); }
            catch (NumberFormatException e) {
                // 📝 Changed to showCustomToast and removed prefix
                showCustomToast("Invalid amount");
                return;
            }

            long targetDateMillis = parseDateToMillis(dateStr);
            if (targetDateMillis == -1) {
                // 📝 Changed to showCustomToast and removed prefix
                showCustomToast("Invalid date");
                return;
            }

            SavingsGoal goal = new SavingsGoal(categoryId, goalName, desc, targetAmount, targetDateMillis);
            viewModel.insert(goal);
            // 📝 Changed to showCustomToast and removed prefix
            showCustomToast("Goal saved!");
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

        if (dialog.getWindow() != null) {
            // CRITICAL FIX 1: Force Full Width and Full Height
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);

            // CRITICAL FIX 2: Ensure dialog resizes when keyboard appears
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

            // CRITICAL FIX 3: Remove default AlertDialog padding/insets for true full screen
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Find the title TextView and set the text
        TextView tvTitle = popup.findViewById(R.id.popupTitle);
        if (tvTitle != null) {
            tvTitle.setText("Edit Goal");
        }

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

        // Set character limits
        etNewCategory.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});
        actCategory.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});
        etGoalName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});
        etTargetAmount.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});

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

                // ✅ Auto-focus & show keyboard
                etNewCategory.requestFocus();
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etNewCategory, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        btnSaveCategory.setOnClickListener(v -> {
            String newCat = etNewCategory.getText().toString().trim();
            if (newCat.isEmpty()) {
                // 📝 Changed to showCustomToast and removed prefix
                showCustomToast("Category cannot be empty");
                return;
            }
            viewModel.addCategoryWithSync(newCat);
            // 📝 Changed to showCustomToast and removed prefix
            showCustomToast("New category added!");
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
                // 📝 Changed to showCustomToast and removed prefix
                showCustomToast("Please fill all fields");
                return;
            }
            if (!nameToId.containsKey(catName)) {
                // 📝 Changed to showCustomToast and removed prefix
                showCustomToast("Invalid category!");
                return;
            }

            int categoryId = nameToId.get(catName);
            double targetAmount;
            try { targetAmount = Double.parseDouble(amountStr); }
            catch (NumberFormatException e) {
                // 📝 Changed to showCustomToast and removed prefix
                showCustomToast("Invalid amount");
                return;
            }

            long targetDateMillis = parseDateToMillis(dateStr);
            if (targetDateMillis == -1) {
                // 📝 Changed to showCustomToast and removed prefix
                showCustomToast("Invalid date");
                return;
            }

            // ✅ Added validation for no changes
            boolean noChange =
                    existing.getCategoryId() == categoryId &&
                            existing.getGoalName().equals(goalName) &&
                            ((existing.getGoalDescription() == null && desc.isEmpty()) ||
                                    (existing.getGoalDescription() != null && existing.getGoalDescription().equals(desc))) &&
                            existing.getTargetAmount() == targetAmount &&
                            existing.getTargetDate() == targetDateMillis;

            if (noChange) {
                // 📝 Changed to showCustomToast and removed prefix
                showCustomToast("No changes detected");
                return;
            }

            // Build updated goal with same primary key
            SavingsGoal updated = new SavingsGoal(categoryId, goalName, desc, targetAmount, targetDateMillis);
            updated.setId(existing.getId());

            viewModel.update(updated);
            // 📝 Changed to showCustomToast and removed prefix
            showCustomToast("Goal updated!");
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
        // You should also set the typeface here, as discussed previously:
        // tvMessage.setTypeface(Typeface.MONOSPACE);

        Button btnCancel = v.findViewById(R.id.cancelDeleteBtn);
        Button btnDelete = v.findViewById(R.id.confirmDeleteBtn);

        String amount = String.format(Locale.getDefault(), "Rs. %,.0f", goal.getTargetAmount());
        String goalName = goal.getGoalName() == null ? "" : goal.getGoalName();

        tvMessage.setText("Are you sure you want to delete the goal of " + amount + " for '" + goalName + "'?");

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(v)
                .setCancelable(true)
                .create();

        // The dialog MUST be shown before setting window properties to ensure the Window object is active
        dialog.show(); // 👈 MOVED show() HERE

        // --- Window Customization Block (Now runs AFTER show()) ---
        Window window = dialog.getWindow();
        if (window != null) {
            // Set transparent background
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            // Set width to 70% of screen
            int dialogWidth = (int)(requireActivity().getResources().getDisplayMetrics().widthPixels * 0.8);
            window.setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        // -----------------------------------------------------------

        btnCancel.setOnClickListener(x -> dialog.dismiss());
        btnDelete.setOnClickListener(x -> {
            // 1. Delete the goal
            viewModel.delete(goal);

            // 2. Show toast
            showCustomToast("Goal deleted");

            // 3. Dismiss dialog
            dialog.dismiss();
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void refreshData() {
        // 1. Tell the ViewModel to re-fetch the category map.
        //    (The goals list updates automatically via LiveData, but categories don't)
        if (viewModel != null) {
            viewModel.fetchLatestCategoryMap();
        }

        // 2. Force the adapter to re-bind all visible items.
        //    This is CRITICAL. It forces onBindViewHolder() to run again
        //    for each item, which re-runs your progress bar calculation.
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        // 3. (Optional) Show a toast to confirm
        showCustomToast("Refreshing goals...");
    }



    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------
    private interface OnDatePicked { void onPicked(String ddMMyyyy); }

    private void showDatePicker(OnDatePicked cb) {
        final Calendar calendar = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        DatePickerDialog dp = new DatePickerDialog(
                requireContext(),
                (view, y, m, d) -> {
                    calendar.set(y, m, d);
                    String selectedDate = format.format(calendar.getTime());
                    cb.onPicked(selectedDate);
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

    private void showCustomToast(String message) { // Method to show custom toast dialog
        LayoutInflater inflater = getLayoutInflater(); // Get inflater to load custom layout
        View layout = inflater.inflate(R.layout.custom_message, null); // Inflate the custom toast layout

        TextView text = layout.findViewById(R.id.toast_message); // Get TextView for message
        ImageView close = layout.findViewById(R.id.toast_close); // Get close button ImageView
        ProgressBar progressBar = layout.findViewById(R.id.toast_progress); // Get ProgressBar

        text.setText(message); // Set the toast message text
        progressBar.setProgress(100); // Initialize progress bar to full

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext()) // Use requireContext() here
                .setView(layout) // Set custom layout
                .create(); // Create dialog instance

        close.setOnClickListener(v -> dialog.dismiss()); // Close dialog on button click

        if (dialog.getWindow() != null) { // Check if window exists
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); // Make background transparent
            dialog.getWindow().setDimAmount(0f); // Remove dim behind dialog

            WindowManager.LayoutParams params = dialog.getWindow().getAttributes(); // Get window attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT; // Set width to match parent
            params.height = WindowManager.LayoutParams.WRAP_CONTENT; // Set height to wrap content

            // 🛠️ UPDATED: Set gravity to BOTTOM
            params.gravity = android.view.Gravity.BOTTOM; // Position at bottom

            // 🛠️ UPDATED: Set offset from bottom in pixels
            params.y = 50;

            dialog.getWindow().setAttributes(params); // Apply attributes
        }

        dialog.show(); // Show the dialog

        new CountDownTimer(3000, 50) { // Timer for auto-dismiss: 3s, tick every 50ms
            public void onTick(long millisUntilFinished) { // Called on every tick
                int progress = (int) ((millisUntilFinished / 3000.0) * 100); // Calculate progress
                progressBar.setProgress(progress); // Update progress bar
            }
            public void onFinish() { // Called when timer finishes
                if (dialog.isShowing()) dialog.dismiss(); // Dismiss dialog
            }
        }.start(); // Start timer
    }
}