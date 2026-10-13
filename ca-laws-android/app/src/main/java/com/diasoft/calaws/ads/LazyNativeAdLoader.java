package com.diasoft.calaws.ads;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.nativead.NativeAd;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Loads native ads for a long list one at a time, only when the user scrolls close to the next
 * ad slot - instead of requesting a whole batch up front. This keeps requests roughly equal to ads
 * actually seen (better show rate), never has more than one request in flight, and backs off after
 * no-fill so scrolling can not turn into a request storm.
 */
public class LazyNativeAdLoader {

    /** Hard cap on ads inserted into a single list, however long it is. */
    public static final int MAX_ADS_PER_LIST = 30;

    private static final int LOOKAHEAD_ITEMS = 4;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final long FAILURE_COOLDOWN_MS = 30_000L;

    private final Context context;
    private final RecyclerView recyclerView;
    private final BiConsumer<Integer, NativeAd> insertAd;

    private List<Integer> positions = new ArrayList<>();
    private int nextSlot = 0;
    private int inserted = 0;
    private boolean loading = false;
    private boolean stopped = false;
    private int consecutiveFailures = 0;
    private long nextAllowedAtMs = 0;
    private NativeAd buffered;
    private int generation = 0;

    /**
     * @param insertAd inserts the loaded ad at the given final-list position (adapter.addNativeAd);
     *                 positions are always passed in ascending order.
     */
    public LazyNativeAdLoader(Context context, RecyclerView recyclerView, BiConsumer<Integer, NativeAd> insertAd) {
        this.context = context.getApplicationContext();
        this.recyclerView = recyclerView;
        this.insertAd = insertAd;
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0) maybeLoadNext();
            }
        });
    }

    /**
     * Begins (or restarts) lazy loading for a list whose ad slots are {@code allPositions}
     * (ascending, as produced by {@link AdsManager#computeAdPositions}). Pass an empty list to
     * switch ads off for the current list, e.g. while a search is active.
     */
    public void start(List<Integer> allPositions) {
        generation++;
        discardBuffered();
        positions = new ArrayList<>(allPositions);
        nextSlot = 0;
        inserted = 0;
        consecutiveFailures = 0;
        nextAllowedAtMs = 0;
        loading = false;
        recyclerView.postDelayed(this::maybeLoadNext, 400);
    }

    /** Call when the owning screen is destroyed; late arrivals are destroyed instead of inserted. */
    public void stop() {
        stopped = true;
        generation++;
        discardBuffered();
    }

    private void discardBuffered() {
        if (buffered != null) {
            buffered.destroy();
            buffered = null;
        }
    }

    private int lastVisible() {
        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        return lm instanceof LinearLayoutManager ? ((LinearLayoutManager) lm).findLastVisibleItemPosition() : -1;
    }

    private int firstVisible() {
        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        return lm instanceof LinearLayoutManager ? ((LinearLayoutManager) lm).findFirstVisibleItemPosition() : 0;
    }

    private void maybeLoadNext() {
        if (stopped || loading || inserted >= MAX_ADS_PER_LIST || nextSlot >= positions.size()) return;
        if (!AdsManager.nativeAdsEnabled()) return;
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) return;
        if (System.currentTimeMillis() < nextAllowedAtMs) return;

        int last = lastVisible();
        if (last < 0 || last + LOOKAHEAD_ITEMS < positions.get(nextSlot)) return;

        if (buffered != null) {
            placeBuffered();
            return;
        }

        loading = true;
        final int myGeneration = generation;
        AdsManager.loadNativeAds(context, 1, ads -> {
            loading = false;
            if (stopped || myGeneration != generation) {
                for (NativeAd ad : ads) ad.destroy();
                return;
            }
            if (ads.isEmpty()) {
                consecutiveFailures++;
                nextAllowedAtMs = System.currentTimeMillis() + FAILURE_COOLDOWN_MS * consecutiveFailures;
                return;
            }
            consecutiveFailures = 0;
            buffered = ads.get(0);
            for (int i = 1; i < ads.size(); i++) ads.get(i).destroy();
            placeBuffered();
        });
    }

    /** Puts the loaded ad into the nearest slot that is still ahead of the viewport. */
    private void placeBuffered() {
        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (buffered == null || adapter == null) return;
        int first = firstVisible();
        while (nextSlot < positions.size()) {
            int position = positions.get(nextSlot);
            if (position <= first) {
                nextSlot++; // user already scrolled past this slot
                continue;
            }
            if (position > adapter.getItemCount()) return; // list shorter than expected; keep ad buffered
            NativeAd ad = buffered;
            buffered = null;
            nextSlot++;
            inserted++;
            insertAd.accept(position, ad);
            // the next slot may already be close enough to want an ad
            recyclerView.post(this::maybeLoadNext);
            return;
        }
    }
}
