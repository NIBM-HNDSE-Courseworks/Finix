package com.example.finix.data;

import com.google.gson.annotations.SerializedName;
import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

/**
 * Retrofit interface for the Oracle REST Data Services (ORDS) 'categories' endpoint.
 * ... (omitted documentation) ...
 */
public interface CategoryService {

    // --- Data Transfer Objects (DTOs) for ORDS Communication ---
    // ... (CategoryListResponse and CategoryNetworkModel remain the same) ...

    class CategoryListResponse {
        @SerializedName("items")
        private List<CategoryNetworkModel> items;

        public List<CategoryNetworkModel> getItems() {
            return items;
        }
    }

    class CategoryNetworkModel {
        @SerializedName("ID") // Matches 'id' column in Oracle
        private Integer id;

        @SerializedName("NAME") // Matches 'name' column in Oracle
        private String name;

        public CategoryNetworkModel() {}

        public CategoryNetworkModel(String name) {
            this.name = name;
        }

        // --- Getters and Setters ---
        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }


    // --- Retrofit API Endpoints ---

    @GET("categories/")
    Call<CategoryListResponse> getAllCategories();

    @POST("categories/")
    Call<CategoryNetworkModel> createCategory(@Body CategoryNetworkModel category);

    /**
     * PUT: Updates an existing category on the server.
     * Endpoint: /ords/finix/categories/{id}
     * @param id The ID of the category to update.
     * @param category The updated category data.
     * @return The updated category.
     */
    @PUT("categories/{id}")
    Call<CategoryNetworkModel> updateCategory(@Path("id") int id, @Body CategoryNetworkModel category); // <-- USED FOR UPDATES

    /**
     * DELETE: Deletes a category from the server.
     * Endpoint: /ords/finix/categories/{id}
     * @param id The ID of the category to delete.
     * @return An empty successful Response (Call<Void>) if deleted.
     */
    @DELETE("categories/{id}")
    Call<Void> deleteCategory(@Path("id") int id);
}