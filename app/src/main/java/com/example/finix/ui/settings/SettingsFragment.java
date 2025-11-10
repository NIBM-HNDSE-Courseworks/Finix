package com.example.finix.ui.settings;

import android.app.Dialog;
import android.content.Intent; // NEW IMPORT
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri; // NEW IMPORT
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract; // NEW IMPORT
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher; // NEW IMPORT
import androidx.activity.result.contract.ActivityResultContracts; // NEW IMPORT
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.documentfile.provider.DocumentFile; // NEW IMPORT for better URI handling

import com.example.finix.R;
import com.example.finix.data.FinixRepository;
import com.example.finix.data.SynchronizationLog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import androidx.transition.TransitionManager;
import androidx.transition.AutoTransition;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    private SettingsViewModel viewModel;

    private SwitchMaterial switchSyncProject;
    private TextView labelSyncFrequency;
    private RadioGroup radioGroupSync;
    private MaterialButton buttonSyncNow;
    private ConstraintLayout layoutManageCategory;
    private ConstraintLayout layoutSyncLog;

    // Backup/Restore UI elements
    private MaterialButton buttonCreateBackup;
    private MaterialButton buttonRestoreBackup;
    private MaterialCardView cardSelectFolder;
    private MaterialCardView cardSelectFile;

    // Color constants for backup/restore buttons
    private static final int COLOR_CREATE_BACKUP = Color.parseColor("#3F9EA0");
    private static final int COLOR_RESTORE_BACKUP = Color.parseColor("#222222");

    // Dynamic text and button elements inside the selection card
    private TextView textFolderLabel;
    private TextView textSelectedFolder;
    private MaterialButton buttonSelectFolder;

    // SYNC UI elements
    private ProgressBar progressSync;
    private TextView textSyncPercentage;
    private TextView textSyncStatus;

    // Reference to the parent layout for transitions
    private ViewGroup settingsRootLayout;

    // Handler for the delayed visibility change
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideSyncUIRunnable;

    // Track the current backup mode (true = Backup/Create, false = Restore)
    private boolean isBackupMode = true;

    // Member variable to hold the URI selected for backup/restore
    private Uri selectedUri = null;

    private MaterialCardView buttonOpenReports;

    // ----------------------------------------------------
    // NEW: Activity Result Launchers for Storage Access Framework (SAF)
    // ----------------------------------------------------

    // Launcher for Folder Selection (ACTION_OPEN_DOCUMENT_TREE) for Backup
    private final ActivityResultLauncher<Uri> createBackupFolderLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), this::handleBackupFolderSelection);

    // Launcher for File Selection (ACTION_OPEN_DOCUMENT) for Restore
    private final ActivityResultLauncher<String[]> restoreFileLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleRestoreFileSelection);

    // ----------------------------------------------------


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
        layoutSyncLog = view.findViewById(R.id.layout_sync_log);

        progressSync = view.findViewById(R.id.progress_sync);
        textSyncPercentage = view.findViewById(R.id.text_sync_percentage);
        textSyncStatus = view.findViewById(R.id.text_sync_status);

        // Backup/Restore views
        buttonCreateBackup = view.findViewById(R.id.button_create_backup);
        buttonRestoreBackup = view.findViewById(R.id.button_restore_backup);
        cardSelectFolder = view.findViewById(R.id.card_select_folder);
        cardSelectFile = view.findViewById(R.id.card_select_file);

        // Dynamic elements inside the selection card
        textFolderLabel = view.findViewById(R.id.text_folder_label);
        textSelectedFolder = view.findViewById(R.id.text_selected_folder);
        buttonSelectFolder = view.findViewById(R.id.button_select_folder);


        buttonOpenReports = view.findViewById(R.id.card_reports);

        buttonOpenReports.setOnClickListener(v -> {
            try {
                Navigation.findNavController(view).navigate(R.id.action_SettingsFragment_to_ReportsFragment);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(getContext(), "Navigation error!", Toast.LENGTH_SHORT).show();
            }
        });

        // 1. Set the initial visibility based on the switch state
        updateSyncOptionsVisibility(switchSyncProject.isChecked());

        // 2. Add a listener to the switch to toggle visibility with animation
        switchSyncProject.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (hideSyncUIRunnable != null) {
                handler.removeCallbacks(hideSyncUIRunnable);
            }
            TransitionManager.beginDelayedTransition(settingsRootLayout, new AutoTransition());
            updateSyncOptionsVisibility(isChecked);
        });

        // 3. Sync Now Button Listener
        buttonSyncNow.setOnClickListener(v -> {
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

        // 7. Backup/Restore Button Listeners
        buttonCreateBackup.setOnClickListener(v -> {
            // Reset selected URI when mode changes
            selectedUri = null;

            isBackupMode = true;
            buttonCreateBackup.setBackgroundTintList(ColorStateList.valueOf(COLOR_CREATE_BACKUP));
            buttonRestoreBackup.setBackgroundTintList(ColorStateList.valueOf(COLOR_RESTORE_BACKUP));

            textFolderLabel.setText(getString(R.string.selected_backup_folder_label));
            // Use a placeholder text indicating no folder selected yet
            textSelectedFolder.setText(getString(R.string.no_folder_selected));
            buttonSelectFolder.setText(getString(R.string.select_folder_button));

            TransitionManager.beginDelayedTransition(settingsRootLayout, new AutoTransition());
            cardSelectFolder.setVisibility(View.VISIBLE);
            cardSelectFile.setVisibility(View.GONE);

            Toast.makeText(getContext(), "Select folder to save backup...", Toast.LENGTH_SHORT).show();
        });

        buttonRestoreBackup.setOnClickListener(v -> {
            // Reset selected URI when mode changes
            selectedUri = null;

            isBackupMode = false;
            buttonCreateBackup.setBackgroundTintList(ColorStateList.valueOf(COLOR_RESTORE_BACKUP));
            buttonRestoreBackup.setBackgroundTintList(ColorStateList.valueOf(COLOR_CREATE_BACKUP));

            textFolderLabel.setText(getString(R.string.selected_backup_file_label));
            // Use a placeholder text indicating no file selected yet
            textSelectedFolder.setText(getString(R.string.no_file_selected));
            buttonSelectFolder.setText(getString(R.string.select_file_button));

            TransitionManager.beginDelayedTransition(settingsRootLayout, new AutoTransition());
            cardSelectFolder.setVisibility(View.VISIBLE);
            cardSelectFile.setVisibility(View.GONE);

            Toast.makeText(getContext(), "Select file to restore backup from...", Toast.LENGTH_SHORT).show();
        });

        // 8. Select Folder/File Action Button Listener (Triggers the system picker)
        buttonSelectFolder.setOnClickListener(v -> {
            if (isBackupMode) {
                // Launch the SAF Folder Picker (OpenDocumentTree)
                // Use the last selected folder URI if available as the starting point
                Uri initialUri = selectedUri != null ? selectedUri : null;
                createBackupFolderLauncher.launch(initialUri);
            } else {
                // Launch the SAF File Picker (OpenDocument) for JSON files
                restoreFileLauncher.launch(new String[]{"application/json"});
            }
        });
    }


    /**
     * Handles the result of the folder selection for backup.
     */
    private void handleBackupFolderSelection(Uri uri) {
        if (uri != null) {
            // CRITICAL: Take persistence permissions for the selected folder URI
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            requireContext().getContentResolver().takePersistableUriPermission(uri, takeFlags);

            selectedUri = uri;

            // Use DocumentFile to display a human-readable name, if possible, otherwise use the raw URI
            DocumentFile documentFile = DocumentFile.fromTreeUri(requireContext(), uri);
            String name = (documentFile != null && documentFile.getName() != null) ? documentFile.getName() : uri.toString();

            textSelectedFolder.setText(name);

            // Now that a folder is selected, perform the backup.
            // We pass the folder URI string and the desired filename.
            String fileName = "finix_backup_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".json";
            performBackupRestoreOperation(uri.toString(), fileName);
        } else {
            Toast.makeText(getContext(), "Backup folder selection cancelled.", Toast.LENGTH_SHORT).show();
            textSelectedFolder.setText(getString(R.string.no_folder_selected));
        }
    }

    /**
     * Handles the result of the file selection for restore.
     */
    private void handleRestoreFileSelection(Uri uri) {
        if (uri != null) {
            selectedUri = uri;

            // Use DocumentFile to display a human-readable name
            DocumentFile documentFile = DocumentFile.fromSingleUri(requireContext(), uri);
            String name = (documentFile != null && documentFile.getName() != null) ? documentFile.getName() : uri.toString();

            textSelectedFolder.setText(name);

            // Now that a file is selected, perform the restore.
            performBackupRestoreOperation(uri.toString(), null); // Filename is null for restore
        } else {
            Toast.makeText(getContext(), "Restore file selection cancelled.", Toast.LENGTH_SHORT).show();
            textSelectedFolder.setText(getString(R.string.no_file_selected));
        }
    }


    /**
     * Executes the backup or restore operation based on the current mode and URI.
     * This is called by the ActivityResultLauncher handlers.
     */
    private void performBackupRestoreOperation(String uriString, @Nullable String fileName) {
        boolean success;
        String toastMessage;

        if (isBackupMode) {
            // Backup: uriString is the folder URI. fileName is the desired name of the new file.
            success = viewModel.createBackup(uriString, fileName);
            toastMessage = success ? "Backup created successfully as: " + fileName : "Backup failed! Check permissions/logs.";

        } else {
            // Restore: uriString is the file URI.
            success = viewModel.restoreBackup(uriString);
            toastMessage = success ? "Data restored successfully from selected file." : "Restore failed! (Check file format)";
        }

        Toast.makeText(getContext(), toastMessage, Toast.LENGTH_LONG).show();
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