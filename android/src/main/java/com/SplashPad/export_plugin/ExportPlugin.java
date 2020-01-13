package com.SplashPad.export_plugin;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;
import io.flutter.plugin.common.PluginRegistry.Registrar;

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
      launchFilePicker((String) call.argument("title"));
      return;
    }

    if ("open".equals(call.method)) {
      expectMapArguments(call);
      // Android does not support showing the share sheet at a particular point on screen.
      try {
        openFile(
                (String) call.argument("uri")
        );
        result.success(null);
      } catch (IOException e) {
        result.error(e.getMessage(), null, null);
      }
      return;
    }

    // else
    result.notImplemented();
  }

  @Override
  public boolean onActivityResult(int requestCode, int resultCode, Intent intent) {

    //GUARD
    if (requestCode != CREATE_FILE) return false;

    if (BuildConfig.DEBUG) {
      Log.d("ExportPlugin", "onActivityResult: intent = " + intent);
    }

    //GUARD
    if (intent == null || intent.getData() == null) {
      result.error(null, "No directory chosen", null);
      return true;
    }

    try {
      writeFile(intent.getData());
      result.success("Successfully saved to local storage");
    } catch (final Throwable e) {
      result.error(null,"Failed to save file", e);
    } finally {
      result = null;
      path = null;
    }

    return true;
  }

  private void launchFilePicker(String title) {
    // file picker prompt to save file
    final Intent intent = new Intent();
    intent.setAction(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("audio/wav");
    intent.putExtra(Intent.EXTRA_TITLE, title);

    registrar.activity().startActivityForResult(intent, CREATE_FILE);
  }

  private void openFile(String uri) throws IOException {
    if (uri == null || uri.isEmpty()) {
      throw new IllegalArgumentException("Non-empty path expected");
    }

    Intent intent = new Intent();
    intent.setAction(Intent.ACTION_GET_CONTENT);
    intent.setDataAndType(Uri.parse(uri), "audio/wav");

    registrar.activeContext().startActivity(intent);
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