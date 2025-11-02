package com.example.finix.ui.budget;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;

import com.example.finix.R;
import com.example.finix.data.Budget;
import com.example.finix.data.Category;
import com.example.finix.data.Transaction;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.BudgetViewHolder> {

    private List<Budget> budgetList;
    private final Context context;
    private List<Transaction> transactionList;
    private List<Category> categoryList;
    private final OnBudgetActionListener listener;

    // Date formatter for display
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM", Locale.getDefault());


    // Listener interface
    public interface OnBudgetActionListener {
        void onEdit(Budget budget);
        void onDelete(Budget budget);
    }

    public BudgetAdapter(Context context, OnBudgetActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setData(List<Budget> budgets, List<Transaction> transactions, List<Category> categories) {
        this.budgetList = budgets;
        this.transactionList = transactions;
        this.categoryList = categories;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BudgetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_budget, parent, false);
        return new BudgetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BudgetViewHolder holder, int position) {
        Budget budget = budgetList.get(position);

        // Find category name
        String categoryName = "Unknown";
        if (categoryList != null) {
            for (Category c : categoryList) {
                if (c.getId() == budget.getCategoryId()) {
                    categoryName = c.getName();
                    break;
                }
            }
        }

        // --- 1. Set Date Range ---
        String startDate = dateFormat.format(new Date(budget.getStartDate()));
        String endDate = dateFormat.format(new Date(budget.getEndDate()));
        holder.tvDateRange.setText(String.format("%s - %s", startDate, endDate));


        // Calculate spent amount from transactions
        double spent = 0;
        if (transactionList != null) {
            for (Transaction t : transactionList) {
                // UPDATED: Use t.getDateTime() instead of t.getTimestamp()
                // Check if transaction category matches budget category AND the transaction date is within the budget period
                if (t.getCategoryId() == budget.getCategoryId()
                        && t.getType().equalsIgnoreCase("expense")
                        && t.getDateTime() >= budget.getStartDate()
                        && t.getDateTime() <= budget.getEndDate()) {
                    spent += t.getAmount();
                }
            }
        }

        double budgetAmount = budget.getBudgetedAmount();
        // Recalculate percent and progressPercentage
        double progressPercentage = (spent / budgetAmount) * 100;

        int progressInt = (int) progressPercentage;
        if (progressInt > 100) progressInt = 100; // Cap visual progress at 100%

        holder.tvCategory.setText(categoryName);

        // Set Budget Details
        holder.tvBudgetDetails.setText(String.format(Locale.getDefault(), "Spent: Rs.%.0f / Rs.%.0f", spent, budgetAmount));

        // Set Percentage
        holder.tvProgressPercentage.setText(String.format(Locale.getDefault(), "%.0f%%", progressPercentage));


        // Animate progress bar (uses progressInt capped at 100)
        ObjectAnimator animation = ObjectAnimator.ofInt(holder.progressBudget, "progress", 0, progressInt);
        animation.setDuration(1000);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.start();

        // Color change based on progress
        if (progressPercentage >= 100) {
            // 🔴 Budget exceeded
            holder.progressBudget.setIndicatorColor(ContextCompat.getColor(context, R.color.red));
        } else if (progressPercentage >= 75) {
            // 🟠 High usage, nearing limit (75% to 99%)
            holder.progressBudget.setIndicatorColor(ContextCompat.getColor(context, R.color.red_orange));
        } else if (progressPercentage >= 50) {
            // 🟡 Moderate usage (50% to 74%)
            holder.progressBudget.setIndicatorColor(ContextCompat.getColor(context, R.color.yellow));
        } else {
            // 🟢 Low usage (Under 50%)
            holder.progressBudget.setIndicatorColor(ContextCompat.getColor(context, R.color.teal_700));
        }


        // Set click listeners for edit and delete
        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(budget);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(budget);
        });

    }

    @Override
    public int getItemCount() {
        return budgetList == null ? 0 : budgetList.size();
    }

    static class BudgetViewHolder extends RecyclerView.ViewHolder {
        // --- Added tvDateRange ---
        TextView tvCategory, tvDateRange, tvBudgetDetails, tvProgressPercentage;
        LinearProgressIndicator progressBudget;
        ImageButton btnEdit, btnDelete;


        public BudgetViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            // --- Initialized tvDateRange ---
            tvDateRange = itemView.findViewById(R.id.tvDateRange);
            tvBudgetDetails = itemView.findViewById(R.id.tvBudgetDetails);
            progressBudget = itemView.findViewById(R.id.progressBudget);
            tvProgressPercentage = itemView.findViewById(R.id.tvProgressPercentage);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}