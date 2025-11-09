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
 * Retrofit Interface for SavingsGoal-related API calls.
 * All endpoints assume the ORDS base path: "finix/api/savings_goals/"
 */
public interface SavingsGoalService {

    /**
     * Get all savings goals from server.
     */
    @GET("finix/api/savings_goals/")
    Call<List<SavingsGoal>> getAllSavingsGoals();

    /**
     * Create a new savings goal on the server (PENDING).
     * Uses ResponseBody to allow raw JSON logging and avoid EOFException.
     */
    @POST("finix/api/savings_goals/")
    Call<ResponseBody> createSavingsGoal(@Body SavingsGoal savingsGoal);

    /**
     * Update an existing savings goal on the server (UPDATED).
     * @param id The server ID of the savings goal.
     * @param savingsGoal The updated savings goal object.
     * @return Call with ResponseBody for logging.
     */
    @PUT("finix/api/savings_goals/{id}")
    Call<ResponseBody> updateSavingsGoal(
            @Path("id") int id,
            @Body SavingsGoal savingsGoal
    );

    /**
     * Delete a savings goal from the server (DELETED).
     * @param id The server ID of the savings goal to delete.
     * @return Call with ResponseBody for logging.
     */
    @DELETE("finix/api/savings_goals/{id}")
    Call<ResponseBody> deleteSavingsGoal(@Path("id") int id);
}
