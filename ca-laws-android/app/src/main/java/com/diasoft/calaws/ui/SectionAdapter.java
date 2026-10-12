package com.diasoft.calaws.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.diasoft.calaws.R;
import com.diasoft.calaws.data.LawsDbHelper;
import com.diasoft.calaws.data.Prefs;
import com.diasoft.calaws.data.UserDb;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SectionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_SECTION = 1;

    private final UserDb userDb;
    private LawsDbHelper.DocumentDetail header;
    private List<LawsDbHelper.Section> sections = new ArrayList<>();
    private Set<Integer> bookmarked = new HashSet<>();
    private String highlight;

    public SectionAdapter(UserDb userDb) {
        this.userDb = userDb;
    }

    public void setHeader(LawsDbHelper.DocumentDetail doc) {
        this.header = doc;
        notifyItemChanged(0);
    }

    public void setSections(List<LawsDbHelper.Section> sections) {
        this.sections = sections;
        notifyDataSetChanged();
    }

    public void setBookmarked(Set<Integer> seqs) {
        this.bookmarked = seqs;
        notifyDataSetChanged();
    }

    /** Highlights every occurrence of {@code query} in the section text; null clears it. */
    public void setHighlight(String query) {
        this.highlight = (query == null || query.isEmpty()) ? null : query;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_HEADER : TYPE_SECTION;
    }

    @Override
    public int getItemCount() {
        return sections.size() + 1; // +1 for header
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View v = inflater.inflate(R.layout.item_document_header, parent, false);
            return new HeaderViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_section, parent, false);
            return new SectionViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder && header != null) {
            HeaderViewHolder h = (HeaderViewHolder) holder;
            h.title.setText(header.longTitle);
            h.meta.setText(h.itemView.getContext().getString(
                    R.string.document_meta, header.consolidatedNumber, header.currentTo));
        } else if (holder instanceof SectionViewHolder) {
            LawsDbHelper.Section s = sections.get(position - 1);
            SectionViewHolder sh = (SectionViewHolder) holder;
            Context ctx = sh.itemView.getContext();
            float scale = Prefs.getFontScale(ctx);

            sh.heading.setText(s.headingPath);
            sh.heading.setVisibility(s.headingPath == null || s.headingPath.isEmpty()
                    ? View.GONE : View.VISIBLE);
            sh.label.setText(s.label);
            sh.label.setVisibility(s.label == null || s.label.isEmpty() ? View.GONE : View.VISIBLE);
            sh.marginalNote.setText(s.marginalNote);
            sh.marginalNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13 * scale);
            sh.marginalNote.setVisibility(s.marginalNote == null || s.marginalNote.isEmpty()
                    ? View.GONE : View.VISIBLE);

            String body = bodyText(s);
            sh.text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14 * scale);
            sh.text.setText(highlight == null ? body : highlighted(body, highlight));

            sh.bookmark.setImageResource(bookmarked.contains(s.seq)
                    ? R.drawable.ic_star_filled : R.drawable.ic_star_border);
            sh.bookmark.setOnClickListener(v -> toggleBookmark(sh, s));
            sh.copy.setOnClickListener(v -> copySection(v.getContext(), s));
            sh.share.setOnClickListener(v -> shareSection(v.getContext(), s));
        }
    }

    private void toggleBookmark(SectionViewHolder sh, LawsDbHelper.Section s) {
        if (header == null) return;
        boolean now = userDb.toggleBookmark(header.id, s.seq, header.shortTitle, s.label, s.marginalNote);
        if (now) bookmarked.add(s.seq); else bookmarked.remove(s.seq);
        int pos = sh.getBindingAdapterPosition();
        if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
        Context ctx = sh.itemView.getContext();
        Toast.makeText(ctx, now ? R.string.bookmark_added : R.string.bookmark_removed, Toast.LENGTH_SHORT).show();
    }

    /** Section text as stored begins with the marginal note and label again; drop that duplicate prefix. */
    public static String bodyText(LawsDbHelper.Section s) {
        String t = s.text == null ? "" : s.text;
        for (int i = 0; i < 2; i++) {
            if (s.marginalNote != null && !s.marginalNote.isEmpty() && t.startsWith(s.marginalNote)) {
                t = t.substring(s.marginalNote.length()).trim();
            } else if (s.label != null && !s.label.isEmpty() && t.startsWith(s.label)) {
                t = t.substring(s.label.length()).trim();
            }
        }
        return t.isEmpty() ? (s.text == null ? "" : s.text) : t;
    }

    private static CharSequence highlighted(String body, String query) {
        SpannableString sp = new SpannableString(body);
        int q = query.length();
        for (int i = 0; i + q <= body.length(); ) {
            if (body.regionMatches(true, i, query, 0, q)) {
                sp.setSpan(new BackgroundColorSpan(0xFFFFE066), i, i + q, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sp.setSpan(new ForegroundColorSpan(Color.BLACK), i, i + q, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i += q;
            } else {
                i++;
            }
        }
        return sp;
    }

    private String citation(LawsDbHelper.Section s) {
        StringBuilder sb = new StringBuilder();
        if (header != null && header.shortTitle != null) {
            sb.append(header.shortTitle);
            if (s.label != null && !s.label.isEmpty()) sb.append(", s. ").append(s.label);
            if (s.marginalNote != null && !s.marginalNote.isEmpty()) sb.append(" — ").append(s.marginalNote);
            sb.append('\n');
        }
        sb.append(bodyText(s));
        return sb.toString();
    }

    private void copySection(Context context, LawsDbHelper.Section s) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("law", citation(s)));
        Toast.makeText(context, R.string.section_copied, Toast.LENGTH_SHORT).show();
    }

    private void shareSection(Context context, LawsDbHelper.Section s) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, citation(s));
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_section)));
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView title, meta;
        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.headerTitle);
            meta = itemView.findViewById(R.id.headerMeta);
        }
    }

    static class SectionViewHolder extends RecyclerView.ViewHolder {
        TextView heading, label, marginalNote, text;
        View copy, share;
        ImageButton bookmark;
        SectionViewHolder(@NonNull View itemView) {
            super(itemView);
            copy = itemView.findViewById(R.id.sectionCopy);
            share = itemView.findViewById(R.id.sectionShare);
            bookmark = itemView.findViewById(R.id.sectionBookmark);
            heading = itemView.findViewById(R.id.sectionHeading);
            label = itemView.findViewById(R.id.sectionLabel);
            marginalNote = itemView.findViewById(R.id.sectionMarginalNote);
            text = itemView.findViewById(R.id.sectionText);
        }
    }
}
