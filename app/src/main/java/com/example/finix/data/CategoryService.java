package com.example.finix.data;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

/**
 * Retrofit Interface for Category-related API calls.
 * All operations are expected to work with the 'Category' data model.
 * NOTE: Paths are updated to align with ORDS schema (finix) and module (api) base paths.
 */
public interface CategoryService {

    // --- All paths now use the correct ORDS mapping: "finix/api/..." ---

    /**
     * Retrieves all categories from the server.
     */
    @GET("finix/api/categories/") // CORRECTED: Added 'api/'
    Call<List<Category>> getAllCategories();

    /**
     * Uploads a new category to the server (corresponds to local 'PENDING' status).
     * @param category The Category object to be created.
     * @return A Call object containing the newly created Category (which should include
     * the server-assigned ID and timestamp).
     */
    @POST("finix/api/categories/") // CORRECTED: Added 'api/'
    Call<Category> createCategory(@Body Category category);

    /**
     * Updates an existing category on the server (corresponds to local 'UPDATED' status).
     * @param id The ID of the category to update.
     * @param category The Category object with updated fields.
     * @return A Call object containing the updated Category.
     */
    @PUT("finix/api/categories/{id}") // CORRECTED: Added 'api/'
    Call<Category> updateCategory(
            @Path("id") int id,
            @Body Category category
    );

    /**
     * Deletes a category from the server (corresponds to local 'DELETED' status).
     * @param id The ID of the category to delete.
     * @return A Call object for the response. Void is common for successful DELETE.
     */
    @DELETE("finix/api/categories/{id}") // CORRECTED: Added 'api/'
    Call<Void> deleteCategory(@Path("id") int id);
}