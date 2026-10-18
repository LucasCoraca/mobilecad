package org.qtproject.qt.android;

import android.app.Activity;

/**
 * FreeCAD's App layer uses QtCore, whose Android backend (QStandardPaths, timezones, ...) reaches Java through
 * QtNative. The app is not a QtActivity, so register our Activity with QtNative before QtCore's JNI_OnLoad runs.
 * Lives in Qt's package because QtNative's setters are package-private.
 */
public final class QtHostAccess {
    private QtHostAccess() {}

    public static void attach(Activity activity) {
        QtNative.setClassLoader(activity.getClassLoader());
        QtNative.setActivity(activity);
    }
}
