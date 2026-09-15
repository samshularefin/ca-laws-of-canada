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

public class DocumentAdapter extends RecyclerView.Adapter<DocumentAdapter.ViewHolder> {

    public interface OnDocumentClick {
        void onClick(String docId);
    }

    private final List<LawsDbHelper.DocumentSummary> items = new ArrayList<>();
    private final OnDocumentClick clickListener;

    public DocumentAdapter(OnDocumentClick clickListener) {
        this.clickListener = clickListener;
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    public void addAll(List<LawsDbHelper.DocumentSummary> newItems) {
        int start = items.size();
        items.addAll(newItems);
        notifyItemRangeInserted(start, newItems.size());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_document, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LawsDbHelper.DocumentSummary doc = items.get(position);
        holder.title.setText(doc.shortTitle);
        holder.subtitle.setText(holder.itemView.getContext().getString(
                R.string.document_subtitle, doc.consolidatedNumber, doc.sectionCount));
        holder.itemView.setOnClickListener(v -> clickListener.onClick(doc.id));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, subtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.docTitle);
            subtitle = itemView.findViewById(R.id.docSubtitle);
        }
    }
}
