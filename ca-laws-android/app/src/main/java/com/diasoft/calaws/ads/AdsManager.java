package com.diasoft.calaws.ads;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.material.button.MaterialButton;
import com.diasoft.calaws.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/** Central place for initializing Google Mobile Ads and loading banner/native/interstitial/app-open ads. */
public class AdsManager {

    private static final int NATIVE_AD_INTERVAL = 15;
    private static final int INTERSTITIAL_INTERVAL = 3;
    private static final long INTERSTITIAL_EXPIRY_MS = 60 * 60 * 1000L;
    private static final int MAX_LOAD_RETRIES = 5;
    private static final long RETRY_BASE_DELAY_MS = 10_000L;
    private static final long RETRY_MAX_DELAY_MS = 60_000L;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static boolean initialized = false;
    private static boolean canRequestAds = false;
    private static final List<WeakReference<AdView>> pendingBanners = new ArrayList<>();

    private AdsManager() {
    }

    /** Exponential backoff (10s, 20s, 40s, then 60s) so failed loads are retried without hammering AdMob. */
    private static long retryDelayMs(int attempt) {
        return Math.min(RETRY_MAX_DELAY_MS, RETRY_BASE_DELAY_MS << Math.min(attempt - 1, 3));
    }

    /** Call once consent has been resolved and allows ad requests (see {@link ConsentManager}). */
    public static void initialize(Context context) {
        canRequestAds = true;
        if (!initialized) {
            initialized = true;
            MobileAds.setRequestConfiguration(new RequestConfiguration.Builder()
                    .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_T)
                    .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
                    .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE)
                    .build());
            MobileAds.initialize(context.getApplicationContext(), initializationStatus -> { /* ready */ });
        }

        // Banners created before consent resolved (e.g. the splash consent timeout elapsed) load now.
        List<WeakReference<AdView>> toLoad = new ArrayList<>(pendingBanners);
        pendingBanners.clear();
        for (WeakReference<AdView> ref : toLoad) {
            AdView adView = ref.get();
            if (adView != null && adView.isAttachedToWindow()) adView.loadAd(new AdRequest.Builder().build());
        }
    }

    /**
     * Loads the standard banner declared in the screen's XML (an {@code AdView} with a fixed size and
     * ad unit) and shows {@code container} only once an ad is actually available. If consent has not
     * resolved yet the banner loads as soon as it does; failed loads are retried with backoff.
     */
    public static void loadBanner(AdView adView, View container) {
        AtomicInteger attempts = new AtomicInteger(0);
        adView.setAdListener(new AdListener() {
            @Override
            public void onAdFailedToLoad(LoadAdError error) {
                container.setVisibility(View.GONE);
                int attempt = attempts.incrementAndGet();
                if (attempt > MAX_LOAD_RETRIES) return;
                mainHandler.postDelayed(() -> {
                    if (adView.isAttachedToWindow()) adView.loadAd(new AdRequest.Builder().build());
                }, retryDelayMs(attempt));
            }

            @Override
            public void onAdLoaded() {
                attempts.set(0);
                container.setVisibility(View.VISIBLE);
            }
        });

        if (canRequestAds) {
            container.setVisibility(View.VISIBLE);
            adView.loadAd(new AdRequest.Builder().build());
        } else {
            container.setVisibility(View.GONE);
            pendingBanners.add(new WeakReference<>(adView));
        }
    }

    public interface NativeAdsCallback {
        void onLoaded(List<NativeAd> ads);
    }

    /** Safety net for loadNativeAdsBatch - see the comment on its use below. */
    private static final long NATIVE_AD_LOAD_TIMEOUT_MS = 5000;

    /** AdMob's own limit on how many native ads a single AdLoader.loadAds() call can request. */
    private static final int MAX_ADS_PER_BATCH = 5;

    /** Sensible density cap regardless of how long the underlying list is (e.g. 1670 laws). */
    public static final int MAX_NATIVE_ADS_PER_LIST = 4;

    /**
     * Loads up to {@code count} distinct native ads - callers don't need to worry about AdMob's
     * 5-per-call limit; this fans out into multiple parallel batches when count exceeds it and
     * combines the results. The callback fires exactly once, with however many actually loaded
     * (0 if native ads are disabled, none load, or count &lt;= 0) - callers should treat a
     * short/empty list as "skip those slots," not as an error. Every NativeAd handed back must be
     * destroyed by the caller ({@code nativeAd.destroy()}) once no longer displayed.
     *
     * Ad rows are never inserted into a RecyclerView until the ads they show already exist - so
     * there's no lazy per-row loading, no placeholder state, and no risk of a view being recycled
     * out from under an in-flight request.
     */
    public static void loadNativeAds(Context context, int count, NativeAdsCallback callback) {
        if (count <= 0 || !nativeAdsEnabled()) {
            callback.onLoaded(new ArrayList<>());
            return;
        }
        if (count <= MAX_ADS_PER_BATCH) {
            loadNativeAdsBatch(context, count, callback);
            return;
        }

        int numBatches = (count + MAX_ADS_PER_BATCH - 1) / MAX_ADS_PER_BATCH;
        List<NativeAd> combined = new ArrayList<>();
        AtomicInteger batchesRemaining = new AtomicInteger(numBatches);
        for (int b = 0; b < numBatches; b++) {
            int batchCount = Math.min(MAX_ADS_PER_BATCH, count - b * MAX_ADS_PER_BATCH);
            loadNativeAdsBatch(context, batchCount, ads -> {
                synchronized (combined) {
                    combined.addAll(ads);
                }
                if (batchesRemaining.decrementAndGet() == 0) callback.onLoaded(new ArrayList<>(combined));
            });
        }
    }

    /** Loads up to MAX_ADS_PER_BATCH native ads in one AdLoader call - see loadNativeAds() for the public, uncapped entry point. */
    private static void loadNativeAdsBatch(Context context, int count, NativeAdsCallback callback) {
        List<NativeAd> loaded = new ArrayList<>();
        AtomicInteger responses = new AtomicInteger(0);
        AtomicBoolean delivered = new AtomicBoolean(false);
        Handler mainHandler = new Handler(Looper.getMainLooper());
        Runnable deliver = () -> {
            // Only the first of (all-responses-in / timeout) actually delivers - whichever loses
            // just finds delivered already true and no-ops.
            if (delivered.compareAndSet(false, true)) callback.onLoaded(new ArrayList<>(loaded));
        };

        AdLoader adLoader = new AdLoader.Builder(context, context.getString(R.string.native_ad_unit_id))
                .forNativeAd(nativeAd -> {
                    if (delivered.get()) {
                        // Arrived after the timeout already delivered a (possibly shorter) list -
                        // nobody's holding a reference to destroy this one, so it must be destroyed here.
                        nativeAd.destroy();
                        return;
                    }
                    loaded.add(nativeAd);
                    if (responses.incrementAndGet() >= count) deliver.run();
                })
                .withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        // This one slot failed; the rest of the batch still reports in
                        // independently, so just count it and move on. On a whole-batch failure
                        // (offline, no fill at all) AdMob can collapse this to a single call
                        // instead of `count` of them - the timeout below is what saves us there.
                        if (responses.incrementAndGet() >= count) deliver.run();
                    }
                })
                .withNativeAdOptions(new NativeAdOptions.Builder().build())
                .build();
        adLoader.loadAds(new AdRequest.Builder().build(), count);

        mainHandler.postDelayed(deliver, NATIVE_AD_LOAD_TIMEOUT_MS);
    }

    /**
     * Where to insert native ads among {@code itemCount} rows: after every {@code interval} items.
     * Each returned position already accounts for the ad slots placed before it, so it's ready to
     * hand straight to an adapter's addNativeAd(position, ad) in ascending order.
     */
    public static List<Integer> computeAdPositions(int itemCount, int interval) {
        List<Integer> positions = new ArrayList<>();
        if (interval <= 0) return positions;
        int inserted = 0;
        int originalIndex = interval;
        while (originalIndex < itemCount) {
            positions.add(originalIndex + inserted);
            inserted++;
            originalIndex += interval;
        }
        return positions;
    }

    /** Same as {@link #computeAdPositions(int, int)} but only counts items matching {@code isEligible} toward the interval. */
    public static <T> List<Integer> computeAdPositions(List<T> items, Predicate<T> isEligible, int interval) {
        List<Integer> positions = new ArrayList<>();
        if (interval <= 0) return positions;
        int eligibleCount = 0;
        int inserted = 0;
        for (int i = 0; i < items.size(); i++) {
            if (isEligible.test(items.get(i))) {
                eligibleCount++;
                if (eligibleCount % interval == 0) {
                    positions.add(i + 1 + inserted);
                    inserted++;
                }
            }
        }
        return positions;
    }

    /** Wires a loaded {@link NativeAd}'s assets into the standard item_native_ad.xml template. */
    public static void populateNativeAdView(NativeAdView adView, NativeAd ad) {
        // Guards against redundant rebinds - e.g. a theme/font-size change calling
        // notifyDataSetChanged() would otherwise call NativeAdView.setNativeAd() again on the
        // exact same ad every time.
        if (adView.getTag(R.id.tag_native_ad_slot_key) == ad) return;
        adView.setTag(R.id.tag_native_ad_slot_key, ad);

        TextView headline = adView.findViewById(R.id.adHeadline);
        TextView body = adView.findViewById(R.id.adBody);
        ImageView icon = adView.findViewById(R.id.adAppIcon);
        MediaView media = adView.findViewById(R.id.adMediaView);
        MaterialButton cta = adView.findViewById(R.id.adCallToAction);

        adView.setHeadlineView(headline);
        adView.setBodyView(body);
        adView.setIconView(icon);
        adView.setMediaView(media);
        adView.setCallToActionView(cta);

        headline.setText(ad.getHeadline());

        if (ad.getBody() == null) {
            body.setVisibility(View.GONE);
        } else {
            body.setVisibility(View.VISIBLE);
            body.setText(ad.getBody());
        }

        if (ad.getIcon() == null) {
            icon.setVisibility(View.GONE);
        } else {
            icon.setVisibility(View.VISIBLE);
            icon.setImageDrawable(ad.getIcon().getDrawable());
        }

        if (ad.getCallToAction() == null) {
            cta.setVisibility(View.GONE);
        } else {
            cta.setVisibility(View.VISIBLE);
            cta.setText(ad.getCallToAction());
        }

        adView.setNativeAd(ad);
    }

    public static boolean nativeAdsEnabled() {
        return canRequestAds;
    }

    public static int nativeAdInterval() {
        return NATIVE_AD_INTERVAL;
    }

    private static InterstitialAd cachedInterstitial;
    private static long interstitialLoadTimeMs = 0;
    private static boolean isLoadingInterstitial = false;
    private static int interstitialLoadAttempts = 0;
    private static int exitsSinceInterstitial = 0;

    private static boolean isInterstitialFresh() {
        return cachedInterstitial != null
                && (System.currentTimeMillis() - interstitialLoadTimeMs) < INTERSTITIAL_EXPIRY_MS;
    }

    /** Ensures one fresh interstitial is loaded (or loading) so it is ready the next time one is due. */
    public static void preloadInterstitial(Context context) {
        interstitialLoadAttempts = 0;
        loadInterstitial(context.getApplicationContext());
    }

    private static void loadInterstitial(Context appContext) {
        if (!canRequestAds || isInterstitialFresh() || isLoadingInterstitial) return;
        isLoadingInterstitial = true;
        InterstitialAd.load(appContext, appContext.getString(R.string.interstitial_ad_unit_id),
                new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        isLoadingInterstitial = false;
                        interstitialLoadAttempts = 0;
                        cachedInterstitial = ad;
                        interstitialLoadTimeMs = System.currentTimeMillis();
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        isLoadingInterstitial = false;
                        cachedInterstitial = null;
                        int attempt = ++interstitialLoadAttempts;
                        if (attempt <= MAX_LOAD_RETRIES) {
                            mainHandler.postDelayed(() -> loadInterstitial(appContext), retryDelayMs(attempt));
                        }
                    }
                });
    }

    /**
     * Shows a preloaded interstitial once INTERSTITIAL_INTERVAL law-detail exits have
     * accumulated, then always runs {@code onProceed} - whether or not an ad was shown - so the
     * caller's navigation never gets stuck waiting on ad state. If an ad is due but was not ready,
     * the due-ness carries over to the next exit (instead of being skipped) while a fresh load starts.
     */
    public static void maybeShowInterstitial(Activity activity, Runnable onProceed) {
        exitsSinceInterstitial++;
        if (!canRequestAds || exitsSinceInterstitial < INTERSTITIAL_INTERVAL) {
            onProceed.run();
            return;
        }
        if (!isInterstitialFresh() || activity.isFinishing() || activity.isDestroyed()) {
            preloadInterstitial(activity);
            onProceed.run();
            return;
        }

        exitsSinceInterstitial = 0;
        InterstitialAd ad = cachedInterstitial;
        cachedInterstitial = null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                preloadInterstitial(activity);
                onProceed.run();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                preloadInterstitial(activity);
                onProceed.run();
            }
        });
        ad.show(activity);
    }

    private static AppOpenAd cachedAppOpenAd;
    private static long appOpenLoadTimeMs = 0;
    private static boolean isShowingAppOpenAd = false;
    private static boolean isLoadingAppOpenAd = false;
    private static int appOpenLoadAttempts = 0;
    private static WeakReference<Activity> pendingColdLaunchActivity;
    private static final long APP_OPEN_EXPIRY_MS = 4 * 60 * 60 * 1000L; // Google's recommended cache window

    /** Preloads one App Open ad so it is ready the next time the app comes back to the foreground. */
    public static void preloadAppOpenAd(Context context) {
        appOpenLoadAttempts = 0;
        loadAppOpenAd(context.getApplicationContext());
    }

    private static void loadAppOpenAd(Context appContext) {
        if (!canRequestAds || isAppOpenAdFresh() || isLoadingAppOpenAd) return;
        isLoadingAppOpenAd = true;
        AppOpenAd.load(appContext, appContext.getString(R.string.app_open_ad_unit_id), new AdRequest.Builder().build(),
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull AppOpenAd ad) {
                        isLoadingAppOpenAd = false;
                        appOpenLoadAttempts = 0;
                        cachedAppOpenAd = ad;
                        appOpenLoadTimeMs = System.currentTimeMillis();

                        // The ad may have finished loading just after a cold-launch show attempt
                        // found nothing ready - if so, this is the retry that actually shows it,
                        // instead of silently burning the one-per-launch opportunity.
                        if (coldLaunchAdPending && pendingColdLaunchActivity != null) {
                            Activity target = pendingColdLaunchActivity.get();
                            pendingColdLaunchActivity = null;
                            if (target != null && !target.isFinishing()) {
                                coldLaunchAdPending = false;
                                maybeShowAppOpenAd(target);
                            }
                        }
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        isLoadingAppOpenAd = false;
                        cachedAppOpenAd = null;
                        int attempt = ++appOpenLoadAttempts;
                        if (attempt <= MAX_LOAD_RETRIES) {
                            mainHandler.postDelayed(() -> loadAppOpenAd(appContext), retryDelayMs(attempt));
                        }
                    }
                });
    }

    private static boolean isAppOpenAdFresh() {
        return cachedAppOpenAd != null && (System.currentTimeMillis() - appOpenLoadTimeMs) < APP_OPEN_EXPIRY_MS;
    }

    /**
     * Shows a preloaded App Open ad, if one is cached and not stale.
     * No-ops (and just kicks off a fresh preload) if nothing is ready - the caller (a background
     * -> foreground transition) never needs to wait on this.
     */
    public static void maybeShowAppOpenAd(Activity activity) {
        if (isShowingAppOpenAd || !canRequestAds || !isAppOpenAdFresh()
                || activity.isFinishing() || activity.isDestroyed()) {
            preloadAppOpenAd(activity);
            return;
        }

        AppOpenAd ad = cachedAppOpenAd;
        isShowingAppOpenAd = true;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                isShowingAppOpenAd = false;
                cachedAppOpenAd = null;
                preloadAppOpenAd(activity);
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                isShowingAppOpenAd = false;
                cachedAppOpenAd = null;
                preloadAppOpenAd(activity);
            }
        });
        ad.show(activity);
    }

    private static boolean coldLaunchAdPending = true;

    /**
     * Call once per cold launch (from SplashActivity) so the app-open ad can show once, right
     * after splash, rather than being permanently skipped for first-time users.
     */
    public static void resetColdLaunchGate() {
        coldLaunchAdPending = true;
    }

    /**
     * Shows the app-open ad once for this cold launch (e.g. from the first screen's onResume),
     * then no-ops on every subsequent call until {@link #resetColdLaunchGate} runs again.
     * Background->foreground returns after that are handled separately by {@link #maybeShowAppOpenAd}.
     */
    public static void maybeShowAppOpenAdOnColdLaunch(Activity activity) {
        if (!coldLaunchAdPending) return;
        if (isAppOpenAdFresh()) {
            coldLaunchAdPending = false;
            maybeShowAppOpenAd(activity);
            return;
        }
        // Not loaded yet - keep the gate open and show it the moment it finishes loading
        // (see loadAppOpenAd's onAdLoaded) instead of giving up on this launch entirely.
        pendingColdLaunchActivity = new WeakReference<>(activity);
        preloadAppOpenAd(activity);
    }
}
