package com.example.finix.ui.settings;

import android.app.Dialog; // NEW IMPORT
import android.graphics.Color; // NEW IMPORT
import android.graphics.drawable.ColorDrawable; // NEW IMPORT
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity; // NEW IMPORT
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager; // NEW IMPORT
import android.widget.LinearLayout; // NEW IMPORT
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

import com.example.finix.R;
import com.example.finix.data.FinixRepository;
import com.example.finix.data.SynchronizationLog; // NEW IMPORT
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import androidx.transition.TransitionManager;
import androidx.transition.AutoTransition;

import java.text.SimpleDateFormat; // NEW IMPORT
import java.util.Date; // NEW IMPORT
import java.util.List;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    private SettingsViewModel viewModel;

    private SwitchMaterial switchSyncProject;
    private TextView labelSyncFrequency;
    private RadioGroup radioGroupSync;
    private MaterialButton buttonSyncNow;
    private ConstraintLayout layoutManageCategory;
    private ConstraintLayout layoutSyncLog; // NEW: ConstraintLayout for the Sync Log button

    // SYNC UI elements
    private ProgressBar progressSync;
    private TextView textSyncPercentage;
    private TextView textSyncStatus;

    // Reference to the parent layout for transitions
    private ViewGroup settingsRootLayout;

    // Handler for the delayed visibility change
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideSyncUIRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        settingsRootLayout = (ViewGroup) view;

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Find all the views
        switchSyncProject = view.findViewById(R.id.switch_sync_project);
        labelSyncFrequency = view.findViewById(R.id.label_sync_frequency);
        radioGroupSync = view.findViewById(R.id.radio_group_sync);
        buttonSyncNow = view.findViewById(R.id.button_sync_now);
        layoutManageCategory = view.findViewById(R.id.layout_manage_category);
        layoutSyncLog = view.findViewById(R.id.layout_sync_log); // NEW: Find Sync Log Layout

        progressSync = view.findViewById(R.id.progress_sync);
        textSyncPercentage = view.findViewById(R.id.text_sync_percentage);
        textSyncStatus = view.findViewById(R.id.text_sync_status);

        // 1. Set the initial visibility based on the switch state
        updateSyncOptionsVisibility(switchSyncProject.isChecked());

        // 2. Add a listener to the switch to toggle visibility with animation
        switchSyncProject.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Cancel any pending UI reset when the sync switch is toggled
            if (hideSyncUIRunnable != null) {
                handler.removeCallbacks(hideSyncUIRunnable);
            }
            TransitionManager.beginDelayedTransition(settingsRootLayout, new AutoTransition());
            updateSyncOptionsVisibility(isChecked);
        });

        // 3. Sync Now Button Listener (Delegates to ViewModel)
        buttonSyncNow.setOnClickListener(v -> {
            // Show sync UI immediately upon click
            TransitionManager.beginDelayedTransition(settingsRootLayout, new AutoTransition());
            showSyncUIElements(true);

            viewModel.startSync();
            Toast.makeText(getContext(), "Sync started...", Toast.LENGTH_SHORT).show();
        });


        // 4. Observe Synchronization Status
        observeSyncStatus();


        // 5. Sync Log Button Listener (NEW)
        layoutSyncLog.setOnClickListener(v -> {
            showSyncLogPopup();
        });


        // 6. Edit Category Button Navigation
        layoutManageCategory.setOnClickListener(v -> {
            try {
                Navigation.findNavController(view).navigate(R.id.action_SettingsFragment_to_EditCategoriesFragment);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Crucial: Clean up the handler callbacks to prevent memory leaks
        if (hideSyncUIRunnable != null) {
            handler.removeCallbacks(hideSyncUIRunnable);
        }
    }

    /**
     * Helper method to show or hide the sync options based on the switch state.
     */
    private void updateSyncOptionsVisibility(boolean isEnabled) {
        if (isEnabled) {
            labelSyncFrequency.setVisibility(View.VISIBLE);
            radioGroupSync.setVisibility(View.VISIBLE);
            buttonSyncNow.setVisibility(View.GONE); // Hide Sync Now
            showSyncUIElements(false); // Hide sync status elements too
        } else {
            labelSyncFrequency.setVisibility(View.GONE);
            radioGroupSync.setVisibility(View.GONE);
            // In the manual sync case, the button's visibility is controlled by sync state
            // If IDLE, show button; if running/finished, show progress/status
            updateSyncUI(viewModel.getSyncStatus().getValue());
        }
    }

    /**
     * Toggles the visibility of the sync status UI elements (ProgressBar, TextViews).
     * @param show If true, hides button and shows sync status elements. If false, shows button and hides sync status elements.
     */
    private void showSyncUIElements(boolean show) {
        if (switchSyncProject.isChecked()) return; // Only control visibility in Manual Sync mode

        // Ensure we are operating on the root layout for transition
        TransitionManager.beginDelayedTransition(settingsRootLayout, new AutoTransition());

        buttonSyncNow.setVisibility(show ? View.GONE : View.VISIBLE);
        progressSync.setVisibility(show ? View.VISIBLE : View.GONE);
        textSyncPercentage.setVisibility(show ? View.VISIBLE : View.GONE);
        textSyncStatus.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * Observes the LiveData from the ViewModel to update the sync status UI.
     */
    private void observeSyncStatus() {
        viewModel.getSyncStatus().observe(getViewLifecycleOwner(), this::updateSyncUI);
    }

    /**
     * Updates the sync UI elements based on the SynchronizationState and handles the 10-second delay.
     */
    private void updateSyncUI(FinixRepository.SynchronizationState state) {
        if (state == null || switchSyncProject.isChecked()) return; // Ignore if Auto Sync is on

        // Cancel any pending runnable to reset the UI if a new sync starts
        if (hideSyncUIRunnable != null) {
            handler.removeCallbacks(hideSyncUIRunnable);
        }

        String statusText;
        int progress = 0;
        boolean isSyncing = false;
        boolean isFinished = false;

        switch (state) {
            case IDLE:
                statusText = "Status: Ready to sync.";
                progress = 0;
                isSyncing = false;
                showSyncUIElements(false); // Hide sync UI, show button
                break;

            case CHECKING:
                statusText = "Status: Checking for local changes...";
                progress = 10;
                isSyncing = true;
                break;

            case PROCESSING:
                statusText = "Status: Uploading changes to server...";
                progress = 50;
                isSyncing = true;
                break;

            case SUCCESS:
                statusText = "Status: Synchronization successful!";
                progress = 100;
                isFinished = true;
                break;

            case NO_CHANGES:
                statusText = "Status: Nothing to sync. Local data is up-to-date.";
                progress = 100;
                isFinished = true;
                break;

            case ERROR:
                statusText = "Status: Synchronization failed! Please check your network.";
                progress = 0;
                isFinished = true;
                break;

            default:
                statusText = "Status: Unknown state.";
                progress = 0;
                isFinished = true;
                break;
        }

        // --- Apply UI updates for ongoing sync/checking ---
        if (state != FinixRepository.SynchronizationState.IDLE) {
            // Ensure sync UI is shown (only if not IDLE)
            showSyncUIElements(true);

            progressSync.setProgress(progress);
            textSyncPercentage.setText(progress + "%");
            textSyncStatus.setText(statusText);

            // Disable button while syncing/checking
            buttonSyncNow.setEnabled(!isSyncing);
        }


        // --- Handle delay after finished state ---
        if (isFinished) {
            // Set the visibility of the "finished" state immediately
            progressSync.setProgress(progress);
            textSyncPercentage.setText(progress + "%");
            textSyncStatus.setText(statusText);
            buttonSyncNow.setEnabled(true);

            // Define the runnable to hide the sync status elements
            hideSyncUIRunnable = () -> {
                // Ensure we use a smooth transition
                TransitionManager.beginDelayedTransition(settingsRootLayout, new AutoTransition());
                showSyncUIElements(false); // Hide sync UI, show button
            };

            // Post the runnable with a 10-second delay (10000 milliseconds)
            handler.postDelayed(hideSyncUIRunnable, 10000);
        }
    }


    /**
     * NEW: Displays the sync log popup and populates it with log entries.
     */
    private void showSyncLogPopup() {
        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(R.layout.log_popup);

        // Make the dialog background transparent and set layout parameters
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.CENTER;
            dialog.getWindow().setAttributes(lp);
        }

        LinearLayout logLayout = dialog.findViewById(R.id.layout_sync_log_entries);
        logLayout.removeAllViews(); // Clear previous views

        // Get logs from ViewModel (assuming a synchronous call for simplicity in this example)
        // NOTE: In a real app, this should be done asynchronously (e.g., using LiveData/Coroutines).
        List<SynchronizationLog> logs = viewModel.getAllSyncLogs();


        if (logs == null || logs.isEmpty()) {
            TextView noLogText = new TextView(getContext());
            noLogText.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            noLogText.setText("No synchronization logs found.");
            noLogText.setTextColor(Color.parseColor("#AAAAAA"));
            noLogText.setTextSize(16);
            logLayout.addView(noLogText);
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
            for (SynchronizationLog log : logs) {
                TextView logEntry = new TextView(getContext());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 4, 0, 4);
                logEntry.setLayoutParams(params);

                String date = sdf.format(new Date(log.getLastSyncedTimestamp()));
                String logText = String.format(Locale.getDefault(),
                        "[%s] Table: %s | ID: %d | Status: %s",
                        date, log.getTableName(), log.getRecordId(), log.getStatus());

                logEntry.setText(logText);
                logEntry.setTextColor(Color.parseColor("#FFFFFF"));
                logEntry.setTextSize(14);
                logLayout.addView(logEntry);
            }
        }

        // Setup button listeners
        dialog.findViewById(R.id.buttonCancelLog).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.buttonDownloadLog).setOnClickListener(v -> {
            // Placeholder for download logic
            Toast.makeText(getContext(), "Download Log functionality not yet implemented.", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }
}