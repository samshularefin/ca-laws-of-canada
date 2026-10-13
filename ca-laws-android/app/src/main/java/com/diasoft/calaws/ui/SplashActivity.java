package com.diasoft.calaws.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.diasoft.calaws.R;
import com.diasoft.calaws.ads.AdsManager;
import com.diasoft.calaws.ads.ConsentManager;
import com.diasoft.calaws.data.DatabaseInstaller;
import com.diasoft.calaws.data.DownloadService;

public class SplashActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView statusText;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final long CONSENT_TIMEOUT_MS = 3000;
    private boolean navigated;
    private boolean consentResolved;
    private Runnable consentTimeout;

    private final DownloadService.Listener serviceListener = new DownloadService.Listener() {
        @Override
        public void onProgress(int percent, long downloadedBytes, long totalBytes) {
            mainHandler.post(() -> showProgress(percent, downloadedBytes, totalBytes));
        }

        @Override
        public void onComplete() {
            mainHandler.post(SplashActivity.this::maybeGoToMain);
        }

        @Override
        public void onError(String message) {
            mainHandler.post(() -> showError(message));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);

        // Consent (EEA/UK form if required) must be settled before any ad request; never let it block entry for long.
        AdsManager.resetColdLaunchGate();
        ConsentManager.requestConsent(this, canRequestAds -> mainHandler.post(() -> {
            consentResolved = true;
            if (canRequestAds) {
                AdsManager.initialize(this);
                AdsManager.preloadInterstitial(this);
                AdsManager.preloadAppOpenAd(this);
            }
            maybeGoToMain();
        }));
        consentTimeout = () -> {
            consentResolved = true;
            maybeGoToMain();
        };
        mainHandler.postDelayed(consentTimeout, CONSENT_TIMEOUT_MS);

        if (DatabaseInstaller.isInstalled(this)) {
            // Already installed — enter once consent is settled; check for updates quietly meanwhile.
            checkForUpdateInBackground();
        } else {
            progressBar.setVisibility(View.VISIBLE);
            statusText.setText(R.string.downloading_database);
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
            startDownload();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (navigated) return;
        DownloadService.setListener(serviceListener);
        // Catch up with anything that happened while this screen was not visible.
        if (DatabaseInstaller.isInstalled(this) && !DownloadService.running) {
            maybeGoToMain();
        } else if (DownloadService.error != null) {
            showError(DownloadService.error);
        } else if (DownloadService.running && DownloadService.total > 0) {
            showProgress(DownloadService.percent, DownloadService.downloaded, DownloadService.total);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        DownloadService.setListener(null);
    }

    private void startDownload() {
        if (!DownloadService.running) {
            ContextCompat.startForegroundService(this, new Intent(this, DownloadService.class));
        }
    }

    private void showProgress(int percent, long downloadedBytes, long totalBytes) {
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(percent);
        statusText.setOnClickListener(null);
        statusText.setText(getString(R.string.download_progress, percent,
                downloadedBytes / 1048576f, totalBytes / 1048576f));
    }

    private void showError(String message) {
        statusText.setText(getString(R.string.download_failed, message));
        statusText.setOnClickListener(v -> {
            statusText.setOnClickListener(null);
            statusText.setText(R.string.downloading_database);
            startDownload();
        });
    }

    private void checkForUpdateInBackground() {
        new Thread(() -> {
            try {
                String remote = DatabaseInstaller.fetchRemoteVersion();
                String local = DatabaseInstaller.getInstalledVersion(this);
                if (remote != null && !remote.equals(local)) {
                    // A newer consolidation is available; silently refresh for next launch.
                    DatabaseInstaller.installOrUpdate(this, remote, null);
                }
            } catch (Exception ignored) {
                // Offline or host unreachable — app continues to work with the existing DB.
            }
        }).start();
    }

    private void maybeGoToMain() {
        if (navigated || !consentResolved || DownloadService.running || !DatabaseInstaller.isInstalled(this)) return;
        navigated = true;
        if (consentTimeout != null) mainHandler.removeCallbacks(consentTimeout);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
