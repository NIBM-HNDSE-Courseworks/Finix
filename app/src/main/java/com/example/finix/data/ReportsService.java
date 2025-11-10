package com.example.finix.data;

import com.example.finix.ui.Reports.ReportsFragment;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;

public interface ReportsService {

    @GET("reports/monthly_expenditure") // Replace with your actual endpoint path
    Call<ReportsFragment.MonthlyExpenditureResponse> getMonthlyExpenditureWrapper();

    @GET("reports/budget_adherence")
    Call<ReportsFragment.BudgetAdherenceResponse> getBudgetAdherence();


}
