package com.diasoft.calaws.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.diasoft.calaws.R;
import com.diasoft.calaws.data.LawsDbHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchActivity extends AppCompatActivity {

    private LawsDbHelper dbHelper;
    private RecyclerView recyclerView;
    private TabLayout modeTabs;
    private SearchView searchView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean searchingTitles = true; // true = title search, false = full text

    private ResultsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        bannerAdView = com.diasoft.calaws.ads.Ads.attachBanner(this, R.id.bannerAdContainer);

        dbHelper = new LawsDbHelper(this);
        recyclerView = findViewById(R.id.searchResults);
        modeTabs = findViewById(R.id.searchModeTabs);
        searchView = findViewById(R.id.searchView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ResultsAdapter();
        recyclerView.setAdapter(adapter);

        modeTabs.addTab(modeTabs.newTab().setText(R.string.search_by_title));
        modeTabs.addTab(modeTabs.newTab().setText(R.string.search_full_text));
        modeTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                searchingTitles = tab.getPosition() == 0;
                runSearch(searchView.getQuery().toString());
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                runSearch(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.length() >= 3) runSearch(newText);
                return true;
            }
        });
        searchView.setIconifiedByDefault(false);
        searchView.requestFocus();
    }

    private void runSearch(String query) {
        if (query == null || query.trim().length() < 2) {
            adapter.setTitleResults(new ArrayList<>());
            adapter.setTextResults(new ArrayList<>());
            return;
        }
        final boolean titles = searchingTitles;
        executor.execute(() -> {
            try {
                if (titles) {
                    List<LawsDbHelper.DocumentSummary> results = dbHelper.searchTitles(query);
                    mainHandler.post(() -> adapter.setTitleResults(results));
                } else {
                    List<LawsDbHelper.SectionSearchResult> results = dbHelper.searchText(query);
                    mainHandler.post(() -> adapter.setTextResults(results));
                }
            } catch (RuntimeException e) {
                // e.g. an older database whose search index this device cannot read; it refreshes itself.
                mainHandler.post(() -> android.widget.Toast.makeText(
                        this, R.string.search_unavailable, android.widget.Toast.LENGTH_LONG).show());
            }
        });
    }

    private void openDocument(String docId) {
        Intent intent = new Intent(this, DocumentActivity.class);
        intent.putExtra(DocumentActivity.EXTRA_DOC_ID, docId);
        startActivity(intent);
    }

    private void openSection(String docId, int seq) {
        Intent intent = new Intent(this, DocumentActivity.class);
        intent.putExtra(DocumentActivity.EXTRA_DOC_ID, docId);
        intent.putExtra(DocumentActivity.EXTRA_SCROLL_TO_SEQ, seq);
        startActivity(intent);
    }

    private com.google.android.gms.ads.AdView bannerAdView;

    @Override
    protected void onResume() {
        super.onResume();
        if (bannerAdView != null) bannerAdView.resume();
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
        dbHelper.close();
        executor.shutdown();
    }

    /** Adapter that renders either title-search or full-text-search results. */
    private class ResultsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<LawsDbHelper.DocumentSummary> titleResults = new ArrayList<>();
        private List<LawsDbHelper.SectionSearchResult> textResults = new ArrayList<>();

        void setTitleResults(List<LawsDbHelper.DocumentSummary> r) {
            titleResults = r; textResults = new ArrayList<>(); notifyDataSetChanged();
        }
        void setTextResults(List<LawsDbHelper.SectionSearchResult> r) {
            textResults = r; titleResults = new ArrayList<>(); notifyDataSetChanged();
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
            return new RecyclerView.ViewHolder(v) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TextView title = holder.itemView.findViewById(R.id.resultTitle);
            TextView subtitle = holder.itemView.findViewById(R.id.resultSubtitle);

            if (searchingTitles) {
                LawsDbHelper.DocumentSummary doc = titleResults.get(position);
                title.setText(doc.shortTitle);
                subtitle.setText(doc.consolidatedNumber);
                holder.itemView.setOnClickListener(v -> openDocument(doc.id));
            } else {
                LawsDbHelper.SectionSearchResult r = textResults.get(position);
                title.setText(r.shortTitle + (r.marginalNote != null && !r.marginalNote.isEmpty() ? " — " + r.marginalNote : ""));
                subtitle.setText(Html.fromHtml(r.snippet.replace("[", "<b>").replace("]", "</b>"), Html.FROM_HTML_MODE_LEGACY));
                holder.itemView.setOnClickListener(v -> openSection(r.docId, r.seq));
            }
        }

        @Override
        public int getItemCount() {
            return searchingTitles ? titleResults.size() : textResults.size();
        }
    }
}
