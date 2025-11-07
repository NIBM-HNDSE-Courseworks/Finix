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
 * Retrofit Interface for Transaction-related API calls.
 * All endpoints assume the ORDS base path: "finix/api/transactions/"
 */
public interface TransactionService {

    /**
     * Get all transactions from server.
     */
    @GET("finix/api/transactions/")
    Call<List<Transaction>> getAllTransactions();

    /**
     * Create a new transaction on the server (PENDING).
     * Uses ResponseBody to allow raw JSON logging and avoid EOFException.
     */
    @POST("finix/api/transactions/")
    Call<ResponseBody> createTransaction(@Body Transaction transaction);

    /**
     * Update an existing transaction on the server (UPDATED).
     * @param id The server ID of the transaction.
     * @param transaction The updated transaction object.
     * @return Call with ResponseBody for logging.
     */
    @PUT("finix/api/transactions/{id}")
    Call<ResponseBody> updateTransaction(
            @Path("id") int id,
            @Body Transaction transaction
    );

    /**
     * Delete a transaction from the server (DELETED).
     * @param id The server ID of the transaction to delete.
     * @return Call with ResponseBody for logging.
     */
    @DELETE("finix/api/transactions/{id}")
    Call<ResponseBody> deleteTransaction(@Path("id") int id);
}
