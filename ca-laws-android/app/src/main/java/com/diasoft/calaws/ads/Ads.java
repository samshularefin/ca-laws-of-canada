package com.diasoft.calaws.ads;

import android.app.Activity;
import android.widget.FrameLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.gms.ads.AdView;

public final class Ads {

    private Ads() {}

    /**
     * Puts the adaptive banner into {@code containerId}, keeping it clear of the navigation bar.
     * Returns the AdView so the caller can forward resume/pause/destroy to it.
     */
    public static AdView attachBanner(Activity activity, int containerId) {
        FrameLayout container = activity.findViewById(containerId);
        ViewCompat.setOnApplyWindowInsetsListener(container, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });
        return AdsManager.loadAdaptiveBanner(activity, container);
    }
}
