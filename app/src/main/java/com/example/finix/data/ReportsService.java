package com.example.finix.data;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;

public interface ReportsService {

    @GET("reports/monthly_expenditure/")
    Call<List<MonthlyExpenditure>> getMonthlyExpenditure();
}
