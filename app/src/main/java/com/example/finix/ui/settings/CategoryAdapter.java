package com.example.finix.ui.settings;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.finix.R;
import com.example.finix.data.Category;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private static final String LOG_TAG = "CategoryAdapter";

    private List<Category> categories;
    private final OnCategoryClickListener listener;

    // Interface to handle clicks
    public interface OnCategoryClickListener {
        void onEditClick(Category category);
        void onDeleteClick(Category category);
    }

    public CategoryAdapter(List<Category> categories, OnCategoryClickListener listener) {
        this.categories = categories;
        this.listener = listener;
        Log.d(LOG_TAG, "Adapter initialized with " + categories.size() + " categories.");
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d(LOG_TAG, "Creating new ViewHolder.");
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_categories, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categories.get(position);
        Log.v(LOG_TAG, "Binding category at position " + position + ": " + category.getName() + " (ID: " + category.getId() + ")");
        holder.bind(category, listener);
    }

    @Override
    public int getItemCount() {
        // Defensive check for null list
        return categories != null ? categories.size() : 0;
    }

    // Method to update the list
    public void setCategories(List<Category> newCategories) {
        int oldSize = this.categories != null ? this.categories.size() : 0;
        this.categories = newCategories;
        Log.i(LOG_TAG, "Data set updated. Old size: " + oldSize + ", New size: " + (newCategories != null ? newCategories.size() : 0) + ". Notifying data set changed.");
        notifyDataSetChanged();
    }

    // ViewHolder class
    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategory;
        ImageButton btnEdit, btnDelete;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            // DEBUG: Log the view holder creation
            Log.v(LOG_TAG, "ViewHolder created for item view.");
            tvCategory = itemView.findViewById(R.id.tvCategory);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        public void bind(final Category category, final OnCategoryClickListener listener) {
            tvCategory.setText(category.getName());

            // Add logs to button clicks for debugging user interaction
            btnEdit.setOnClickListener(v -> {
                Log.d(LOG_TAG, "Edit clicked for category: " + category.getName() + " (ID: " + category.getId() + ")");
                listener.onEditClick(category);
            });
            btnDelete.setOnClickListener(v -> {
                Log.d(LOG_TAG, "Delete clicked for category: " + category.getName() + " (ID: " + category.getId() + ")");
                listener.onDeleteClick(category);
            });
        }
    }
}
