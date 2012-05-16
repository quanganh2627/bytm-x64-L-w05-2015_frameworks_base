package com.android.server.am;
import android.util.Slog;

public class DebugAnr{
    static final String TAG = "DebugAnr";

    public DebugAnr(){
        Slog.i(TAG, "DebugAnr");
    }

    public native void logToFile(String name);
    public native void logname(String name, String name1);

    static{
        System.loadLibrary("debug_anr");
    }
}
