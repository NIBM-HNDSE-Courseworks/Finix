// 📦 This is the package name of your app
package com.example.finix;

// 🧩 Importing required Android classes
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Menu;

// 🍫 Snack bar shows small messages at the bottom of the screen
import com.google.android.material.snackbar.Snackbar;
// 🌐 NavigationView is the side menu (drawer) in your app
import com.google.android.material.navigation.NavigationView;

// 🧭 These are for handling app navigation between fragments
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
// 📂 DrawerLayout helps create the sliding side menu
import androidx.drawerlayout.widget.DrawerLayout;
// 🏗️ Base class for activities that use the modern Android features
import androidx.appcompat.app.AppCompatActivity;

// 🧵 This connects layout XML files using "View Binding"
import com.example.finix.databinding.ActivityMainBinding;

// 🌟 Main Activity — runs first when the app starts
public class MainActivity extends AppCompatActivity {

    // 🧭 Used to manage top-level destinations in navigation
    private AppBarConfiguration mAppBarConfiguration;
    // 🔗 Used for connecting XML views with Java code
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // 🏁 Calls the parent onCreate() method to start the activity

        // 🧵 Inflates (creates) the view from XML using View Binding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        // 📱 Sets the screen view to the one defined in activity_main.xml
        setContentView(binding.getRoot());

        // 🛠️ Sets up the toolbar (top app bar)
        setSupportActionBar(binding.appBarMain.toolbar);

        // ➕ Adds a click listener for the floating action button (FAB)
        binding.appBarMain.addQuickMenu.setOnClickListener(view -> {

            // Inflate popup layout
            View popupView = getLayoutInflater().inflate(R.layout.layout_quick_add_popup, null);

            // Create PopupWindow
            final android.widget.PopupWindow popupWindow = new android.widget.PopupWindow(
                    popupView,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
            );

            // Transparent background for vector drawable
            popupWindow.setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            );

            // Convert 12dp gap to pixels
            int gap = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    12,
                    getResources().getDisplayMetrics()
            );

            // Get FAB location
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            int fabX = location[0];
            int fabY = location[1];

            // Measure popup
            popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int popupWidth = popupView.getMeasuredWidth();
            int popupHeight = popupView.getMeasuredHeight();

            // --- Position popup around FAB ---
            // Example: top-right corner of FAB with small floating gap
            // Position popup so bottom-right corner points to FAB
            int xOffset = fabX + view.getWidth() - popupWidth; // align right edges
            int yOffset = fabY + view.getHeight() - popupHeight; // align bottom edges

// Optional small gap so it "floats"
            int leftShift = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, getResources().getDisplayMetrics());
            xOffset -= leftShift; // move popup more to the left
            xOffset -= gap; // shift left slightly
            yOffset -= gap; // shift up slightly


            // Show popup
            popupWindow.showAtLocation(view, Gravity.NO_GRAVITY, xOffset, yOffset);

            // Handle popup buttons
            popupView.findViewById(R.id.btnAddTransaction).setOnClickListener(v -> {
                popupWindow.dismiss();
            });

            popupView.findViewById(R.id.btnAddBudget).setOnClickListener(v -> {
                popupWindow.dismiss();
            });

            popupView.findViewById(R.id.btnAddGoal).setOnClickListener(v -> {
                popupWindow.dismiss();
            });
        });


        // 📂 Gets the drawer layout (side menu)
        DrawerLayout drawer = binding.drawerLayout;
        // 📋 Gets the navigation menu view
        NavigationView navigationView = binding.navView;

        // 🧭 Tells which fragments are the main (top-level) screens
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_dashboard,
                R.id.nav_transactions,
                R.id.nav_budget,
                R.id.nav_savings,
                R.id.nav_settings)
                .setOpenableLayout(drawer) // 🔗 Connects drawer layout to navigation
                .build();

        // 🚀 Finds the navigation controller (controls movement between screens)
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);

        // 🔄 Connects the top app bar (toolbar) with navigation controller
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);

        // 🧭 Connects the side menu items with the navigation controller
        NavigationUI.setupWithNavController(navigationView, navController);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 🍔 Adds menu items to the top app bar (if any menu XML exists)
        getMenuInflater().inflate(R.menu.main, menu);
        return true; // ✅ Show the menu
    }

    @Override
    public boolean onSupportNavigateUp() {
        // 🧭 Handles "up" navigation (back arrow on the toolbar)
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        // 🔙 Moves up in navigation or falls back to default behavior
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}

// 📝 Summary of Navigation Setup:
// 1️⃣ Dashboard — Main home screen
// 2️⃣ Transactions — Show all money transactions
// 3️⃣ Budget — Manage user budgets
// 4️⃣ Savings — Track savings & goals
// 5️⃣ Settings — Change app preferences ⚙️
