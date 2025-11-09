package com.example.finix.data;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE; // 💡 NEW IMPORT
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

import java.util.List;

/**
 * Retrofit Interface for SynchronizationLog-related API calls.
 * Assumes the ORDS base path: "finix/api/synclogs/"
 */
public interface SynchronizationLogService {

    @GET("finix/api/synclogs/")
    Call<SynchronizationLogsResponse> getAllLogs();

    /**
     * Create a new log entry on the server.
     */
    @POST("finix/api/synclogs/")
    Call<ResponseBody> createLog(@Body SynchronizationLog log);


    // 🚀 NEW METHOD: Delete ALL synchronization log records from the server.
    @DELETE("finix/api/synclogs/")
    Call<ResponseBody> deleteAllLogs();
}