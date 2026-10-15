package com.diasoft.calaws.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioGroup;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.diasoft.calaws.R;
import com.diasoft.calaws.data.Prefs;

public final class SettingsDialog {

    private SettingsDialog() {}

    public static void show(Activity activity) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_settings, null);
        RadioGroup fontGroup = view.findViewById(R.id.fontGroup);
        RadioGroup themeGroup = view.findViewById(R.id.themeGroup);
        RadioGroup langGroup = view.findViewById(R.id.langGroup);

        float scale = Prefs.getFontScale(activity);
        fontGroup.check(scale < 0.95f ? R.id.fontSmall
                : scale < 1.1f ? R.id.fontNormal
                : scale < 1.3f ? R.id.fontLarge : R.id.fontXLarge);

        int mode = Prefs.getNightMode(activity);
        themeGroup.check(mode == AppCompatDelegate.MODE_NIGHT_NO ? R.id.themeLight
                : mode == AppCompatDelegate.MODE_NIGHT_YES ? R.id.themeDark : R.id.themeSystem);

        view.findViewById(R.id.privacyLink).setOnClickListener(v ->
                activity.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(activity.getString(R.string.privacy_policy_url)))));

        final String oldLang = Prefs.getLang(activity);
        langGroup.check("fr".equals(oldLang) ? R.id.langFr : R.id.langEn);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.settings_title)
                .setView(view)
                .setPositiveButton(R.string.ok, (d, w) -> apply(activity, fontGroup, themeGroup, langGroup, oldLang))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void apply(Activity activity, RadioGroup fontGroup, RadioGroup themeGroup,
                              RadioGroup langGroup, String oldLang) {
        int fontId = fontGroup.getCheckedRadioButtonId();
        Prefs.setFontScale(activity, fontId == R.id.fontSmall ? 0.9f
                : fontId == R.id.fontLarge ? 1.2f
                : fontId == R.id.fontXLarge ? 1.4f : 1f);

        int themeId = themeGroup.getCheckedRadioButtonId();
        int mode = themeId == R.id.themeLight ? AppCompatDelegate.MODE_NIGHT_NO
                : themeId == R.id.themeDark ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        Prefs.setNightMode(activity, mode);

        String newLang = langGroup.getCheckedRadioButtonId() == R.id.langFr ? "fr" : "en";
        if (!newLang.equals(oldLang)) {
            Prefs.setLang(activity, newLang);
            // The splash screen downloads the other edition first if it is not installed yet.
            Intent restart = new Intent(activity, SplashActivity.class);
            restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(restart);
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLang));
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
