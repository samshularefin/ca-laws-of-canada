package com.diasoft.calaws.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.diasoft.calaws.R;
import com.diasoft.calaws.data.LawsDbHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DocumentActivity extends AppCompatActivity {

    public static final String EXTRA_DOC_ID = "doc_id";
    public static final String EXTRA_SCROLL_TO_SEQ = "scroll_to_seq";

    private LawsDbHelper dbHelper;
    private RecyclerView recyclerView;
    private SectionAdapter adapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document);

        dbHelper = new LawsDbHelper(this);
        recyclerView = findViewById(R.id.sectionsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SectionAdapter();
        recyclerView.setAdapter(adapter);

        String docId = getIntent().getStringExtra(EXTRA_DOC_ID);
        int scrollToSeq = getIntent().getIntExtra(EXTRA_SCROLL_TO_SEQ, -1);

        if (docId != null) loadDocument(docId, scrollToSeq);
    }

    private void loadDocument(String docId, int scrollToSeq) {
        executor.execute(() -> {
            LawsDbHelper.DocumentDetail doc = dbHelper.getDocument(docId);
            mainHandler.post(() -> {
                if (doc == null) return;
                setTitle(doc.shortTitle);
                adapter.setHeader(doc);
                adapter.setSections(doc.sections);
                if (scrollToSeq >= 0) {
                    // +1 to account for the header item at position 0.
                    recyclerView.scrollToPosition(scrollToSeq + 1);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbHelper.close();
        executor.shutdown();
    }
}
