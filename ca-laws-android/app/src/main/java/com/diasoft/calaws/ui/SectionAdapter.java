package com.diasoft.calaws.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.diasoft.calaws.R;
import com.diasoft.calaws.data.LawsDbHelper;

import java.util.ArrayList;
import java.util.List;

public class SectionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_SECTION = 1;

    private LawsDbHelper.DocumentDetail header;
    private List<LawsDbHelper.Section> sections = new ArrayList<>();

    public void setHeader(LawsDbHelper.DocumentDetail doc) {
        this.header = doc;
        notifyItemChanged(0);
    }

    public void setSections(List<LawsDbHelper.Section> sections) {
        this.sections = sections;
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
            sh.heading.setText(s.headingPath);
            sh.heading.setVisibility(s.headingPath == null || s.headingPath.isEmpty()
                    ? View.GONE : View.VISIBLE);
            sh.label.setText(s.label);
            sh.label.setVisibility(s.label == null || s.label.isEmpty() ? View.GONE : View.VISIBLE);
            sh.marginalNote.setText(s.marginalNote);
            sh.marginalNote.setVisibility(s.marginalNote == null || s.marginalNote.isEmpty()
                    ? View.GONE : View.VISIBLE);
            sh.text.setText(s.text);
        }
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
        SectionViewHolder(@NonNull View itemView) {
            super(itemView);
            heading = itemView.findViewById(R.id.sectionHeading);
            label = itemView.findViewById(R.id.sectionLabel);
            marginalNote = itemView.findViewById(R.id.sectionMarginalNote);
            text = itemView.findViewById(R.id.sectionText);
        }
    }
}
