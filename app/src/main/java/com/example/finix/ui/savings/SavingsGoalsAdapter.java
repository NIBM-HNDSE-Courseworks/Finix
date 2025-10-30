package com.example.finix.ui.savings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finix.R;
import com.example.finix.data.SavingsGoal;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Function;

public class SavingsGoalsAdapter
        extends ListAdapter<SavingsGoal, SavingsGoalsAdapter.VH> {

    // Resolve categoryId -> categoryName (provided by Fragment)
    private final Function<Integer, String> categoryNameResolver;

    public SavingsGoalsAdapter(Function<Integer, String> categoryNameResolver) {
        super(DIFF);
        this.categoryNameResolver = categoryNameResolver;
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

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_goal, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SavingsGoal g = getItem(position);

        String catName = categoryNameResolver.apply(g.getCategoryId());
        h.tvGoalName.setText(g.getGoalName());
        h.tvCategory.setText(catName);

        // second row
        h.tvAmount.setText(String.format(Locale.getDefault(), "Rs. %,.2f", g.getTargetAmount()));
        String date = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(new Date(g.getTargetDate()));
        h.tvDate.setText(date);

        // info icon: show description
        h.btnInfo.setOnClickListener(v -> {
            String msg = (g.getGoalDescription() == null || g.getGoalDescription().trim().isEmpty())
                    ? "No description"
                    : g.getGoalDescription();
            Toast.makeText(v.getContext(), msg, Toast.LENGTH_LONG).show();
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvGoalName, tvCategory, tvAmount, tvDate;
        ImageButton btnInfo;

        VH(@NonNull View v) {
            super(v);
            tvGoalName = v.findViewById(R.id.tvGoalName);
            tvCategory = v.findViewById(R.id.tvCategory);
            tvAmount   = v.findViewById(R.id.tvAmount);
            tvDate     = v.findViewById(R.id.tvDate);
            btnInfo    = v.findViewById(R.id.btnInfo);
        }
    }
}
