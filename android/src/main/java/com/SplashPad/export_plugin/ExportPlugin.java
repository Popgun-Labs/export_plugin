package com.SplashPad.export_plugin;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.OutputStream;
import java.util.Map;

import io.flutter.BuildConfig;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

/** ExportPlugin */
public class ExportPlugin implements FlutterPlugin, MethodCallHandler, ActivityResultListener, ActivityAware {

  // this is so we can keep both the result and the path together
  // prevents us getting into a weird state where we have 1 but not
  // the other
  class ExportJob {
    final MethodChannel.Result result;
    final String path;

    ExportJob(Result result, String path) {
      this.result = result;
      this.path = path;
    }
  }

  private static final int CREATE_FILE = 1;

  private ExportJob exportJob;
  private Registrar registrar;
  private Context activeContext;
  private ExportPlugin instance;
  private ActivityPluginBinding activityBinding;

  // returns the current job and sets it to null
  private ExportJob consumeJob() {
    final ExportJob job = this.exportJob;
    this.exportJob = null;
    return job;
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    final MethodChannel channel = new MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "export_plugin");
    instance = new ExportPlugin();
    channel.setMethodCallHandler(instance);

    this.activeContext = flutterPluginBinding.getApplicationContext();
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
  public void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "export_plugin");
    instance = new ExportPlugin();
    registrar.addActivityResultListener(instance);
    channel.setMethodCallHandler(instance);

    this.registrar = registrar;
    this.activeContext = registrar.activeContext();
  }

  @Override
  public void onAttachedToActivity(ActivityPluginBinding activityPluginBinding) {
    activityBinding = activityPluginBinding;
    activityPluginBinding.addActivityResultListener(instance);
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity();
  }

  @Override
  public void onReattachedToActivityForConfigChanges(ActivityPluginBinding activityPluginBinding) {
    onAttachedToActivity(activityPluginBinding);
  }

  @Override
  public void onDetachedFromActivity() {
    activityBinding.removeActivityResultListener(instance);
    instance = null;
    // todo clean up refs
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {

    if ("export".equals(call.method)) {
      expectMapArguments(call);

      // these are used in onActivityResult
      this.exportJob = new ExportJob(result, (String) call.argument("path"));

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
      Log.d("ExportPlugin", "onActivityResult: requestCode = " + requestCode);
      Log.d("ExportPlugin", "onActivityResult: resultCode = " + resultCode);
      Log.d("ExportPlugin", "onActivityResult: intent = " + intent);
    }

    //GUARD : only handle create file requests
    if (requestCode != CREATE_FILE) return false;

    //GUARD : make sure we have a job
    final ExportJob job = consumeJob();
    if (BuildConfig.DEBUG) Log.d("ExportPlugin", "onActivityResult: exportJob = " + job);
    if (job == null) return false;

    // -- past this point we are consuming the result

    final Result result = job.result;
    final String path = job.path;

    try {

      if (resultCode == RESULT_CANCELED) {
        result.error("cancelled", "User cancelled the export", null);
      }

      else if (resultCode == RESULT_OK) {
        final Uri data = intent != null ? intent.getData() : null;
        if (data != null) {
          writeFile(activeContext, path, data);
          result.success("Successfully saved to local storage");
        } else {
          result.error("no_directory", "No directory chosen", null);
        }
      }

      else {
        // Unhandled case
        result.error("unknown_result_code", "Unknown result code returned by OS: " + resultCode, null);
      }
    } catch (final Throwable e) {
      result.error("save_failed", e.getMessage(), e);
    }

    return true;
  }

  private void launchFilePicker(String title, String mimeType) {
    // file picker prompt to save file
    final Intent intent = new Intent();
    intent.setAction(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    // setType does not seem to affect outcome audio/video, but we are setting it anyway
    intent.setType(mimeType);
    intent.putExtra(Intent.EXTRA_TITLE, title);

    if (registrar != null) {
      registrar.activity().startActivityForResult(intent, CREATE_FILE);
    } else {
      activityBinding.getActivity().startActivityForResult(intent, CREATE_FILE);
    }

  }

  static private void writeFile(Context context, String fromPath, Uri uri) throws Throwable {
    final OutputStream os = context.getContentResolver().openOutputStream(uri);

    //GUARD
    if (os == null) throw new NullPointerException("output stream is null");

    CopyHelperKt.copyTo(fromPath, os);
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