package com.diasoft.calaws.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.diasoft.calaws.R;
import com.diasoft.calaws.ads.Ads;
import com.diasoft.calaws.ads.AdsManager;
import com.diasoft.calaws.ads.LazyNativeAdLoader;
import com.diasoft.calaws.data.LawsDbHelper;
import com.google.android.gms.ads.AdView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private TabLayout tabLayout;
    private DocumentAdapter adapter;
    private AdView bannerAdView;
    private LazyNativeAdLoader adLoader;
    private LawsDbHelper dbHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String currentType = "act"; // "act" | "regulation"
    private int currentPage = 1;
    private static final int PAGE_SIZE = 50;
    private boolean isLoading = false;
    private boolean hasMore = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new LawsDbHelper(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        bannerAdView = Ads.attachBanner(this);

        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        tabLayout = findViewById(R.id.tabLayout);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DocumentAdapter(this::openDocument);
        recyclerView.setAdapter(adapter);
        adLoader = new LazyNativeAdLoader(this, recyclerView, adapter::addNativeAd);

        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_acts));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_regulations));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentType = (tab.getPosition() == 0) ? "act" : "regulation";
                resetAndLoad();
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        swipeRefresh.setOnRefreshListener(this::resetAndLoad);

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitDialog();
            }
        });

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null || isLoading || !hasMore) return;
                int visibleItemCount = lm.getChildCount();
                int totalItemCount = lm.getItemCount();
                int firstVisible = lm.findFirstVisibleItemPosition();
                if ((visibleItemCount + firstVisible) >= totalItemCount - 10) {
                    loadNextPage();
                }
            }
        });

        resetAndLoad();
    }

    private void showExitDialog() {
        android.view.View content = getLayoutInflater().inflate(R.layout.dialog_exit, null);
        androidx.appcompat.app.AlertDialog dialog =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_CaLaws_Dialog)
                        .setView(content)
                        .create();
        content.findViewById(R.id.exitStay).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.exitConfirm).setOnClickListener(v -> finishAffinity());
        dialog.show();
    }

    private void resetAndLoad() {
        currentPage = 1;
        hasMore = true;
        adapter.clear();
        // Slots for far more rows than are loaded yet; the loader keeps an ad buffered until its slot exists.
        adLoader.start(AdsManager.computeAdPositions(5000, AdsManager.nativeAdInterval()));
        loadNextPage();
    }

    private void loadNextPage() {
        if (isLoading || !hasMore) return;
        isLoading = true;
        swipeRefresh.setRefreshing(true);
        final int page = currentPage;
        final String type = currentType;

        executor.execute(() -> {
            List<LawsDbHelper.DocumentSummary> results = dbHelper.listDocuments(type, page, PAGE_SIZE);
            mainHandler.post(() -> {
                adapter.addAll(results);
                hasMore = results.size() == PAGE_SIZE;
                currentPage++;
                isLoading = false;
                swipeRefresh.setRefreshing(false);
            });
        });
    }

    private void openDocument(String docId) {
        Intent intent = new Intent(this, DocumentActivity.class);
        intent.putExtra(DocumentActivity.EXTRA_DOC_ID, docId);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search) {
            startActivity(new Intent(this, SearchActivity.class));
            return true;
        } else if (id == R.id.action_library) {
            startActivity(new Intent(this, LibraryActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            SettingsDialog.show(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bannerAdView != null) bannerAdView.resume();
        AdsManager.maybeShowAppOpenAdOnColdLaunch(this);
    }

    @Override
    protected void onPause() {
        if (bannerAdView != null) bannerAdView.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bannerAdView != null) bannerAdView.destroy();
        adLoader.stop();
        adapter.destroyAds();
        dbHelper.close();
        executor.shutdown();
    }
}
