package com.example.finix.ui.savings;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.example.finix.R;
import com.example.finix.data.FinixDatabase;
import com.example.finix.data.SavingsGoal;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class SavingsGoalScreen extends Fragment {

    private RecyclerView rvGoals;
    private Button btnAddGoal;
    private FinixDatabase db;
    private GoalsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_savings_goals, container, false);

        rvGoals = view.findViewById(R.id.rvGoals);
        btnAddGoal = view.findViewById(R.id.btnAddGoal);

        db = Room.databaseBuilder(requireContext(),
                        FinixDatabase.class, "finix-db")
                .allowMainThreadQueries()
                .build();

        loadGoals();

        btnAddGoal.setOnClickListener(v -> showAddGoalDialog());

        return view;
    }

    private void loadGoals() {
        List<SavingsGoal> goalList = db.savingsGoalDao().getAllGoals();
        rvGoals.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new GoalsAdapter(goalList);
        rvGoals.setAdapter(adapter);
    }

    private void showAddGoalDialog() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View dialogView = inflater.inflate(R.layout.dialog_add_goal, null);

        EditText etCategoryId = dialogView.findViewById(R.id.etCategoryId);
        EditText etGoalName = dialogView.findViewById(R.id.etGoalName);
        EditText etGoalDescription = dialogView.findViewById(R.id.etGoalDescription);
        EditText etTargetAmount = dialogView.findViewById(R.id.etTargetAmount);
        EditText etTargetDate = dialogView.findViewById(R.id.etTargetDate);
        Button btnSaveGoal = dialogView.findViewById(R.id.btnSaveGoal);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        btnSaveGoal.setOnClickListener(v -> {
            try {
                int categoryId = Integer.parseInt(etCategoryId.getText().toString());
                String name = etGoalName.getText().toString();
                String desc = etGoalDescription.getText().toString();
                double amount = Double.parseDouble(etTargetAmount.getText().toString());
                String dateStr = etTargetDate.getText().toString();

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                Date parsedDate = sdf.parse(dateStr);
                long dateMillis = parsedDate != null ? parsedDate.getTime() : 0;

                SavingsGoal goal = new SavingsGoal(categoryId, name, desc, amount, dateMillis);
                db.savingsGoalDao().insert(goal);
                Toast.makeText(getContext(), "Goal added successfully!", Toast.LENGTH_SHORT).show();

                dialog.dismiss();
                loadGoals();

            } catch (NumberFormatException | ParseException e) {
                Toast.makeText(getContext(), "Invalid input format!", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }
}
