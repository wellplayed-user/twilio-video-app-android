package io.orcana;

import android.os.Build;

import timber.log.Timber;

public final class DeviceInfo {
    private static final String DELIMITER = "$";
    private static final String VuzixBrand = "vuzix";
    private static final String LenovoBrand = "Lenovo";
    private static final String SamsungBrand = "samsung";
    private static final String GoogleBrand = "google";

    private DeviceInfo(){}

    public static boolean is(String brand){
        return brand().equals(brand);
    }

    public static boolean isVuzix(){
//        Timber.d("Is Vuzix: %s ", is(VuzixBrand));
        return is(VuzixBrand);
    }

    public static boolean isHeadset(){
        return isVuzix();
    }
    public static boolean isTablet(){
        return !isHeadset();
    }
    public static boolean isPhone(){ return is(SamsungBrand) || is(GoogleBrand); }

    public static String brand() {
        Timber.d("Device Brand: %s ", Build.BRAND);
        return Build.BRAND;
    }

    public static String brandWithDelimiter(){
        return DELIMITER + brand() + DELIMITER;
    }
}
