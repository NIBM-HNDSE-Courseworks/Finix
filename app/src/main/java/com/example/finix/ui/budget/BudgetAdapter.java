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

import java.util.List;
import java.util.Locale;

public class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.BudgetViewHolder> {

    private List<Budget> budgetList;
    private final Context context;
    private List<Transaction> transactionList;
    private List<Category> categoryList;
    private final OnBudgetActionListener listener;


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

        // Calculate spent amount from transactions
        double spent = 0;
        if (transactionList != null) {
            for (Transaction t : transactionList) {
                if (t.getCategoryId() == budget.getCategoryId() && t.getType().equalsIgnoreCase("expense")) {
                    spent += t.getAmount();
                }
            }
        }

        double budgetAmount = budget.getBudgetedAmount();
        int percent = (int) ((spent / budgetAmount) * 100);
        double budgeted = budget.getBudgetedAmount();
        double progressPercentage = (spent / budgeted) * 100;

        if (percent > 100) percent = 100;

        holder.tvCategory.setText(categoryName);
        holder.tvBudgetDetails.setText(String.format("Spent: Rs.%.2f / $%.2f", spent, budgetAmount));
        holder.tvProgressPercentage.setText(String.format(Locale.getDefault(), "%.0f%%", progressPercentage));

        // Animate progress bar
        ObjectAnimator animation = ObjectAnimator.ofInt(holder.progressBudget, "progress", 0, (int) progressPercentage);
        animation.setDuration(1000);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.start();

        // Color change as user nears limit
        if (progressPercentage >= 90) {
            holder.progressBudget.setIndicatorColor(ContextCompat.getColor(context, R.color.red));
        } else if (progressPercentage >= 50) {
            holder.progressBudget.setIndicatorColor(ContextCompat.getColor(context, R.color.yellow));
        } else {
            holder.progressBudget.setIndicatorColor(ContextCompat.getColor(context, R.color.teal_700));
        }


        // Set click listeners for edit and delete
        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(budget); // <-- Edit
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(budget); // <-- Delete
        });

    }

    @Override
    public int getItemCount() {
        return budgetList == null ? 0 : budgetList.size();
    }

    static class BudgetViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategory, tvBudgetDetails, tvProgressPercentage;
        LinearProgressIndicator progressBudget;
        ImageButton btnEdit, btnDelete;


        public BudgetViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvBudgetDetails = itemView.findViewById(R.id.tvBudgetDetails);
            progressBudget = itemView.findViewById(R.id.progressBudget);
            tvProgressPercentage = itemView.findViewById(R.id.tvProgressPercentage);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }

}
