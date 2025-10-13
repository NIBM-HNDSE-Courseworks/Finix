package com.example.finix.ui.transactions;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.finix.R;
import com.example.finix.databinding.FragmentTransactionsBinding;

public class TransactionsFragment extends Fragment {

    private FragmentTransactionsBinding binding;
    // Assuming you created a TransactionsViewModel as per the previous answer
    private TransactionsViewModel transactionsViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        // 1. Get the corresponding ViewModel
        transactionsViewModel =
                new ViewModelProvider(this).get(TransactionsViewModel.class);

        // 2. Inflate the layout using View Binding
        binding = FragmentTransactionsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // 3. Set up UI references and connect to ViewModel data
        final TextView textView = binding.textTransactions;
        transactionsViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}