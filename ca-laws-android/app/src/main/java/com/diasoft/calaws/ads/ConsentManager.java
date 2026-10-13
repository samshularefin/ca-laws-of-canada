package com.diasoft.calaws.ads;

import android.app.Activity;

import com.google.android.ump.ConsentDebugSettings;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

/**
 * Wraps Google's User Messaging Platform (UMP) SDK so EEA/UK users are shown the GDPR consent
 * form before any ad request is made, per Google Play / AdMob policy. Outside those regions the
 * SDK determines consent isn't required and this resolves immediately.
 */
public class ConsentManager {

    public interface OnConsentReady {
        void onReady(boolean canRequestAds);
    }

    public static void requestConsent(Activity activity, OnConsentReady callback) {
        ConsentInformation consentInformation = UserMessagingPlatform.getConsentInformation(activity);

        ConsentRequestParameters.Builder paramsBuilder = new ConsentRequestParameters.Builder();
        // Uncomment during development to force the EEA consent dialog on a test device:
        // paramsBuilder.setConsentDebugSettings(new ConsentDebugSettings.Builder(activity)
        //         .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
        //         .addTestDeviceHashedId("TEST_DEVICE_HASHED_ID")
        //         .build());
        ConsentRequestParameters params = paramsBuilder.build();

        consentInformation.requestConsentInfoUpdate(activity, params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, formError ->
                        callback.onReady(consentInformation.canRequestAds())),
                requestError -> callback.onReady(consentInformation.canRequestAds()));

        // If consent was already gathered on a previous run, canRequestAds() is already true and
        // the form load above will simply no-op - so surface that immediately too.
        if (consentInformation.canRequestAds()) {
            callback.onReady(true);
        }
    }
}
