package com.diasoft.calaws.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.diasoft.calaws.R;
import com.diasoft.calaws.data.DatabaseInstaller;

public class SplashActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView statusText;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);

        if (DatabaseInstaller.isInstalled(this)) {
            // Already installed — go straight in, check for updates quietly in the background.
            goToMain();
            checkForUpdateInBackground();
        } else {
            progressBar.setVisibility(android.view.View.VISIBLE);
            statusText.setText(R.string.downloading_database);
            downloadDatabase();
        }
    }

    private void downloadDatabase() {
        new Thread(() -> {
            String remoteVersion = null;
            try {
                remoteVersion = DatabaseInstaller.fetchRemoteVersion();
            } catch (Exception ignored) {
                // Proceed without version tracking if the version endpoint is unreachable.
            }
            final String finalVersion = remoteVersion;

            DatabaseInstaller.installOrUpdate(this, finalVersion, new DatabaseInstaller.ProgressListener() {
                @Override
                public void onProgress(int percent) {
                    mainHandler.post(() -> progressBar.setProgress(percent));
                }

                @Override
                public void onComplete(java.io.File dbFile) {
                    mainHandler.post(SplashActivity.this::goToMain);
                }

                @Override
                public void onError(Exception e) {
                    mainHandler.post(() -> statusText.setText(getString(R.string.download_failed, e.getMessage())));
                }
            });
        }).start();
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

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
