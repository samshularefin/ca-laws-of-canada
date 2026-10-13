package com.diasoft.calaws.ads;

import android.app.Activity;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.diasoft.calaws.R;
import com.google.android.gms.ads.AdView;

public final class Ads {

    private Ads() {}

    /**
     * Loads the banner declared in view_banner_ad.xml and keeps its container clear of the
     * navigation bar. Returns the AdView so the caller can forward resume/pause/destroy to it.
     */
    public static AdView attachBanner(Activity activity) {
        View container = activity.findViewById(R.id.bannerAdContainer);
        ViewCompat.setOnApplyWindowInsetsListener(container, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });
        AdView adView = activity.findViewById(R.id.adView);
        AdsManager.loadBanner(adView, container);
        return adView;
    }
}
