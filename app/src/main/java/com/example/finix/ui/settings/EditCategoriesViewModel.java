package com.example.finix.ui.settings;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log; // ADDED: Import Log

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.finix.data.Category;
import com.example.finix.data.CategoryDAO;
import com.example.finix.data.FinixDatabase;
import com.example.finix.data.SynchronizationLog;
import com.example.finix.data.SynchronizationLogDAO;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class EditCategoriesViewModel extends AndroidViewModel {

    private static final String LOG_TAG = "EditCategoriesVM"; // ADDED: Log Tag

    private final CategoryDAO categoryDAO;
    private final SynchronizationLogDAO syncLogDAO;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<Category> fullCategoryList = new ArrayList<>();

    // LiveData to hold the list of categories for the fragment to observe
    private final MutableLiveData<List<Category>> categoriesLive = new MutableLiveData<>();

    // LiveData to send one-time messages (like toasts or errors) to the fragment
    private final MutableLiveData<Event<String>> messageEvent = new MutableLiveData<>();

    private final MutableLiveData<Event<UndoPayload>> showUndoDeleteEvent = new MutableLiveData<>();

    public EditCategoriesViewModel(@NonNull Application application) {
        super(application);
        // Get the database DAO instance
        FinixDatabase db = FinixDatabase.getDatabase(application);
        categoryDAO = db.categoryDao();
        syncLogDAO = db.synchronizationLogDao();
        Log.i(LOG_TAG, "ViewModel initialized. DAOs and Executor ready.");
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
        Log.d(LOG_TAG, "Starting to load all categories from DB.");
        executor.execute(() -> {
            List<Category> categories = categoryDAO.getAllCategories();
            Log.d(LOG_TAG, "DB load complete. Found " + categories.size() + " categories.");
            // Store in our local "true" list
            fullCategoryList = new ArrayList<>(categories);
            // Post a *copy* to the LiveData
            categoriesLive.postValue(new ArrayList<>(fullCategoryList));
            Log.v(LOG_TAG, "Categories posted to LiveData and local list updated.");
        });
    }

    /**
     * Searches for categories by name and posts the results.
     * If the query is empty, it loads all categories.
     * If no results are found, it posts a "NO_RESULTS" message.
     */
    public void searchCategories(String query) {
        if (query == null || query.trim().isEmpty()) {
            Log.d(LOG_TAG, "Search query is empty/null. Reloading all categories.");
            loadCategories(); // Load all if search is empty
            return;
        }

        String searchQuery = "%" + query.trim() + "%";
        Log.d(LOG_TAG, "Starting search for query: '" + query.trim() + "'. SQL pattern: " + searchQuery);
        executor.execute(() -> {
            List<Category> searchResults = categoryDAO.searchCategories(searchQuery);

            if (searchResults.isEmpty()) {
                Log.w(LOG_TAG, "Search yielded NO_RESULTS for query: " + query.trim());
                // Post a special message for the fragment to handle
                messageEvent.postValue(new Event<>("NO_RESULTS:" + query.trim()));
            } else {
                Log.d(LOG_TAG, "Search complete. Found " + searchResults.size() + " results.");
            }

            // Post the results (even if empty) to update the list
            categoriesLive.postValue(searchResults);
        });
    }

    /**
     * Validates and adds a new category.
     */
    public void addCategory(String categoryName, Runnable onSuccess, Consumer<String> onFailure) {
        Log.d(LOG_TAG, "Attempting to add new category: '" + categoryName + "'");

        // --- 1. Validation ---
        if (categoryName == null || categoryName.trim().isEmpty()) {
            Log.e(LOG_TAG, "Validation failed: Category Name is empty.");
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
                Log.w(LOG_TAG, "Add failed: Duplicate category found: " + trimmedName + " (ID: " + existingCategory.getId() + ")");
                messageEvent.postValue(new Event<>("ERROR:A category with this name already exists"));
                String duplicateErrorMessage = "Please fill the new category name";
                handler.post(() -> onFailure.accept(duplicateErrorMessage));
            } else {
                // --- 3. Save to Database ---
                Category newCategory = new Category(trimmedName);
                long newId = categoryDAO.insert(newCategory);

                Log.i(LOG_TAG, "Category inserted locally: '" + trimmedName + "'. Temp ID generated: " + newId);

                // Log entry uses the new unique ID:
                SynchronizationLog log = new SynchronizationLog(
                        "CATEGORIES",
                        (int) newId, // This is the unique negative ID (e.g., -1)
                        System.currentTimeMillis(),
                        "PENDING" // Will be POSTed in FinixRepository
                );
                syncLogDAO.insert(log);
                Log.d(LOG_TAG, "Sync log created for new insert. Record ID: " + newId);

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
        Log.d(LOG_TAG, "Attempting to update category ID " + categoryToUpdate.getId() + " from '" + categoryToUpdate.getName() + "' to '" + newName + "'");
        // --- 1. Validation ---
        if (newName == null || newName.trim().isEmpty()) {
            Log.e(LOG_TAG, "Update validation failed: New Name is empty.");
            handler.post(() -> onFailure.accept("Please fill the edit category name"));
            messageEvent.postValue(new Event<>("ERROR:Category Name cannot be empty"));
            return;
        }

        String trimmedName = newName.trim();

        if (trimmedName.equalsIgnoreCase(categoryToUpdate.getName())) {
            Log.w(LOG_TAG, "Update validation failed: New Name is same as old name.");
            handler.post(() -> onFailure.accept("Please edit the category name"));
            messageEvent.postValue(new Event<>("ERROR:Please enter a new name to update."));
            return;
        }

        executor.execute(() -> {
            // --- 2. Check for Duplicates (that aren't this category) ---
            Category existingCategory = categoryDAO.getCategoryByName(trimmedName);
            if (existingCategory != null && existingCategory.getId() != categoryToUpdate.getId()) {
                // Duplicate found (and it's a different item)
                Log.w(LOG_TAG, "Update failed: Duplicate name found in different category ID " + existingCategory.getId());
                String duplicateErrorMessage = "Please edit the category name";
                handler.post(() -> onFailure.accept(duplicateErrorMessage));
                messageEvent.postValue(new Event<>("ERROR:A category with this name already exists"));
            } else {
                // --- 3. Update in Database ---
                Log.i(LOG_TAG, "Executing update for category ID " + categoryToUpdate.getId() + ". New Name: " + trimmedName);
                categoryToUpdate.setName(trimmedName);
                categoryDAO.update(categoryToUpdate);

                // --- 3a. Log Synchronization for UPDATE (NEW) ---
                SynchronizationLog log = new SynchronizationLog(
                        "CATEGORIES",
                        categoryToUpdate.getId(),
                        System.currentTimeMillis(),
                        "PENDING" // Re-use PENDING status to mark a change needing sync
                );
                syncLogDAO.insert(log); // Insert a new log entry for the change
                Log.d(LOG_TAG, "Sync log created for update. Record ID: " + categoryToUpdate.getId());

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
     * NOTE: This is the temporary deletion that triggers the UNDO Snackbar.
     */
    public void deleteCategory(Category category) {
        Log.d(LOG_TAG, "Starting temporary delete for category: " + category.getName() + " (ID: " + category.getId() + ")");
        // Find the category's current position
        int index = fullCategoryList.indexOf(category);
        if (index == -1) {
            Log.w(LOG_TAG, "Temporary delete failed: Category not found in list (ID: " + category.getId() + "). Aborting.");
            return;
        }

        // 1. Remove from the local list
        fullCategoryList.remove(index);
        Log.v(LOG_TAG, "Category removed from local list at index: " + index + ". List size: " + fullCategoryList.size());

        // 2. Post the updated (smaller) list to the UI
        categoriesLive.postValue(new ArrayList<>(fullCategoryList));

        // 3. Post an event to the Fragment to show the Undo Snackbar
        showUndoDeleteEvent.postValue(new Event<>(new UndoPayload(category, index)));
        Log.d(LOG_TAG, "Posted UndoPayload event to fragment.");
    }

    public void undoDelete(UndoPayload payload) {
        if (payload == null) {
            Log.e(LOG_TAG, "Undo delete called with null payload. Aborting.");
            return;
        }
        Log.d(LOG_TAG, "Executing undo delete for category: " + payload.category.getName() + " at index " + payload.index);

        // Add the category back to our "true" list at its original position
        if (payload.index >= 0 && payload.index <= fullCategoryList.size()) {
            fullCategoryList.add(payload.index, payload.category);
            Log.v(LOG_TAG, "Category restored to original index: " + payload.index);
        } else {
            // Fallback, just add to the end
            fullCategoryList.add(payload.category);
            Log.w(LOG_TAG, "Category index invalid (" + payload.index + "). Restored to the end of the list.");
        }

        // Post the restored list to the UI
        categoriesLive.postValue(new ArrayList<>(fullCategoryList));
        Log.d(LOG_TAG, "Restored list posted to LiveData. New size: " + fullCategoryList.size());
    }

    /**
     * Finalizes the deletion by permanently removing it from the database and logging the sync.
     */
    public void finalizeDelete(Category category) {
        Log.d(LOG_TAG, "Starting PERMANENT delete for category: " + category.getName() + " (ID: " + category.getId() + ")");
        executor.execute(() -> {
            // 1. Log Synchronization for DELETE (NEW)
            SynchronizationLog deleteLog = new SynchronizationLog(
                    "CATEGORIES",
                    category.getId(),
                    System.currentTimeMillis(),
                    "DELETE_PENDING" // Log that this record needs to be deleted remotely
            );
            syncLogDAO.insert(deleteLog);
            Log.d(LOG_TAG, "Sync log created for permanent delete. Record ID: " + category.getId());

            // 2. Permanently delete from the database
            categoryDAO.delete(category);
            Log.i(LOG_TAG, "Category permanently deleted from Room DB: " + category.getId());

            // 3. Report success
            messageEvent.postValue(new Event<>("SUCCESS:This category deleted successfully"));
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
