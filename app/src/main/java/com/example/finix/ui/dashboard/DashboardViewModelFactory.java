package com.example.finix.ui.dashboard;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.finix.data.CategoryDAO;

public class DashboardViewModelFactory implements ViewModelProvider.Factory {

    private final Application application;
    private final CategoryDAO categoryDao;

    public DashboardViewModelFactory(@NonNull Application application, @NonNull CategoryDAO categoryDao) {
        this.application = application;
        this.categoryDao = categoryDao;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(DashboardViewModel.class)) {
            // Pass the required dependencies to the constructor
            return (T) new DashboardViewModel(application, categoryDao);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}