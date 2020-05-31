package com.ejlchina.okhttps;

import java.lang.reflect.Field;

public class Platform {

    /**
     * Android 的 SDK 版本，若不是 Android 平台，则为 0
     */
    public static final int ANDROID_SDK_INT = getAndroidSdkInt();


    private static int getAndroidSdkInt() {
        try {
            Class<?> versionClass = Class.forName("android.os.Build$VERSION");
            Field field = versionClass.getDeclaredField("SDK_INT");
            return field.getInt(field);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            return 0;
        }
    }

}
