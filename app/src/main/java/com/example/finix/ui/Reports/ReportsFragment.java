package com.example.finix.ui.Reports;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
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
    private static final int CREATE_PDF_REQUEST_CODE = 1001;
    private List<MonthlyExpenditure> latestData; // Holds fetched data

    // 🔹 Wrapper class matching JSON response from server
    public static class MonthlyExpenditureResponse {
        public String status;
        public String message;
        public List<MonthlyExpenditure> items;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reports, container, false);

        TextView textReport1 = view.findViewById(R.id.text_report1);
        textReport1.setOnClickListener(v -> {
            Log.d(TAG, "Report 1 clicked");
            fetchMonthlyExpenditure();
        });

        return view;
    }

    private void fetchMonthlyExpenditure() {
        Log.d(TAG, "Fetching Monthly Expenditure from server...");
        Toast.makeText(getContext(), "Fetching report...", Toast.LENGTH_SHORT).show();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://172.16.100.204:8080/ords/finix/api/")  // your ORDS URL
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ReportsService api = retrofit.create(ReportsService.class);
        Call<MonthlyExpenditureResponse> call = api.getMonthlyExpenditureWrapper(); // Note: wrapper call
        call.enqueue(new Callback<MonthlyExpenditureResponse>() {
            @Override
            public void onResponse(Call<MonthlyExpenditureResponse> call, Response<MonthlyExpenditureResponse> response) {
                Log.d(TAG, "Response received. Code: " + response.code());

                if (response.isSuccessful() && response.body() != null) {
                    MonthlyExpenditureResponse body = response.body();
                    Log.d(TAG, "Response status: " + body.status + ", message: " + body.message);

                    if (body.items != null && !body.items.isEmpty()) {
                        latestData = body.items;
                        Log.d(TAG, "Fetched " + latestData.size() + " items.");
                        for (MonthlyExpenditure item : latestData) {
                            Log.d(TAG, "Item: month=" + item.getMonth_year() +
                                    ", category=" + item.getCategory_id() +
                                    ", total=" + item.getTotal_spent());
                        }
                        openFilePicker();
                    } else {
                        Log.e(TAG, "No items in response");
                        Toast.makeText(getContext(), "No report data available.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e(TAG, "Failed to get data. Response code: " + response.code());
                    Toast.makeText(getContext(), "Failed to get data! Check logs.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<MonthlyExpenditureResponse> call, Throwable t) {
                Log.e(TAG, "Network request failed", t);
                Toast.makeText(getContext(), "Error fetching data! Check logs.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openFilePicker() {
        Log.d(TAG, "Opening file picker to save PDF...");
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, "Monthly_Expenditure_Report.pdf");
        startActivityForResult(intent, CREATE_PDF_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        if (requestCode == CREATE_PDF_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null && latestData != null) {
                Log.d(TAG, "Creating PDF at URI: " + uri.toString());
                createPdf(uri, latestData);
            } else {
                Log.e(TAG, "No data to save or URI is null");
                Toast.makeText(getContext(), "No data to save!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void createPdf(Uri uri, List<MonthlyExpenditure> data) {
        Log.d(TAG, "Start creating PDF with " + data.size() + " items");
        try (OutputStream outputStream = requireContext().getContentResolver().openOutputStream(uri)) {

            PdfDocument pdf = new PdfDocument();
            Paint paint = new Paint();
            Paint titlePaint = new Paint();

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setTextSize(20f);
            titlePaint.setFakeBoldText(true);
            canvas.drawText("Monthly Expenditure Report", pageInfo.getPageWidth() / 2, 60, titlePaint);

            paint.setTextSize(12f);
            int y = 100;
            canvas.drawText("Month-Year      Category                Total Spent", 40, y, paint);
            y += 20;
            canvas.drawLine(40, y, pageInfo.getPageWidth() - 40, y, paint);
            y += 20;

            for (MonthlyExpenditure item : data) {
                String line = String.format("%-12s %-20s %.2f",
                        item.getMonth_year(), item.getCategory_id(), item.getTotal_spent());
                canvas.drawText(line, 40, y, paint);
                Log.d(TAG, "Writing PDF line: " + line);
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

            Log.d(TAG, "PDF created successfully at URI: " + uri.toString());
            Toast.makeText(getContext(), "PDF downloaded successfully!", Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            Log.e(TAG, "Error creating PDF", e);
            Toast.makeText(getContext(), "Error creating PDF! Check logs.", Toast.LENGTH_SHORT).show();
        }
    }
}
