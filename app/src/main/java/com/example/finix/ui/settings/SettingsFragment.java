package com.example.finix.ui.settings;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log; // ADDED: Import Log
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.example.finix.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsFragment extends Fragment {

    private static final String LOG_TAG = "SettingsFragment"; // ADDED: Log Tag

    private SwitchMaterial switchSyncProject;
    private TextView labelSyncFrequency;
    private RadioGroup radioGroupSync;
    private MaterialButton buttonSyncNow;
    private ConstraintLayout layoutManageCategory;

    private ProgressBar progressSync;
    private TextView textSyncPercentage;
    private TextView textSyncStatus;

    private SettingsViewModel viewModel;
    private ViewGroup settingsRootLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreateView started.");
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        settingsRootLayout = (ViewGroup) view;
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(LOG_TAG, "onViewCreated started. Initializing ViewModel and Views.");

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        switchSyncProject = view.findViewById(R.id.switch_sync_project);
        labelSyncFrequency = view.findViewById(R.id.label_sync_frequency);
        radioGroupSync = view.findViewById(R.id.radio_group_sync);
        buttonSyncNow = view.findViewById(R.id.button_sync_now);
        layoutManageCategory = view.findViewById(R.id.layout_manage_category);

        progressSync = view.findViewById(R.id.progress_sync);
        textSyncPercentage = view.findViewById(R.id.text_sync_percentage);
        textSyncStatus = view.findViewById(R.id.text_sync_status);

        // Initial UI state setup
        updateSyncOptionsVisibility(switchSyncProject.isChecked());
        Log.d(LOG_TAG, "Initial sync switch state: " + switchSyncProject.isChecked());


        // --- Listeners ---
        switchSyncProject.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.i(LOG_TAG, "Sync switch toggled to: " + isChecked);
            startSmoothTransition();
            updateSyncOptionsVisibility(isChecked);
        });

        layoutManageCategory.setOnClickListener(v -> {
            Log.d(LOG_TAG, "Manage Category layout clicked. Navigating...");
            try {
                Navigation.findNavController(view).navigate(R.id.action_SettingsFragment_to_EditCategoriesFragment);
                Log.i(LOG_TAG, "Navigation to EditCategoriesFragment successful.");
            } catch (Exception e) {
                Log.e(LOG_TAG, "Navigation failed: " + e.getMessage());
                e.printStackTrace();
            }
        });

        buttonSyncNow.setOnClickListener(v -> {
            Log.w(LOG_TAG, "Sync Now button clicked. Starting sync process.");
            startSmoothTransition();
            setSyncProgressVisibility(true);

            Toast.makeText(getContext(), "Synchronization Started...", Toast.LENGTH_SHORT).show();
            viewModel.syncCategories();
        });

        // --- Observers ---
        viewModel.getSyncProgress().observe(getViewLifecycleOwner(), progress -> {
            if (progress != null) {
                Log.v(LOG_TAG, "Sync Progress update: " + progress + "%");
                progressSync.setProgress(progress);
                textSyncPercentage.setText(progress + "%");
            }
        });

        viewModel.getSyncStatus().observe(getViewLifecycleOwner(), status -> {
            if (status != null) {
                Log.d(LOG_TAG, "Sync Status update: " + status);
                textSyncStatus.setText("Status: " + status);

                if (status.equals("Sync Complete") || status.startsWith("Sync Failed") || status.equals("No changes detected")) {
                    Log.i(LOG_TAG, "Sync has concluded. Handling final state.");

                    final boolean showToast = !status.equals("No changes detected");

                    if (showToast) {
                        Toast.makeText(getContext(), status, Toast.LENGTH_LONG).show();
                    }

                    startSmoothTransition();

                    // Delayed UI reset to allow user to see final status
                    new Handler().postDelayed(() -> {
                        Log.v(LOG_TAG, "Delay finished. Resetting UI state.");
                        viewModel.setSyncing(false);
                        startSmoothTransition();
                        setSyncProgressVisibility(false);
                        updateSyncOptionsVisibility(switchSyncProject.isChecked());
                    }, 3000);
                }
            }
        });

        // Set visibility based on initial ViewModel state (e.g., if sync was running before fragment recreation)
        boolean isSyncing = viewModel.isSyncing();
        Log.i(LOG_TAG, "Initial check for isSyncing: " + isSyncing);
        setSyncProgressVisibility(isSyncing);
    }

    /** 🌈 Creates a smooth transition */
    private void startSmoothTransition() {
        Log.v(LOG_TAG, "Starting smooth UI transition.");
        AutoTransition smooth = new AutoTransition();
        smooth.setDuration(500);
        smooth.setInterpolator(new DecelerateInterpolator());
        TransitionManager.beginDelayedTransition(settingsRootLayout, smooth);
    }

    private void updateSyncOptionsVisibility(boolean isEnabled) {
        Log.d(LOG_TAG, "Updating sync options visibility (isEnabled: " + isEnabled + ").");
        if (isEnabled) {
            labelSyncFrequency.setVisibility(View.VISIBLE);
            radioGroupSync.setVisibility(View.VISIBLE);
            buttonSyncNow.setVisibility(View.GONE);
            Log.v(LOG_TAG, "Auto-sync enabled. Showing Frequency, hiding Sync Now.");
        } else {
            labelSyncFrequency.setVisibility(View.GONE);
            radioGroupSync.setVisibility(View.GONE);
            // Only show Sync Now if we are NOT currently syncing
            buttonSyncNow.setVisibility(viewModel.isSyncing() ? View.GONE : View.VISIBLE);
            Log.v(LOG_TAG, "Auto-sync disabled. Hiding Frequency. Sync Now visibility: " + buttonSyncNow.getVisibility());
        }
    }

    private void setSyncProgressVisibility(boolean isVisible) {
        Log.i(LOG_TAG, "Setting sync progress UI visibility to: " + isVisible);
        int visibility = isVisible ? View.VISIBLE : View.GONE;

        if (progressSync != null) progressSync.setVisibility(visibility);
        if (textSyncPercentage != null) textSyncPercentage.setVisibility(visibility);
        if (textSyncStatus != null) textSyncStatus.setVisibility(visibility);

        // Ensure Sync Now button is hidden when progress is visible
        if (isVisible) {
            if (buttonSyncNow != null) buttonSyncNow.setVisibility(View.GONE);
        }
    }
}
