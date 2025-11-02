package com.example.finix.ui.savings;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
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
import com.example.finix.data.SavingsGoal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Function;

public class SavingsGoalsAdapter extends ListAdapter<SavingsGoal, SavingsGoalsAdapter.VH> {

    private final Function<Integer, String> categoryNameResolver;
    private final OnGoalActionListener listener;

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

        // 🧩 Add spacing between cards (16dp)
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        params.setMargins(0, 0, 0, (int) (9 * parent.getContext().getResources().getDisplayMetrics().density));
        v.setLayoutParams(params);

        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SavingsGoal g = getItem(position);

        String catName = categoryNameResolver.apply(g.getCategoryId());
        h.tvGoalName.setText(g.getGoalName());
        h.tvCategory.setText(catName);
        h.tvAmount.setText(String.format(Locale.getDefault(), "Rs. %,.0f", g.getTargetAmount()));

        String date = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(new Date(g.getTargetDate()));
        h.tvDate.setText(date);

        // 💬 Info Dialog (OK button teal_200)
        h.btnInfo.setOnClickListener(v -> {
            String msg = (g.getGoalDescription() == null || g.getGoalDescription().trim().isEmpty())
                    ? "No description"
                    : g.getGoalDescription();

            AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
            builder.setTitle("Description");
            builder.setMessage(msg);
            builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
            AlertDialog dialog = builder.create();
            dialog.show();

            // 💚 Set OK button color to teal_200
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(ContextCompat.getColor(v.getContext(), R.color.teal_200));
        });

        // ✏️ Edit
        h.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(g);
        });

        // ❌ Delete
        h.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(g);
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvGoalName, tvCategory, tvAmount, tvDate;
        ImageButton btnInfo, btnEdit, btnDelete;

        VH(@NonNull View v) {
            super(v);
            tvGoalName = v.findViewById(R.id.tvGoalName);
            tvCategory = v.findViewById(R.id.tvCategory);
            tvAmount = v.findViewById(R.id.tvAmount);
            tvDate = v.findViewById(R.id.tvDate);
            btnInfo = v.findViewById(R.id.btnInfo);
            btnEdit = v.findViewById(R.id.btnEditGoal);
            btnDelete = v.findViewById(R.id.btnDeleteGoal);
        }
    }
}
