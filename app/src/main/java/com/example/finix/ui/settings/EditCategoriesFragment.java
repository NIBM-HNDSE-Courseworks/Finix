package com.example.finix.ui.settings;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider; // <-- IMPORTANT: Use ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finix.R;
import com.example.finix.data.Category;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.function.Consumer;

public class EditCategoriesFragment extends Fragment implements CategoryAdapter.OnCategoryClickListener {

    private EditCategoriesViewModel viewModel; // <-- The ViewModel
    private RecyclerView recyclerView;
    private CategoryAdapter adapter;

    private ImageButton buttonAddCategories;
    private ImageView imageNoCategories;
    private TextView textNoCategories;
    private EditText search_bar;
    private ImageButton button_search;
    private ImageButton button_refresh;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_edit_categories, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // --- 1. Initialize ViewModel ---
        viewModel = new ViewModelProvider(this).get(EditCategoriesViewModel.class);

        // --- 2. Initialize Views ---
        recyclerView = view.findViewById(R.id.recyclerIncome); // ID from your XML
        buttonAddCategories = view.findViewById(R.id.buttonAddCategories);
        imageNoCategories = view.findViewById(R.id.imageNoCategories);
        textNoCategories = view.findViewById(R.id.textNoCategories);
        search_bar = view.findViewById(R.id.search_bar);
        button_search = view.findViewById(R.id.button_search);
        button_refresh = view.findViewById(R.id.button_refresh);

        // --- 3. Setup UI ---
        setupRecyclerView();
        setupClickListeners();

        // --- 4. Setup Observers ---
        setupObservers();

        // --- 5. Initial Data Load ---
        // Tell the ViewModel to load the data. The observer will handle the result.
        viewModel.loadCategories();
    }

    private void setupRecyclerView() {
        // Pass an empty list to start. The observer will populate it.
        adapter = new CategoryAdapter(new ArrayList<>(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupClickListeners() {
        // Set click listener for the "Add" button
        buttonAddCategories.setOnClickListener(v -> showAddEditCategoryDialog(null));

        // Set click listener for the "Search" button
        button_search.setOnClickListener(v -> {
            String query = search_bar.getText().toString();
            // Tell the ViewModel to perform the search
            viewModel.searchCategories(query);
        });

        button_refresh.setOnClickListener(v -> {
            // Clear the search bar
            search_bar.setText("");
            // Tell the ViewModel to load all categories
            viewModel.loadCategories();
        });
    }

    /**
     * This is where the Fragment connects to the ViewModel.
     * It observes the LiveData and reacts to any changes.
     */
    private void setupObservers() {
        // --- Observer for the Category List ---
        viewModel.getCategoriesLive().observe(getViewLifecycleOwner(), categories -> {
            // This code runs every time the category list changes in the ViewModel
            if (categories != null) {
                adapter.setCategories(categories);
                toggleEmptyView(categories.isEmpty(), "No Categories");
            }
        });

        // --- Observer for Messages/Events ---
        viewModel.getMessageEvent().observe(getViewLifecycleOwner(), event -> {
            // This code runs when the ViewModel sends a one-time message
            String message = event.getContentIfNotHandled();
            if (message == null) return; // Event was already handled
            if (message.startsWith("ERROR:")) {
                // Show an error toast
                Toast.makeText(getContext(), message.substring(6), Toast.LENGTH_SHORT).show();
            } else if (message.startsWith("SUCCESS:")) {
                // Show a success toast
                Toast.makeText(getContext(), message.substring(8), Toast.LENGTH_SHORT).show();
            } else if (message.startsWith("NO_RESULTS:")) {
                // Show the "No Results" dialog
                String query = message.substring(11);
                showNoResultsDialog(query);
                // Also toggle the empty view (in case the list is empty)
                toggleEmptyView(true, "No Categories");
            }
        });

        viewModel.getShowUndoDeleteEvent().observe(getViewLifecycleOwner(), event -> {
            // This code runs when the ViewModel sends the Undo event
            EditCategoriesViewModel.UndoPayload payload = event.getContentIfNotHandled();
            if (payload != null) {
                // Show the Snackbar
                showUndoSnackbar(payload);
            }
        });
    }

    private void showUndoSnackbar(EditCategoriesViewModel.UndoPayload payload) {
        // We use requireView() to anchor the Snackbar to the fragment's layout
        Snackbar snackbar = Snackbar.make(
                requireView(),
                "Deleted for category '" + payload.category.getName() + "'",
                Snackbar.LENGTH_LONG
        );

        snackbar.setDuration(5000);

        // Set the "UNDO" action
        snackbar.setAction("UNDO", v -> {
            // Tell the ViewModel to undo the delete
            viewModel.undoDelete(payload);
        });

        // Add a callback to know when the Snackbar is dismissed
        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int dismissEvent) {
                super.onDismissed(transientBottomBar, dismissEvent);

                // Check if it was dismissed for any reason *other* than clicking "UNDO"
                // (e.g., it timed out, or was swiped away)
                if (dismissEvent != DISMISS_EVENT_ACTION) {
                    // The user did NOT click undo. Finalize the delete.
                    viewModel.finalizeDelete(payload.category);
                }
            }
        });

        snackbar.show();
    }
    /**
     * Shows the popup for adding or editing a category.
     * @param category The category to edit, or null to add a new one.
     */
    private void showAddEditCategoryDialog(@Nullable Category category) {
        final boolean isEditMode = (category != null);

        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.add_edit_categories_popup, null);

        // Use dialogView in AlertDialog.Builder
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext()); // Use requireContext() for safety in a Fragment
        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();

        // =========================================================================
        // 💥 APPLYING THE REMEMBERED METHOD FOR FULL-SCREEN/KEYBOARD FIXES 💥
        // =========================================================================
        if (dialog.getWindow() != null) {
            // CRITICAL FIX 1: Force Full Width and Full Height
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);

            // CRITICAL FIX 2: Ensure dialog resizes when keyboard appears
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

            // CRITICAL FIX 3: Remove default AlertDialog padding/insets for true full screen
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        // =========================================================================

        // Get views from the popup layout
        TextView popupTitle = dialogView.findViewById(R.id.popupTitle);
        EditText etCategoryName = dialogView.findViewById(R.id.addCategory);
        Button btnSave = dialogView.findViewById(R.id.btnSaveCategory);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);

        // ... (Your existing logic for setting text and listeners) ...

        if (isEditMode) {
            popupTitle.setText("Edit Category");
            btnSave.setText("Update");
            etCategoryName.setText(category.getName());
        } else {
            popupTitle.setText("Add Category");
            btnSave.setText("Save");
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String categoryName = etCategoryName.getText().toString();

            // Create a success callback to dismiss the dialog
            // This will be run by the ViewModel on the main thread ONLY if validation passes
            Runnable onSuccess = () -> dialog.dismiss();

            // Use requireContext() to get the String resource if needed
            Consumer<String> onFailure = (errorMessage) -> {
                etCategoryName.setError(errorMessage);
            };

            etCategoryName.setError(null);

            // --- Pass the data to the ViewModel to handle ALL logic ---
            if (isEditMode) {
                viewModel.updateCategory(category, categoryName, onSuccess, onFailure);
            } else {
                viewModel.addCategory(categoryName, onSuccess, onFailure);
            }
        });

        dialog.show();
    }

    /**
     * Shows the delete confirmation popup.
     * @param category The category to be deleted.
     */
    private void showDeleteConfirmationDialog(Category category) {
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.delete_confirmation_popup, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();

        Button btnConfirmDelete = dialogView.findViewById(R.id.confirmDeleteBtn);
        Button btnCancelDelete = dialogView.findViewById(R.id.cancelDeleteBtn);
        TextView deleteMessage = dialogView.findViewById(R.id.deleteMessage);

        deleteMessage.setText("Are you sure you want to delete '" + category.getName() + "'?");

        btnCancelDelete.setOnClickListener(v -> dialog.dismiss());

        btnConfirmDelete.setOnClickListener(v -> {
            // --- Tell the ViewModel to delete ---
            viewModel.deleteCategory(category);
            // The observer will show the toast and refresh the list
            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * Helper method to show the "No Search Results" dialog.
     */
    private void showNoResultsDialog(String query) {
        new AlertDialog.Builder(getContext())
                .setTitle("Search Result Failed!")
                .setMessage("No results found for '" + query + "'")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Helper method to toggle the visibility of the "Empty" placeholder.
     */
    private void toggleEmptyView(boolean isEmpty, String message) {
        if (isEmpty) {
            recyclerView.setVisibility(View.GONE);
            imageNoCategories.setVisibility(View.VISIBLE);
            textNoCategories.setText(message);
            textNoCategories.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            imageNoCategories.setVisibility(View.GONE);
            textNoCategories.setVisibility(View.GONE);
        }
    }

    // --- Adapter Click Listener Implementation ---

    @Override
    public void onEditClick(Category category) {
        // Called from the adapter
        showAddEditCategoryDialog(category);
    }

    @Override
    public void onDeleteClick(Category category) {
        // Called from the adapter
        showDeleteConfirmationDialog(category);
    }
}