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
import com.diasoft.calaws.data.LawsDbHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private TabLayout tabLayout;
    private DocumentAdapter adapter;
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
        com.diasoft.calaws.ads.Ads.attachBanner(this, R.id.adContainer, R.id.adView);

        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        tabLayout = findViewById(R.id.tabLayout);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DocumentAdapter(this::openDocument);
        recyclerView.setAdapter(adapter);

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

    private void resetAndLoad() {
        currentPage = 1;
        hasMore = true;
        adapter.clear();
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
        android.view.MenuItem searchItem = menu.findItem(R.id.action_search);
        searchItem.setOnMenuItemClickListener(item -> {
            startActivity(new Intent(this, SearchActivity.class));
            return true;
        });
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbHelper.close();
        executor.shutdown();
    }
}
