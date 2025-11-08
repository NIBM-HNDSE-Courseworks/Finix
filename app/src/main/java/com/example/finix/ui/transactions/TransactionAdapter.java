package com.example.finix.ui.transactions;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil; // 💡 NEW IMPORT
import androidx.recyclerview.widget.RecyclerView;
import com.example.finix.R;
import com.example.finix.data.Transaction;
import java.text.SimpleDateFormat;
import java.util.*;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    // 💡 IMPORTANT: Keep this list non-final so you can clear/repopulate it effectively
    private final List<Transaction> list = new ArrayList<>();

    private Map<Integer, String> categoryMap = new HashMap<>();
    private OnTransactionActionListener listener;

    public interface OnTransactionActionListener {
        void onEdit(Transaction transaction);
        void onDelete(Transaction transaction);
    }

    public void setListener(OnTransactionActionListener listener) {
        this.listener = listener;
    }

    public void setCategoryMap(Map<Integer, String> map) {
        this.categoryMap = map;
        // NOTE: We don't call notifyDataSetChanged here. The changes will be applied
        // when setTransactions is called next, or if you need the *content* to update
        // without transaction list change, you'd call notifyDataSetChanged() here.
    }

    /**
     * 💡 FIX: Use DiffUtil for smooth updates instead of notifyDataSetChanged().
     */
    public void setTransactions(List<Transaction> transactions) {
        if (this.list.isEmpty()) {
            // Case 1: Initial load
            this.list.addAll(transactions);
            notifyDataSetChanged();
        } else {
            // Case 2: Update/Filter - Use DiffUtil for smooth animation
            TransactionDiffCallback diffCallback = new TransactionDiffCallback(this.list, transactions);
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

            // Update the list by clearing the old and adding the new
            this.list.clear();
            this.list.addAll(transactions);

            // Dispatch the minimal updates, enabling smooth animations (insert/remove/move)
            diffResult.dispatchUpdatesTo(this);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        Transaction t = list.get(pos);

        // 1. Get Category Name
        String categoryName = categoryMap.getOrDefault(t.getCategoryId(), "Unknown Category");

        // 2. Format Amount String (e.g., "Rs. 9,999,999")
        String amountString = String.format(Locale.getDefault(), "Rs. %,.0f", t.getAmount());

        // 3. Define the conditions
        boolean isCategoryTooLong = categoryName.length() > 11;
        boolean isAmountVeryLarge = amountString.length() == 12 || amountString.length() == 13;

        // 4. Apply character limit logic
        if (isCategoryTooLong && isAmountVeryLarge) {
            String limitedCategoryName = categoryName.substring(0, 7) + "...";
            h.tvCategory.setText(limitedCategoryName);
        } else {
            h.tvCategory.setText(categoryName);
        }

        h.tvDescription.setText(t.getDescription());
        h.tvAmount.setText(amountString);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        h.tvDescription.append("\n" + sdf.format(new Date(t.getDateTime())));

        // 👇 Edit and delete buttons
        h.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(t);
        });
        h.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(t);
        });
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategory, tvAmount, tvDescription;
        ImageButton btnEdit, btnDelete;

        ViewHolder(@NonNull View item) {
            super(item);
            tvCategory = item.findViewById(R.id.tvCategory);
            tvAmount = item.findViewById(R.id.tvAmount);
            tvDescription = item.findViewById(R.id.tvDescription);
            btnEdit = item.findViewById(R.id.btnEdit);
            btnDelete = item.findViewById(R.id.btnDelete);
        }
    }

    // 💡 NEW: DiffUtil Implementation for smooth animations
    private static class TransactionDiffCallback extends DiffUtil.Callback {
        private final List<Transaction> oldList;
        private final List<Transaction> newList;

        public TransactionDiffCallback(List<Transaction> oldList, List<Transaction> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            // Compare by a unique ID (assuming Transaction has getLocalId() or similar)
            return oldList.get(oldItemPosition).getLocalId() == newList.get(newItemPosition).getLocalId();
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            // Compare all fields that affect the display
            Transaction oldT = oldList.get(oldItemPosition);
            Transaction newT = newList.get(newItemPosition);

            return oldT.getLocalId() == newT.getLocalId() &&
                    oldT.getAmount() == newT.getAmount() &&
                    oldT.getCategoryId() == newT.getCategoryId() &&
                    oldT.getDateTime() == newT.getDateTime() &&
                    Objects.equals(oldT.getDescription(), newT.getDescription()) &&
                    Objects.equals(oldT.getType(), newT.getType());
        }
    }
}