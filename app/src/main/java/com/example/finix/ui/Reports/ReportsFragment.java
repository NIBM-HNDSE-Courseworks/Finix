package com.example.finix.ui.Reports;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.finix.R;
import com.example.finix.data.BudgetAdherence;
import com.example.finix.data.MonthlyExpenditure;
import com.example.finix.data.ReportsService;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ReportsFragment extends Fragment {

    private static final String TAG = "ReportsFragment";

    private List<MonthlyExpenditure> latestData; // Monthly Expenditure
    private List<BudgetAdherence> latestBudgetData; // Budget Adherence

    private ActivityResultLauncher<String> createMonthlyPdfLauncher;
    private ActivityResultLauncher<String> createBudgetPdfLauncher;

    // Response wrapper classes
    public static class MonthlyExpenditureResponse {
        public String status;
        public String message;
        public List<MonthlyExpenditure> items;
    }

    public static class BudgetAdherenceResponse {
        public String status;
        public String message;
        public List<BudgetAdherence> items;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_reports, container, false);

        TextView textReport1 = view.findViewById(R.id.text_report1);
        textReport1.setOnClickListener(v -> fetchMonthlyExpenditure());

        TextView textReport2 = view.findViewById(R.id.text_report2);
        textReport2.setOnClickListener(v -> fetchBudgetAdherence());

        // Initialize Activity Result Launchers
        createMonthlyPdfLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/pdf"),
                uri -> {
                    if (uri != null && latestData != null) {
                        createMonthlyPdf(uri, latestData);
                    }
                });

        createBudgetPdfLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/pdf"),
                uri -> {
                    if (uri != null && latestBudgetData != null) {
                        createBudgetPdf(uri, latestBudgetData);
                    }
                });

        return view;
    }

    // -------------------- Fetch Monthly Expenditure --------------------
    private void fetchMonthlyExpenditure() {
        Log.d(TAG, "Fetching Monthly Expenditure...");
        Toast.makeText(getContext(), "Fetching report...", Toast.LENGTH_SHORT).show();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://172.16.100.204:8080/ords/finix/api/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ReportsService api = retrofit.create(ReportsService.class);
        Call<MonthlyExpenditureResponse> call = api.getMonthlyExpenditureWrapper();
        call.enqueue(new Callback<MonthlyExpenditureResponse>() {
            @Override
            public void onResponse(Call<MonthlyExpenditureResponse> call, Response<MonthlyExpenditureResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    MonthlyExpenditureResponse body = response.body();
                    if (body.items != null && !body.items.isEmpty()) {
                        latestData = body.items;
                        openMonthlyFilePicker();
                    } else {
                        Toast.makeText(getContext(), "No report data available.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getContext(), "Failed to fetch data!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<MonthlyExpenditureResponse> call, Throwable t) {
                Log.e(TAG, "Network request failed", t);
                Toast.makeText(getContext(), "Error fetching data!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openMonthlyFilePicker() {
        createMonthlyPdfLauncher.launch("Monthly_Expenditure_Report.pdf");
    }

    private void createMonthlyPdf(Uri uri, List<MonthlyExpenditure> data) {
        try (OutputStream outputStream = requireContext().getContentResolver().openOutputStream(uri)) {

            PdfDocument pdf = new PdfDocument();
            Paint paint = new Paint();
            Paint titlePaint = new Paint();
            Paint linePaint = new Paint();

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            // Title
            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setTextSize(20f);
            titlePaint.setFakeBoldText(true);
            canvas.drawText("Monthly Expenditure Report", pageInfo.getPageWidth() / 2, 60, titlePaint);

            // Table header
            paint.setTextSize(12f);
            paint.setFakeBoldText(true);
            int y = 100;
            int startX = 40;
            int colCategory = startX;
            int colTotal = startX + 300;

            canvas.drawText("Category", colCategory, y, paint);
            canvas.drawText("Total Spent", colTotal, y, paint);
            y += 10;
            linePaint.setStrokeWidth(1f);
            canvas.drawLine(startX, y, pageInfo.getPageWidth() - startX, y, linePaint);
            y += 20;
            paint.setFakeBoldText(false);

            for (MonthlyExpenditure item : data) {
                canvas.drawText(String.valueOf(item.getCategory_id()), colCategory, y, paint);
                canvas.drawText(String.format("%.2f", item.getTotal_spent()), colTotal, y, paint);
                y += 20;

                if (y > 780) {
                    pdf.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pdf.getPages().size() + 1).create();
                    page = pdf.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = 60;
                }
            }

            pdf.finishPage(page);
            pdf.writeTo(outputStream);
            pdf.close();
            Toast.makeText(getContext(), "Monthly PDF created!", Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            Log.e(TAG, "Error creating Monthly PDF", e);
            Toast.makeText(getContext(), "Error creating PDF!", Toast.LENGTH_SHORT).show();
        }
    }

    // -------------------- Fetch Budget Adherence --------------------
    private void fetchBudgetAdherence() {
        Log.d(TAG, "Fetching Budget Adherence...");
        Toast.makeText(getContext(), "Fetching report...", Toast.LENGTH_SHORT).show();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://172.16.100.204:8080/ords/finix/api/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ReportsService api = retrofit.create(ReportsService.class);
        Call<BudgetAdherenceResponse> call = api.getBudgetAdherence();
        call.enqueue(new Callback<BudgetAdherenceResponse>() {
            @Override
            public void onResponse(Call<BudgetAdherenceResponse> call, Response<BudgetAdherenceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BudgetAdherenceResponse body = response.body();
                    if (body.items != null && !body.items.isEmpty()) {
                        latestBudgetData = body.items;
                        openBudgetFilePicker();
                    } else {
                        Toast.makeText(getContext(), "No Budget data available.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getContext(), "Failed to fetch Budget Adherence!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<BudgetAdherenceResponse> call, Throwable t) {
                Log.e(TAG, "Network request failed", t);
                Toast.makeText(getContext(), "Error fetching data!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openBudgetFilePicker() {
        createBudgetPdfLauncher.launch("Budget_Adherence_Report.pdf");
    }

    private void createBudgetPdf(Uri uri, List<BudgetAdherence> data) {
        try (OutputStream outputStream = requireContext().getContentResolver().openOutputStream(uri)) {

            PdfDocument pdf = new PdfDocument();
            Paint paint = new Paint();
            Paint titlePaint = new Paint();
            Paint linePaint = new Paint();

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            // Title
            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setTextSize(20f);
            titlePaint.setFakeBoldText(true);
            canvas.drawText("Budget Adherence Report", pageInfo.getPageWidth() / 2, 60, titlePaint);

            // Table header
            paint.setTextSize(12f);
            paint.setFakeBoldText(true);
            int y = 100;
            int startX = 40;
            int colCategory = startX;
            int colBudgeted = startX + 150;
            int colActual = startX + 250;
            int colPct = startX + 350;
            int colStatus = startX + 450;

            canvas.drawText("Category", colCategory, y, paint);
            canvas.drawText("Budgeted", colBudgeted, y, paint);
            canvas.drawText("Spent", colActual, y, paint);
            canvas.drawText("% of Budget", colPct, y, paint);
            canvas.drawText("Status", colStatus, y, paint);
            y += 10;
            linePaint.setStrokeWidth(1f);
            canvas.drawLine(startX, y, pageInfo.getPageWidth() - startX, y, linePaint);
            y += 20;
            paint.setFakeBoldText(false);

            for (BudgetAdherence item : data) {
                canvas.drawText(item.getCategory() != null ? item.getCategory() : "", colCategory, y, paint);
                canvas.drawText(String.format("%.2f", item.getBudgetedAmount()), colBudgeted, y, paint);
                canvas.drawText(String.format("%.2f", item.getActualSpent()), colActual, y, paint);
                canvas.drawText(String.format("%.2f", item.getPctOfBudget()), colPct, y, paint);
                canvas.drawText(item.getAdherenceStatus() != null ? item.getAdherenceStatus() : "", colStatus, y, paint);

                y += 20;
                if (y > 780) {
                    pdf.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(595, 842, pdf.getPages().size() + 1).create();
                    page = pdf.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = 60;
                }
            }

            pdf.finishPage(page);
            pdf.writeTo(outputStream);
            pdf.close();
            Toast.makeText(getContext(), "Budget PDF created!", Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            Log.e(TAG, "Error creating Budget PDF", e);
            Toast.makeText(getContext(), "Error creating PDF!", Toast.LENGTH_SHORT).show();
        }
    }
}
