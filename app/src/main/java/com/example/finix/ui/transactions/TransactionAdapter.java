package com.example.finix.ui.transactions;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.finix.R;
import com.example.finix.data.Transaction;
import java.text.SimpleDateFormat;
import java.util.*;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    private final List<Transaction> list = new ArrayList<>();
    // 💡 NEW: Map to store Category ID -> Category Name
    private Map<Integer, String> categoryMap = new HashMap<>();
    private OnTransactionActionListener listener;

    public interface OnTransactionActionListener {
        void onEdit(Transaction transaction);
        void onDelete(Transaction transaction);
    }

    public void setListener(OnTransactionActionListener listener) {
        this.listener = listener;
    }

    // 💡 NEW: Method to update the category map
    public void setCategoryMap(Map<Integer, String> map) {
        this.categoryMap = map;
        // Don't call notifyDataSetChanged here, wait for setTransactions if data changes
    }

    public void setTransactions(List<Transaction> transactions) {
        list.clear();
        list.addAll(transactions);
        notifyDataSetChanged();
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
        // Condition check for the *formatted* amount string length.
        // For "Rs. 9,999,999", the length is 13.
        // For "Rs. 999,999", the length is 11.
        // We want to limit the category when the amount is a *very large* one, which typically results in 12 or 13 characters.
        boolean isAmountVeryLarge = amountString.length() == 12 || amountString.length() == 13;

        // 4. Apply character limit logic
        if (isCategoryTooLong && isAmountVeryLarge) {
            // Limit category name to 7 characters and append "..."
            String limitedCategoryName = categoryName.substring(0, 7) + "...";
            h.tvCategory.setText(limitedCategoryName);
        } else {
            // Use the full category name
            h.tvCategory.setText(categoryName);
        }

        h.tvDescription.setText(t.getDescription());
        h.tvAmount.setText(amountString); // Use the pre-calculated amount string

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
}