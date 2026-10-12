package com.diasoft.calaws.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.diasoft.calaws.R;
import com.diasoft.calaws.data.UserDb;

import java.util.ArrayList;
import java.util.List;

public class LibraryActivity extends AppCompatActivity {

    private UserDb userDb;
    private RecyclerView list;
    private TextView empty;
    private TabLayout tabs;
    private final EntryAdapter adapter = new EntryAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        com.diasoft.calaws.ads.Ads.attachBanner(this, R.id.adContainer, R.id.adView);

        userDb = new UserDb(this);
        list = findViewById(R.id.libraryList);
        empty = findViewById(R.id.libraryEmpty);
        tabs = findViewById(R.id.libraryTabs);

        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        tabs.addTab(tabs.newTab().setText(R.string.tab_saved));
        tabs.addTab(tabs.newTab().setText(R.string.tab_history));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                invalidateOptionsMenu();
                reload();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private boolean showingSaved() {
        return tabs.getSelectedTabPosition() == 0;
    }

    private void reload() {
        List<UserDb.Entry> items = showingSaved() ? userDb.bookmarks() : userDb.history();
        adapter.set(items);
        empty.setText(showingSaved() ? R.string.empty_saved : R.string.empty_history);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_library, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem clear = menu.findItem(R.id.action_clear_history);
        if (clear != null) clear.setVisible(!showingSaved());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_clear_history) {
            userDb.clearHistory();
            reload();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void open(UserDb.Entry e) {
        Intent intent = new Intent(this, DocumentActivity.class);
        intent.putExtra(DocumentActivity.EXTRA_DOC_ID, e.docId);
        if (e.seq >= 0) intent.putExtra(DocumentActivity.EXTRA_SCROLL_TO_SEQ, e.seq);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        userDb.close();
    }

    private class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.VH> {
        private List<UserDb.Entry> items = new ArrayList<>();

        void set(List<UserDb.Entry> next) {
            items = next;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_library, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            UserDb.Entry e = items.get(position);
            boolean saved = showingSaved();
            h.title.setText(e.title);
            String sub;
            if (saved) {
                if (e.seq < 0) {
                    sub = getString(R.string.whole_law);
                } else {
                    sub = (e.label != null && !e.label.isEmpty() ? "s. " + e.label : "")
                            + (e.note != null && !e.note.isEmpty() ? " — " + e.note : "");
                }
            } else {
                sub = DateUtils.getRelativeTimeSpanString(e.time, System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS).toString();
            }
            h.subtitle.setText(sub);
            h.remove.setVisibility(saved ? View.VISIBLE : View.GONE);
            h.itemView.setOnClickListener(v -> open(e));
            h.remove.setOnClickListener(v -> {
                userDb.removeBookmark(e.id);
                reload();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView title, subtitle;
            View remove;
            VH(@NonNull View v) {
                super(v);
                title = v.findViewById(R.id.libTitle);
                subtitle = v.findViewById(R.id.libSubtitle);
                remove = v.findViewById(R.id.libRemove);
            }
        }
    }
}
