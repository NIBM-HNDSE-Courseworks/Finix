package com.example.finix.ui.savings;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finix.R;
import com.example.finix.data.SavingsGoal;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class GoalsAdapter extends RecyclerView.Adapter<GoalsAdapter.GoalViewHolder> {

    private final List<SavingsGoal> goals;

    public GoalsAdapter(List<SavingsGoal> goals) {
        this.goals = goals;
    }

    @NonNull
    @Override
    public GoalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_goal, parent, false);
        return new GoalViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GoalViewHolder holder, int position) {
        SavingsGoal goal = goals.get(position);

        holder.tvGoalName.setText(goal.getGoalName());
        holder.tvCategoryName.setText("Category ID: " + goal.getCategoryId());
        holder.tvTargetAmount.setText(String.format(Locale.getDefault(), "Target: $%.2f", goal.getTargetAmount()));

        // Format target date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        holder.tvTargetDate.setText(sdf.format(goal.getTargetDate()));

        // Info icon click → show popup with details
        holder.ivInfo.setOnClickListener(v -> {
            new AlertDialog.Builder(v.getContext())
                    .setTitle(goal.getGoalName())
                    .setMessage(
                            "Description: " + goal.getGoalDescription() + "\n\n" +
                                    "Category ID: " + goal.getCategoryId() + "\n" +
                                    "Target Amount: $" + goal.getTargetAmount() + "\n" +
                                    "Target Date: " + sdf.format(goal.getTargetDate())
                    )
                    .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                    .show();
        });
    }

    @Override
    public int getItemCount() {
        return goals.size();
    }

    static class GoalViewHolder extends RecyclerView.ViewHolder {
        TextView tvGoalName, tvCategoryName, tvTargetAmount, tvTargetDate;
        ImageView ivInfo;

        GoalViewHolder(@NonNull View itemView) {
            super(itemView);
            tvGoalName = itemView.findViewById(R.id.tvGoalName);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
            tvTargetAmount = itemView.findViewById(R.id.tvTargetAmount);
            tvTargetDate = itemView.findViewById(R.id.tvTargetDate);
            ivInfo = itemView.findViewById(R.id.ivInfo);
        }
    }
}
