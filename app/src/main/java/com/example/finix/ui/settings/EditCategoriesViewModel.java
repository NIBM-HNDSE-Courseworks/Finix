package com.example.finix.ui.settings;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.finix.data.Category;
import com.example.finix.data.CategoryDAO;
import com.example.finix.data.FinixDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class EditCategoriesViewModel extends AndroidViewModel {

    private final CategoryDAO categoryDAO;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<Category> fullCategoryList = new ArrayList<>();

    // LiveData to hold the list of categories for the fragment to observe
    private final MutableLiveData<List<Category>> categoriesLive = new MutableLiveData<>();

    // LiveData to send one-time messages (like toasts or errors) to the fragment
    // We use a "Event" wrapper to make sure messages are only shown once.
    private final MutableLiveData<Event<String>> messageEvent = new MutableLiveData<>();

    private final MutableLiveData<Event<UndoPayload>> showUndoDeleteEvent = new MutableLiveData<>();

    public EditCategoriesViewModel(@NonNull Application application) {
        super(application);
        // Get the database DAO instance
        FinixDatabase db = FinixDatabase.getDatabase(application);
        categoryDAO = db.categoryDao();
    }

    public static class UndoPayload {
        public final Category category;
        public final int index;

        public UndoPayload(Category category, int index) {
            this.category = category;
            this.index = index;
        }
    }

    // Public getters for the Fragment to observe
    public LiveData<List<Category>> getCategoriesLive() {
        return categoriesLive;
    }

    public LiveData<Event<String>> getMessageEvent() {
        return messageEvent;
    }

    public LiveData<Event<UndoPayload>> getShowUndoDeleteEvent() {
        return showUndoDeleteEvent;
    }


    /**
     * Loads all categories from the database and posts them to the LiveData.
     */
    public void loadCategories() {
        executor.execute(() -> {
            List<Category> categories = categoryDAO.getAllCategories();
            // Store in our local "true" list
            fullCategoryList = new ArrayList<>(categories);
            // Post a *copy* to the LiveData
            categoriesLive.postValue(new ArrayList<>(fullCategoryList));
            categoriesLive.postValue(categories);
        });
    }

    /**
     * Searches for categories by name and posts the results.
     * If the query is empty, it loads all categories.
     * If no results are found, it posts a "NO_RESULTS" message.
     */
    public void searchCategories(String query) {
        if (query == null || query.trim().isEmpty()) {
            loadCategories(); // Load all if search is empty
            return;
        }

        String searchQuery = "%" + query.trim() + "%";
        executor.execute(() -> {
            List<Category> searchResults = categoryDAO.searchCategories(searchQuery);

            if (searchResults.isEmpty()) {
                // Post a special message for the fragment to handle
                messageEvent.postValue(new Event<>("NO_RESULTS:" + query.trim()));
            }

            // Post the results (even if empty) to update the list
            categoriesLive.postValue(searchResults);
        });
    }

    /**
     * Validates and adds a new category.
     * Posts success or error messages to the messageEvent LiveData.
     * Calls the onSuccess runnable (to dismiss the dialog) from the Fragment.
     */
    public void addCategory(String categoryName, Runnable onSuccess, Consumer<String> onFailure) {
        // --- 1. Validation ---
        if (categoryName == null || categoryName.trim().isEmpty()) {
            handler.post(() -> onFailure.accept("Please fill the category name"));
            messageEvent.postValue(new Event<>("ERROR:Category Name cannot be empty"));
            return;
        }

        String trimmedName = categoryName.trim();

        executor.execute(() -> {
            // --- 2. Check for Duplicates ---
            Category existingCategory = categoryDAO.getCategoryByName(trimmedName);
            if (existingCategory != null) {
                // Duplicate found
                messageEvent.postValue(new Event<>("ERROR:A category with this name already exists"));
                String duplicateErrorMessage = "Please fill the new category name";
                handler.post(() -> onFailure.accept(duplicateErrorMessage));
            } else {
                // --- 3. Save to Database ---
                Category newCategory = new Category(trimmedName);
                categoryDAO.insert(newCategory);

                // --- 4. Report Success and Refresh ---
                messageEvent.postValue(new Event<>("SUCCESS: This category added successfully"));
                loadCategories(); // Refresh the list

                // Run the success callback (e.g., dismiss dialog) on the main thread
                handler.post(onSuccess);
            }
        });
    }

    /**
     * Validates and updates an existing category.
     */
    public void updateCategory(Category categoryToUpdate, String newName, Runnable onSuccess, Consumer<String> onFailure) {
        // --- 1. Validation ---
        if (newName == null || newName.trim().isEmpty()) {
            handler.post(() -> onFailure.accept("Please fill the edit category name"));
            messageEvent.postValue(new Event<>("ERROR:Category Name cannot be empty"));
            return;
        }

        String trimmedName = newName.trim();

        if (trimmedName.equalsIgnoreCase(categoryToUpdate.getName())) {
            handler.post(() -> onFailure.accept("Please edit the category name"));
            messageEvent.postValue(new Event<>("ERROR:Please enter a new name to update."));
            return;
        }

        executor.execute(() -> {
            // --- 2. Check for Duplicates (that aren't this category) ---
            Category existingCategory = categoryDAO.getCategoryByName(trimmedName);
            if (existingCategory != null && existingCategory.getId() != categoryToUpdate.getId()) {
                // Duplicate found (and it's a different item)
                String duplicateErrorMessage = "Please edit the category name";
                handler.post(() -> onFailure.accept(duplicateErrorMessage));
                messageEvent.postValue(new Event<>("ERROR:A category with this name already exists"));
            } else {
                // --- 3. Update in Database ---
                categoryToUpdate.setName(trimmedName);
                categoryDAO.update(categoryToUpdate);

                // --- 4. Report Success and Refresh ---
                messageEvent.postValue(new Event<>("SUCCESS: This category updated successfully"));
                loadCategories(); // Refresh the list

                // Run the success callback on the main thread
                handler.post(onSuccess);
            }
        });
    }

    /**
     * Deletes a category from the database.
     */
    public void deleteCategory(Category category) {
        // Find the category's current position
        int index = fullCategoryList.indexOf(category);
        if (index == -1) {
            // Not found, maybe already deleted
            return;
        }

        // 1. Remove from the local list
        fullCategoryList.remove(index);

        // 2. Post the updated (smaller) list to the UI
        categoriesLive.postValue(new ArrayList<>(fullCategoryList));

        // 3. Post an event to the Fragment to show the Undo Snackbar
        showUndoDeleteEvent.postValue(new Event<>(new UndoPayload(category, index)));
    }

    public void undoDelete(UndoPayload payload) {
        if (payload == null) return;

        // Add the category back to our "true" list at its original position
        if (payload.index >= 0 && payload.index <= fullCategoryList.size()) {
            fullCategoryList.add(payload.index, payload.category);
        } else {
            // Fallback, just add to the end
            fullCategoryList.add(payload.category);
        }

        // Post the restored list to the UI
        categoriesLive.postValue(new ArrayList<>(fullCategoryList));
    }

    public void finalizeDelete(Category category) {
        executor.execute(() -> {
            // Now we permanently delete from the database
            categoryDAO.delete(category);

            // Report success
            messageEvent.postValue(new Event<>("SUCCESS:This category deleted successfully"));
            // We don't need to call loadCategories() because the UI list is already correct.
        });
    }

    /**
     * A wrapper class for LiveData events to ensure they are only
     * handled once (e.g., for showing a Toast).
     */
    public static class Event<T> {
        private final T content;
        private boolean hasBeenHandled = false;

        public Event(T content) {
            this.content = content;
        }

        public T getContentIfNotHandled() {
            if (hasBeenHandled) {
                return null;
            } else {
                hasBeenHandled = true;
                return content;
            }
        }

        public T peekContent() {
            return content;
        }
    }
}