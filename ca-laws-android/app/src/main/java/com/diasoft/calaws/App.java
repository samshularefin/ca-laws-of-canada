package com.diasoft.calaws;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import com.diasoft.calaws.ads.AdsManager;
import com.diasoft.calaws.data.Prefs;

import java.lang.ref.WeakReference;

/**
 * Applies the saved theme and shows an App Open ad when the user returns to the app. The very
 * first foregrounding (the cold launch through the splash screen) is skipped here; that one is
 * handled by MainActivity so a first-time user sees the app before any full-screen ad.
 */
public class App extends Application implements Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    // Weak on purpose: this Application outlives every Activity.
    private WeakReference<Activity> currentActivity;
    private boolean isFirstForeground = true;

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(Prefs.getNightMode(this));
        registerActivityLifecycleCallbacks(this);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        if (isFirstForeground) {
            isFirstForeground = false;
            return;
        }
        Activity activity = currentActivity != null ? currentActivity.get() : null;
        if (activity != null && !activity.isFinishing()) {
            AdsManager.maybeShowAppOpenAd(activity);
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        currentActivity = new WeakReference<>(activity);
    }

    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
    @Override public void onActivityStarted(@NonNull Activity activity) {}
    @Override public void onActivityPaused(@NonNull Activity activity) {}
    @Override public void onActivityStopped(@NonNull Activity activity) {}
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
    @Override public void onActivityDestroyed(@NonNull Activity activity) {}
}
