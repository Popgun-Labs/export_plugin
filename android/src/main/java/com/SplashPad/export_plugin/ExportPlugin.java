package com.SplashPad.export_plugin;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.OutputStream;
import java.util.Map;

import io.flutter.BuildConfig;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

/** ExportPlugin */
public class ExportPlugin implements FlutterPlugin, MethodCallHandler, ActivityResultListener {

  private static final int CREATE_FILE = 1;

  private MethodChannel.Result result;
  private String path;
  private Registrar registrar;


  // constructor for v2 (flutterPluginBinding)
  private ExportPlugin() {
    // NOT YET IMPLEMENTED
  }

  // constructor for v1 (registerWith)
  private ExportPlugin(Registrar registrar) {
    this.registrar = registrar;
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    final MethodChannel channel = new MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "export_plugin");
    channel.setMethodCallHandler(new ExportPlugin());
  }

  // This static function is optional and equivalent to onAttachedToEngine. It supports the old
  // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
  // plugin registration via this function while apps migrate to use the new Android APIs
  // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
  //
  // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
  // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
  // depending on the user's project. onAttachedToEngine or registerWith must both be defined
  // in the same class.
  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "export_plugin");
    ExportPlugin instance = new ExportPlugin(registrar);
    registrar.addActivityResultListener(instance);
    channel.setMethodCallHandler(instance);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {

    if ("export".equals(call.method)) {
      expectMapArguments(call);

      // these are used in onActivityResult
      this.result = result;
      this.path = call.argument("path");

      // Android does not support showing the share sheet at a particular point on screen.
      // So we are not using the SharePointOrigin call.argument
      final String title = call.argument("title");
      final String mimeType = call.argument("mimeType");
      launchFilePicker(title, mimeType);
      return;
    }

    // else
    result.notImplemented();
  }

  @Override
  public boolean onActivityResult(int requestCode, int resultCode, Intent intent) {

    if (BuildConfig.DEBUG) {
        Log.d("ExportPlugin", "onActivityResult: intent = " + intent);
    }

    //GUARD : only handle create file requests
    if (requestCode != CREATE_FILE) return false;

    if (resultCode == RESULT_CANCELED) {
        // If we get a cancelled result, we should reset the result and path
        result.error("result cancelled", null, null);
        result = null;
        path = null;
        return false;
    }

    if (resultCode == RESULT_OK) {
        // GUARD : to make sure this block does not run even after cancelled
        // result is null only in two conditions, a success RESULT_OK return, or a RESULT_CANCELLED return
        if (result == null) {
            return false;
        }

        try {
            if (intent != null && intent.getData() != null) {
                writeFile(intent.getData());
                result.success("Successfully saved to local storage");
            } else {
                result.error("No directory chosen", null, null);
            }
        } catch (final Throwable e) {
            result.error("Failed to save file",null, e);
        } finally {
            result = null;
            path = null;
        }
        return true;
    }

    // Unhandled case
    return false;
  }

  private void launchFilePicker(String title, String mimeType) {
    // file picker prompt to save file
    final Intent intent = new Intent();
    intent.setAction(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    // setType does not seem to affect outcome audio/video, but we are setting it anyway
    intent.setType(mimeType);
    intent.putExtra(Intent.EXTRA_TITLE, title);

    registrar.activity().startActivityForResult(intent, CREATE_FILE);
  }

  private void writeFile(Uri uri) throws Throwable {
    final OutputStream os = registrar.activeContext().getContentResolver().openOutputStream(uri);

    //GUARD
    if (os == null) throw new NullPointerException("output stream is null");

    CopyHelperKt.copyTo(path, os);
  }

  private void expectMapArguments(MethodCall call) throws IllegalArgumentException {
    if (!(call.arguments instanceof Map)) {
      throw new IllegalArgumentException("Map argument expected");
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
  }
}