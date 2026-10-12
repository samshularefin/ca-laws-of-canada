package com.diasoft.calaws.ads;

import android.app.Activity;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

public final class Ads {

    private Ads() {}

    /** Loads the banner in {@code adViewId} and keeps its container clear of the navigation bar. */
    public static void attachBanner(Activity activity, int containerId, int adViewId) {
        MobileAds.initialize(activity);

        View container = activity.findViewById(containerId);
        ViewCompat.setOnApplyWindowInsetsListener(container, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });

        AdView adView = activity.findViewById(adViewId);
        adView.loadAd(new AdRequest.Builder().build());
    }
}
