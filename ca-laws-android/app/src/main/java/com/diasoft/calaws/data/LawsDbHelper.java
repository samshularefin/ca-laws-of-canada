package com.diasoft.calaws.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only accessor over the pre-built ca_laws.db (Acts + Regulations + FTS5 indexes).
 * The DB is created by the ingestion pipeline, not by this app, so this class
 * opens it directly rather than extending SQLiteOpenHelper's onCreate/onUpgrade flow.
 */
public class LawsDbHelper {

    private final SQLiteDatabase db;

    public LawsDbHelper(Context context) {
        db = SQLiteDatabase.openDatabase(
                DatabaseInstaller.getDbFile(context).getAbsolutePath(),
                null,
                SQLiteDatabase.OPEN_READONLY
        );
    }

    public void close() {
        if (db != null && db.isOpen()) db.close();
    }

    public List<DocumentSummary> listDocuments(String type, int page, int pageSize) {
        List<DocumentSummary> results = new ArrayList<>();
        String where = (type != null) ? "WHERE type = ?" : "";
        String[] args = (type != null)
                ? new String[]{type, String.valueOf(pageSize), String.valueOf((page - 1) * pageSize)}
                : new String[]{String.valueOf(pageSize), String.valueOf((page - 1) * pageSize)};

        String sql = "SELECT id, type, consolidated_number, short_title, long_title, section_count " +
                "FROM documents " + where + " ORDER BY short_title LIMIT ? OFFSET ?";

        try (Cursor c = db.rawQuery(sql, args)) {
            while (c.moveToNext()) {
                results.add(DocumentSummary.fromCursor(c));
            }
        }
        return results;
    }

    public DocumentDetail getDocument(String id) {
        DocumentDetail doc = null;
        try (Cursor c = db.rawQuery(
                "SELECT id, type, consolidated_number, short_title, long_title, in_force_start, last_amended, current_to, section_count " +
                        "FROM documents WHERE id = ?", new String[]{id})) {
            if (c.moveToFirst()) doc = DocumentDetail.fromCursor(c);
        }
        if (doc == null) return null;

        try (Cursor c = db.rawQuery(
                "SELECT seq, label, marginal_note, heading_path, text FROM sections WHERE doc_id = ? ORDER BY seq",
                new String[]{id})) {
            while (c.moveToNext()) {
                doc.sections.add(Section.fromCursor(c));
            }
        }
        return doc;
    }

    public List<DocumentSummary> searchTitles(String query) {
        List<DocumentSummary> results = new ArrayList<>();
        String ftsQuery = buildFtsQuery(query);
        if (ftsQuery == null) return results;

        // Laws whose own name contains every word come first (so "Criminal Code" beats a law that only
        // mentions it in its long title), then shorter names first.
        List<String> args = new ArrayList<>();
        args.add(ftsQuery);
        StringBuilder inName = new StringBuilder();
        for (String t : words(query)) {
            if (inName.length() > 0) inName.append(" AND ");
            inName.append("d.short_title LIKE ?");
            args.add("%" + t + "%");
        }
        String sql = "SELECT d.id, d.type, d.consolidated_number, d.short_title, d.long_title, d.section_count " +
                "FROM documents_fts f JOIN documents d ON d.id = f.id " +
                "WHERE documents_fts MATCH ? ORDER BY (" + inName + ") DESC, length(d.short_title) LIMIT 40";

        try (Cursor c = db.rawQuery(sql, args.toArray(new String[0]))) {
            while (c.moveToNext()) {
                results.add(DocumentSummary.fromCursor(c));
            }
        }
        return results;
    }

    public List<SectionSearchResult> searchText(String query) {
        List<SectionSearchResult> results = new ArrayList<>();
        String ftsQuery = buildFtsQuery(query);
        if (ftsQuery == null) return results;

        String sql = "SELECT sections_fts.doc_id, sections_fts.seq, sections_fts.marginal_note, " +
                "snippet(sections_fts, '[', ']', '...', 3, 20) AS snippet, " +
                "d.short_title, d.type " +
                "FROM sections_fts JOIN documents d ON d.id = sections_fts.doc_id " +
                "WHERE sections_fts MATCH ? LIMIT 50";

        try (Cursor c = db.rawQuery(sql, new String[]{ftsQuery})) {
            while (c.moveToNext()) {
                results.add(SectionSearchResult.fromCursor(c));
            }
        }
        return results;
    }

    /**
     * Turns user input into a safe FTS4 MATCH expression: only letters and digits survive (so no
     * operator or quote can break the syntax), and each word is a quoted prefix term, all required.
     */
    private String buildFtsQuery(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String t : words(raw)) {
            if (sb.length() > 0) sb.append(' ');
            sb.append('"').append(t).append("*\"");
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static List<String> words(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String t : raw.split("[^\\p{L}\\p{N}]+")) {
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    // --- Simple data holders ---

    public static class DocumentSummary {
        public String id, type, consolidatedNumber, shortTitle, longTitle;
        public int sectionCount;

        static DocumentSummary fromCursor(Cursor c) {
            DocumentSummary d = new DocumentSummary();
            d.id = c.getString(c.getColumnIndexOrThrow("id"));
            d.type = c.getString(c.getColumnIndexOrThrow("type"));
            d.consolidatedNumber = c.getString(c.getColumnIndexOrThrow("consolidated_number"));
            d.shortTitle = c.getString(c.getColumnIndexOrThrow("short_title"));
            d.longTitle = c.getString(c.getColumnIndexOrThrow("long_title"));
            d.sectionCount = c.getInt(c.getColumnIndexOrThrow("section_count"));
            return d;
        }
    }

    public static class DocumentDetail extends DocumentSummary {
        public String inForceStart, lastAmended, currentTo;
        public List<Section> sections = new ArrayList<>();

        static DocumentDetail fromCursor(Cursor c) {
            DocumentDetail d = new DocumentDetail();
            d.id = c.getString(c.getColumnIndexOrThrow("id"));
            d.type = c.getString(c.getColumnIndexOrThrow("type"));
            d.consolidatedNumber = c.getString(c.getColumnIndexOrThrow("consolidated_number"));
            d.shortTitle = c.getString(c.getColumnIndexOrThrow("short_title"));
            d.longTitle = c.getString(c.getColumnIndexOrThrow("long_title"));
            d.inForceStart = c.getString(c.getColumnIndexOrThrow("in_force_start"));
            d.lastAmended = c.getString(c.getColumnIndexOrThrow("last_amended"));
            d.currentTo = c.getString(c.getColumnIndexOrThrow("current_to"));
            d.sectionCount = c.getInt(c.getColumnIndexOrThrow("section_count"));
            return d;
        }
    }

    public static class Section {
        public int seq;
        public String label, marginalNote, headingPath, text;

        static Section fromCursor(Cursor c) {
            Section s = new Section();
            s.seq = c.getInt(c.getColumnIndexOrThrow("seq"));
            s.label = c.getString(c.getColumnIndexOrThrow("label"));
            s.marginalNote = c.getString(c.getColumnIndexOrThrow("marginal_note"));
            s.headingPath = c.getString(c.getColumnIndexOrThrow("heading_path"));
            s.text = c.getString(c.getColumnIndexOrThrow("text"));
            return s;
        }
    }

    public static class SectionSearchResult {
        public String docId, marginalNote, snippet, shortTitle, type;
        public int seq;

        static SectionSearchResult fromCursor(Cursor c) {
            SectionSearchResult r = new SectionSearchResult();
            r.docId = c.getString(c.getColumnIndexOrThrow("doc_id"));
            r.seq = c.getInt(c.getColumnIndexOrThrow("seq"));
            r.marginalNote = c.getString(c.getColumnIndexOrThrow("marginal_note"));
            r.snippet = c.getString(c.getColumnIndexOrThrow("snippet"));
            r.shortTitle = c.getString(c.getColumnIndexOrThrow("short_title"));
            r.type = c.getString(c.getColumnIndexOrThrow("type"));
            return r;
        }
    }
}
