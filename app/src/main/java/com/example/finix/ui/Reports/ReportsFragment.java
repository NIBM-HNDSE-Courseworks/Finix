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

    private static final int CREATE_PDF_REQUEST_CODE = 1001;
    private List<MonthlyExpenditure> latestData; // To hold fetched data temporarily

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reports, container, false);

        TextView textReport1 = view.findViewById(R.id.text_report1);
        textReport1.setOnClickListener(v -> fetchMonthlyExpenditure());

        return view;
    }

    // 🔹 Step 1: Fetch data from backend
    private void fetchMonthlyExpenditure() {
        Toast.makeText(getContext(), "Fetching report...", Toast.LENGTH_SHORT).show();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://172.16.100.204:8080/ords/finix/api/")  // your ORDS URL
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ReportsService api = retrofit.create(ReportsService.class);
        api.getMonthlyExpenditure().enqueue(new Callback<List<MonthlyExpenditure>>() {
            @Override
            public void onResponse(Call<List<MonthlyExpenditure>> call, Response<List<MonthlyExpenditure>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    latestData = response.body();
                    openFilePicker(); // Let user choose where to save the PDF
                } else {
                    Toast.makeText(getContext(), "Failed to get data!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<MonthlyExpenditure>> call, Throwable t) {
                Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 🔹 Step 2: Ask user where to save
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, "Monthly_Expenditure_Report.pdf");
        startActivityForResult(intent, CREATE_PDF_REQUEST_CODE);
    }

    // 🔹 Step 3: Create PDF and write to selected location
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CREATE_PDF_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null && latestData != null) {
                createPdf(uri, latestData);
            } else {
                Toast.makeText(getContext(), "No data to save!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void createPdf(Uri uri, List<MonthlyExpenditure> data) {
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

            Toast.makeText(getContext(), "PDF downloaded successfully!", Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Error creating PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
