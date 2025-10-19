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

        // 💡 UPDATED LINE: Resolve category ID to name using the map
        String categoryName = categoryMap.getOrDefault(t.getCategoryId(), "Unknown Category");
        h.tvCategory.setText(categoryName);

        h.tvDescription.setText(t.getDescription());
        h.tvAmount.setText(String.format(Locale.getDefault(), "%.2f", t.getAmount()));

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