package com.example.finix.ui.savings;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finix.R;
import com.example.finix.data.FinixDatabase;
import com.example.finix.data.SavingsGoal;
import com.example.finix.data.Transaction;
import com.example.finix.data.TransactionDao;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class SavingsGoalsAdapter extends ListAdapter<SavingsGoal, SavingsGoalsAdapter.VH> {

    private final Function<Integer, String> categoryNameResolver;
    private final OnGoalActionListener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface OnGoalActionListener {
        void onEdit(SavingsGoal goal);
        void onDelete(SavingsGoal goal);
    }

    public SavingsGoalsAdapter(Function<Integer, String> categoryNameResolver, OnGoalActionListener listener) {
        super(DIFF);
        this.categoryNameResolver = categoryNameResolver;
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<SavingsGoal> DIFF =
            new DiffUtil.ItemCallback<SavingsGoal>() {
                @Override
                public boolean areItemsTheSame(@NonNull SavingsGoal a, @NonNull SavingsGoal b) {
                    return a.getId() == b.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull SavingsGoal a, @NonNull SavingsGoal b) {
                    return a.getCategoryId() == b.getCategoryId()
                            && eq(a.getGoalName(), b.getGoalName())
                            && eq(a.getGoalDescription(), b.getGoalDescription())
                            && a.getTargetAmount() == b.getTargetAmount()
                            && a.getTargetDate() == b.getTargetDate();
                }

                private boolean eq(String x, String y) {
                    return x == null ? y == null : x.equals(y);
                }
            };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_goal, parent, false);

        // add some vertical spacing between cards
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        params.setMargins(0, 0, 0, (int) (9 * parent.getContext().getResources().getDisplayMetrics().density));
        v.setLayoutParams(params);

        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SavingsGoal g = getItem(position);
        Context context = h.itemView.getContext();

        String catName = categoryNameResolver.apply(g.getCategoryId());
        h.tvGoalName.setText(g.getGoalName());
        h.tvCategory.setText(catName);
        h.tvAmount.setText(String.format(Locale.getDefault(), "Rs. %,.0f", g.getTargetAmount()));

        String date = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(new Date(g.getTargetDate()));
        h.tvDate.setText(date);

        // --- Info dialog ---
        h.btnInfo.setOnClickListener(v -> {
            String msg = (g.getGoalDescription() == null || g.getGoalDescription().trim().isEmpty())
                    ? "No description"
                    : g.getGoalDescription();
            AlertDialog dialog = new AlertDialog.Builder(v.getContext())
                    .setTitle("Description")
                    .setMessage(msg)
                    .setPositiveButton("OK", (d, which) -> d.dismiss())
                    .create();
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(ContextCompat.getColor(v.getContext(), R.color.teal_200));
        });

        // --- Edit ---
        h.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(g);
        });

        // --- Delete ---
        h.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(g);
        });

        // --- Progress calculation (safe background thread) ---
        executor.execute(() -> {
            TransactionDao dao = FinixDatabase.getDatabase(context).transactionDao();

            // Get all incomes and expenses
            List<Transaction> incomes = dao.getTransactionsByType("income");
            List<Transaction> expenses = dao.getTransactionsByType("expense");

            double totalIncome = 0;
            double totalExpense = 0;

            for (Transaction t : incomes) totalIncome += t.getAmount();
            for (Transaction t : expenses) totalExpense += t.getAmount();

            double saved = totalIncome - totalExpense;
            double target = g.getTargetAmount();
            double progressPercentage = (target > 0) ? ((saved / target) * 100) : 0;
            if (progressPercentage > 100) progressPercentage = 100;

            double finalProgress = progressPercentage;
            ((Activity) context).runOnUiThread(() -> {
                h.tvProgressPercentage.setText(
                        String.format(Locale.getDefault(), "%.0f%%", finalProgress)
                );

                ObjectAnimator anim = ObjectAnimator.ofInt(h.progressGoal, "progress", 0, (int) finalProgress);
                anim.setDuration(1000);
                anim.start();
            });
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvGoalName, tvCategory, tvAmount, tvDate, tvProgressPercentage;
        ImageButton btnInfo, btnEdit, btnDelete;
        LinearProgressIndicator progressGoal;

        VH(@NonNull View v) {
            super(v);
            tvGoalName = v.findViewById(R.id.tvGoalName);
            tvCategory = v.findViewById(R.id.tvCategory);
            tvAmount = v.findViewById(R.id.tvAmount);
            tvDate = v.findViewById(R.id.tvDate);
            btnInfo = v.findViewById(R.id.btnInfo);
            btnEdit = v.findViewById(R.id.btnEditGoal);
            btnDelete = v.findViewById(R.id.btnDeleteGoal);
            progressGoal = v.findViewById(R.id.progressGoal);
            tvProgressPercentage = v.findViewById(R.id.tvProgressPercentage);
        }
    }
}
