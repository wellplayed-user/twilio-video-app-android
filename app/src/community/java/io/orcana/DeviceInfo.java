package io.orcana;

import android.os.Build;

import timber.log.Timber;

public final class DeviceInfo {
    private static final String DELIMITER = "$";

    private DeviceInfo(){}

    public static boolean isVuzix(){
        Boolean result = brand().equals("vuzix");
//        Timber.d("Is Vuzix: %s ", result);
        return result;
    }

    public static boolean isHeadset(){
        return isVuzix();
    }

    public static boolean isTablet(){
        return !isHeadset();
    }

    public static String brand() {
//        Timber.d("Device Brand: %s ", Build.BRAND);
        return Build.BRAND;
    }

    public static String brandWithDelimiter(){
        return DELIMITER + brand() + DELIMITER;
    }
}
