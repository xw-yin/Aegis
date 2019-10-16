package com.beemdevelopment.aegis.helpers;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricManager;

public class BiometricHelper {
    private BiometricHelper() {

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static BiometricManager getManager(Context context) {
        BiometricManager manager = BiometricManager.from(context);
        if (manager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
            return manager;
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean isAvailable(Context context) {
        return getManager(context) != null;
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }
}
