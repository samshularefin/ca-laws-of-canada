package com.diasoft.calaws.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.diasoft.calaws.R;
import com.diasoft.calaws.data.LawsDbHelper;
import com.diasoft.calaws.data.UserDb;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DocumentActivity extends AppCompatActivity {

    public static final String EXTRA_DOC_ID = "doc_id";
    public static final String EXTRA_SCROLL_TO_SEQ = "scroll_to_seq";

    private LawsDbHelper dbHelper;
    private UserDb userDb;
    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private SectionAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String docId;
    private LawsDbHelper.DocumentDetail doc;
    private MenuItem bookmarkItem;

    private View findBar;
    private EditText findInput;
    private TextView findCount;
    private final List<Integer> matches = new ArrayList<>();
    private int matchIdx = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        com.diasoft.calaws.ads.Ads.attachBanner(this, R.id.adContainer, R.id.adView);

        dbHelper = new LawsDbHelper(this);
        userDb = new UserDb(this);
        recyclerView = findViewById(R.id.sectionsRecyclerView);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new SectionAdapter(userDb);
        recyclerView.setAdapter(adapter);

        setupFindBar();

        docId = getIntent().getStringExtra(EXTRA_DOC_ID);
        int scrollToSeq = getIntent().getIntExtra(EXTRA_SCROLL_TO_SEQ, -1);

        if (docId != null) loadDocument(docId, scrollToSeq);
    }

    private void loadDocument(String id, int scrollToSeq) {
        executor.execute(() -> {
            LawsDbHelper.DocumentDetail loaded = dbHelper.getDocument(id);
            mainHandler.post(() -> {
                if (loaded == null) return;
                doc = loaded;
                setTitle(doc.shortTitle);
                adapter.setHeader(doc);
                adapter.setSections(doc.sections);
                adapter.setBookmarked(userDb.bookmarkedSeqs(id));
                userDb.addHistory(id, doc.shortTitle);
                updateBookmarkIcon();
                if (scrollToSeq >= 0) {
                    // +1 to account for the header item at position 0.
                    recyclerView.scrollToPosition(scrollToSeq + 1);
                }
            });
        });
    }

    // ---- Toolbar actions -------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_document, menu);
        bookmarkItem = menu.findItem(R.id.action_bookmark);
        updateBookmarkIcon();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_find) {
            if (findBar.getVisibility() == View.VISIBLE) closeFind(); else openFind();
            return true;
        } else if (id == R.id.action_goto) {
            showGotoDialog();
            return true;
        } else if (id == R.id.action_bookmark) {
            toggleDocBookmark();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleDocBookmark() {
        if (doc == null) return;
        boolean now = userDb.toggleBookmark(doc.id, -1, doc.shortTitle, null, null);
        updateBookmarkIcon();
        Toast.makeText(this, now ? R.string.bookmark_added : R.string.bookmark_removed, Toast.LENGTH_SHORT).show();
    }

    private void updateBookmarkIcon() {
        if (bookmarkItem == null || docId == null) return;
        boolean saved = userDb.bookmarkedSeqs(docId).contains(-1);
        bookmarkItem.setIcon(saved ? R.drawable.ic_star_filled_white : R.drawable.ic_star_border_white);
    }

    // ---- Go to section ---------------------------------------------------

    private void showGotoDialog() {
        if (doc == null) return;
        EditText input = new EditText(this);
        input.setHint(R.string.goto_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.goto_title)
                .setView(box)
                .setPositiveButton(R.string.ok, (d, w) -> gotoSection(input.getText().toString()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static String normalizeLabel(String s) {
        String t = s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
        if (t.startsWith("s.")) t = t.substring(2);
        else if (t.startsWith("art.")) t = t.substring(4);
        return t.replace(" ", "");
    }

    private void gotoSection(String raw) {
        String want = normalizeLabel(raw);
        if (want.isEmpty()) return;
        for (int i = 0; i < doc.sections.size(); i++) {
            if (normalizeLabel(doc.sections.get(i).label).equals(want)) {
                layoutManager.scrollToPositionWithOffset(i + 1, 0);
                return;
            }
        }
        Toast.makeText(this, getString(R.string.goto_not_found, raw.trim()), Toast.LENGTH_SHORT).show();
    }

    // ---- Find in law -----------------------------------------------------

    private void setupFindBar() {
        findBar = findViewById(R.id.findBar);
        findInput = findViewById(R.id.findInput);
        findCount = findViewById(R.id.findCount);

        findInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { runFind(s.toString()); }
        });
        findInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                stepMatch(1);
                return true;
            }
            return false;
        });
        findViewById(R.id.findNext).setOnClickListener(v -> stepMatch(1));
        findViewById(R.id.findPrev).setOnClickListener(v -> stepMatch(-1));
        findViewById(R.id.findClose).setOnClickListener(v -> closeFind());
    }

    private void openFind() {
        findBar.setVisibility(View.VISIBLE);
        findInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(findInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void closeFind() {
        findInput.setText("");
        findBar.setVisibility(View.GONE);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(findInput.getWindowToken(), 0);
        adapter.setHighlight(null);
    }

    private void runFind(String raw) {
        matches.clear();
        matchIdx = -1;
        String q = raw.trim();
        if (doc == null || q.length() < 2) {
            adapter.setHighlight(null);
            findCount.setText("");
            return;
        }
        String lower = q.toLowerCase(Locale.ROOT);
        for (int i = 0; i < doc.sections.size(); i++) {
            LawsDbHelper.Section s = doc.sections.get(i);
            String hay = ((s.marginalNote == null ? "" : s.marginalNote) + " " + SectionAdapter.bodyText(s))
                    .toLowerCase(Locale.ROOT);
            if (hay.contains(lower)) matches.add(i);
        }
        adapter.setHighlight(q);
        if (matches.isEmpty()) {
            findCount.setText(R.string.find_none);
        } else {
            matchIdx = 0;
            showMatch();
        }
    }

    private void stepMatch(int dir) {
        if (matches.isEmpty()) return;
        matchIdx = (matchIdx + dir + matches.size()) % matches.size();
        showMatch();
    }

    private void showMatch() {
        findCount.setText(String.format(Locale.getDefault(), "%d/%d", matchIdx + 1, matches.size()));
        layoutManager.scrollToPositionWithOffset(matches.get(matchIdx) + 1, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbHelper.close();
        userDb.close();
        executor.shutdown();
    }
}
