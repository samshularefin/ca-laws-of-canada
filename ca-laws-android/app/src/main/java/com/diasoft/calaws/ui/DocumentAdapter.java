package com.diasoft.calaws.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.diasoft.calaws.R;
import com.diasoft.calaws.ads.AdsManager;
import com.diasoft.calaws.data.LawsDbHelper;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Law list. Native ad rows are a position overlay on top of the data (see {@link #addNativeAd}),
 * so an ad is only inserted once it has fully loaded.
 */
public class DocumentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnDocumentClick {
        void onClick(String docId);
    }

    private static final int TYPE_DOC = 0;
    private static final int TYPE_NATIVE_AD = 1;

    private final List<LawsDbHelper.DocumentSummary> items = new ArrayList<>();
    private final OnDocumentClick clickListener;
    /** Final-list position -> native ad shown there. */
    private final Map<Integer, NativeAd> adSlots = new HashMap<>();

    public DocumentAdapter(OnDocumentClick clickListener) {
        this.clickListener = clickListener;
    }

    public void clear() {
        items.clear();
        destroyAds();
        notifyDataSetChanged();
    }

    public void addAll(List<LawsDbHelper.DocumentSummary> newItems) {
        int start = getItemCount();
        items.addAll(newItems);
        notifyItemRangeInserted(start, newItems.size());
    }

    /** Inserts an already-loaded native ad at {@code position}; positions arrive in ascending order. */
    public void addNativeAd(int position, NativeAd ad) {
        adSlots.put(position, ad);
        notifyItemInserted(position);
    }

    /** Releases every loaded native ad; call when the list is reset or the screen is destroyed. */
    public void destroyAds() {
        for (NativeAd ad : adSlots.values()) ad.destroy();
        adSlots.clear();
    }

    private int toDataIndex(int position) {
        int adsBefore = 0;
        for (Integer adPos : adSlots.keySet()) {
            if (adPos < position) adsBefore++;
        }
        return position - adsBefore;
    }

    @Override
    public int getItemViewType(int position) {
        return adSlots.containsKey(position) ? TYPE_NATIVE_AD : TYPE_DOC;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_NATIVE_AD) {
            return new NativeAdVH(inflater.inflate(R.layout.item_native_ad, parent, false));
        }
        return new ViewHolder(inflater.inflate(R.layout.item_document, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof NativeAdVH) {
            AdsManager.populateNativeAdView(((NativeAdVH) holder).adView, adSlots.get(position));
            return;
        }
        ViewHolder vh = (ViewHolder) holder;
        LawsDbHelper.DocumentSummary doc = items.get(toDataIndex(position));
        vh.title.setText(doc.shortTitle);
        vh.subtitle.setText(vh.itemView.getContext().getString(
                R.string.document_subtitle, doc.consolidatedNumber, doc.sectionCount));
        vh.itemView.setOnClickListener(v -> clickListener.onClick(doc.id));
    }

    @Override
    public int getItemCount() {
        return items.size() + adSlots.size();
    }

    static class NativeAdVH extends RecyclerView.ViewHolder {
        final NativeAdView adView;

        NativeAdVH(@NonNull View itemView) {
            super(itemView);
            adView = (NativeAdView) itemView;
        }
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
