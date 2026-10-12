package com.diasoft.calaws;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.diasoft.calaws.data.Prefs;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(Prefs.getNightMode(this));
    }
}
