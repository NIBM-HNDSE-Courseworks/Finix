package com.example.finix.ui.settings; // Make sure this package matches your project structure

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.finix.R; // Make sure this R import is correct
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsFragment extends Fragment {

    private SwitchMaterial switchSyncProject;
    private TextView labelSyncFrequency;
    private RadioGroup radioGroupSync;
    private MaterialButton buttonEditCategory;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        // Assumes your XML file is named 'fragment_settings.xml'
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Find all the views from the layout using their IDs
        switchSyncProject = view.findViewById(R.id.switch_sync_project);
        labelSyncFrequency = view.findViewById(R.id.label_sync_frequency);
        radioGroupSync = view.findViewById(R.id.radio_group_sync);
        buttonEditCategory = view.findViewById(R.id.button_edit_category);



        // --- Toggle Switch Logic (ALREADY WORKS) ---
        // This code was also already correct. It shows/hides the
        // radio buttons based on the switch's state.

        // 1. Set the initial visibility
        updateSyncOptionsVisibility(switchSyncProject.isChecked());

        // 2. Add a listener to the switch to toggle visibility
        switchSyncProject.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateSyncOptionsVisibility(isChecked);
        });


        // --- Edit Category Button Navigation (NEWLY ADDED) ---
        // This is the new code you needed.
        // It listens for clicks on the "Edit Category" button.
        buttonEditCategory.setOnClickListener(v -> {
            // It uses the action you defined in 'mobile_navigation.xml'
            // to go from SettingsFragment to EditCategoriesFragment.
            try {
                Navigation.findNavController(view).navigate(R.id.action_SettingsFragment_to_EditCategoriesFragment);
            } catch (Exception e) {
                // Handle case where navigation might fail
                e.printStackTrace();
            }
        });
    }

    /**
     * Helper method to show or hide the sync frequency options.
     * (This method was already correct)
     * @param isVisible true to show the views, false to hide them.
     */
    private void updateSyncOptionsVisibility(boolean isVisible) {
        if (isVisible) {
            // Show the label and the radio button group
            labelSyncFrequency.setVisibility(View.VISIBLE);
            radioGroupSync.setVisibility(View.VISIBLE);
        } else {
            // Hide the label and the radio button group
            labelSyncFrequency.setVisibility(View.GONE);
            radioGroupSync.setVisibility(View.GONE);
        }
    }
}