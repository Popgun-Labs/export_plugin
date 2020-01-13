package com.SplashPad.export_plugin

import android.util.Log
import java.io.FileInputStream
import java.io.OutputStream

fun copyTo(fromPath: String, os: OutputStream) {
    if (BuildConfig.DEBUG) {
        Log.d("CopyHelper", "copyFrom: $fromPath, to: $os");
    }
    os.use {
        FileInputStream(fromPath).use { inputStream ->
            inputStream.copyTo(os)
            os.close()
        }
    }
}