package com.example.finix.data;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

/**
 * Retrofit Interface for Budget-related API calls.
 * All endpoints assume the ORDS base path: "finix/api/budgets/"
 */
public interface BudgetService {

    /**
     * Get all budgets from server.
     */
    @GET("finix/api/budgets/")
    Call<List<Budget>> getAllBudgets();

    /**
     * Create a new budget on the server (PENDING).
     * Uses ResponseBody to allow raw JSON logging and avoid EOFException.
     */
    @POST("finix/api/budgets/")
    Call<ResponseBody> createBudget(@Body Budget budget);

    /**
     * Update an existing budget on the server (UPDATED).
     * @param id The server ID of the budget.
     * @param budget The updated budget object.
     * @return Call with ResponseBody for logging.
     */
    @PUT("finix/api/budgets/{id}")
    Call<ResponseBody> updateBudget(
            @Path("id") int id,
            @Body Budget budget
    );

    /**
     * Delete a budget from the server (DELETED).
     * @param id The server ID of the budget to delete.
     * @return Call with ResponseBody for logging.
     */
    @DELETE("finix/api/budgets/{id}")
    Call<ResponseBody> deleteBudget(@Path("id") int id);
}