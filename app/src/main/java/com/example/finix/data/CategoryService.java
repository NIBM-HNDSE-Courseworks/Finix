package com.example.finix.data;

import java.util.List;

import okhttp3.ResponseBody; // Import required for the fix
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

/**
 * Retrofit Interface for Category-related API calls.
 */
public interface CategoryService {

    // --- All paths now use the correct ORDS mapping: "finix/api/..." ---

    /**
     * Retrieves all categories from the server.
     */
    @GET("finix/api/categories/")
    Call<List<Category>> getAllCategories();

    /**
     * Uploads a new category to the server (PENDING status).
     * We use Call<ResponseBody> to manually check for empty or malformed body,
     * which prevents EOFException on server success with no content.
     * * @param category The Category object to be created.
     * @return A Call object containing the raw ResponseBody.
     */
    @POST("finix/api/categories/")
    Call<ResponseBody> createCategory(@Body Category category); // CHANGED RETURN TYPE

    /**
     * Updates an existing category on the server (UPDATED status).
     */
    @PUT("finix/api/categories/{id}")
    Call<Category> updateCategory(
            @Path("id") int id,
            @Body Category category
    );

    /**
     * Deletes a category from the server (DELETED status).
     */
    @DELETE("finix/api/categories/{id}")
    Call<Void> deleteCategory(@Path("id") int id);
}