package com.SplashPad.export_plugin;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;

/** ExportPlugin */
public class ExportPlugin implements FlutterPlugin, MethodCallHandler, ActivityResultListener {

  private static final int CREATE_FILE = 1;

  MethodChannel.Result _result;
  Uri                  tempUri;
  Registrar            mRegistrar;


  // constructor for v2 (flutterPluginBinding)
  private ExportPlugin() {
    // NOT YET IMPLEMENTED
  }

  // constructor for v1 (registerWith)
  private ExportPlugin(Registrar registrar) {
    this.mRegistrar = registrar;
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
    if (call.method.equals("getPlatformVersion")) {
      result.success("Android " + android.os.Build.VERSION.RELEASE);
    } else if ("export".equals(call.method)) {
      expectMapArguments(call);
      // Android does not support showing the share sheet at a particular point on screen.
      try {
        saveToDisk(
                (String) call.argument("mimeType"),
                (String) call.argument("path"),
                (String) call.argument("title"));
        _result = result;
      } catch (IOException e) {
        result.error(e.getMessage(), null, null);
      }
    } else if ("open".equals(call.method)) {
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
    } else {
      result.notImplemented();
    }
  }

  @Override
  public boolean onActivityResult(int requestCode, int resultCode, Intent intent) {
    Uri fileUri = intent.getData();
    Log.e("onActivityResult", fileUri.toString());

    if (writeFile(intent.getData())) {
      // This is for openFile to consume in dart, openFile is currently not in use
//    _result.success(fileUri.toString());
//      Toast.makeText(mRegistrar.activeContext(),"Successfully saved to local storage", Toast.LENGTH_SHORT).show();
      _result.success("Successfully saved to local storage");
    } else {
//      Toast.makeText(mRegistrar.activeContext(),"Failed to save file", Toast.LENGTH_SHORT).show();
      _result.error(null,"Failed to save file", null);
    }



    return true;
  }

  private void saveToDisk(String mimeType, String path, String title) throws IOException {
    if (path == null || path.isEmpty()) {
      throw new IllegalArgumentException("Non-empty path expected");
    }

    // create temporary file?
    File file = new File(path);
    clearExternalShareFolder();
    if (!fileIsOnExternal(file)) {
      file = copyToExternalShareFolder(file);
    }

    // get URI of created temporary file?
    Uri fileUri =
            FileProvider.getUriForFile(
                    mRegistrar.context(),
                    mRegistrar.context().getPackageName() + ".flutter.share_provider",
                    file);

    // hacky method
    // save to class property to be used in onActivityResult
    tempUri = fileUri;

    // file picker prompt to save file
    Intent intent = new Intent();
    intent.setAction(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("audio/wav");
    intent.putExtra(Intent.EXTRA_TITLE, title);

    mRegistrar.activity().startActivityForResult(intent, CREATE_FILE);
  }

  private void openFile(String uri) throws IOException {
    if (uri == null || uri.isEmpty()) {
      throw new IllegalArgumentException("Non-empty path expected");
    }

    Intent intent = new Intent();
    intent.setAction(Intent.ACTION_GET_CONTENT);
    intent.setDataAndType(Uri.parse(uri), "audio/wav");

    mRegistrar.activeContext().startActivity(intent);
  }

  boolean writeFile(Uri uri) {
    byte[] bytes;

    try {
      InputStream is = mRegistrar.activeContext().getContentResolver().openInputStream(tempUri);
      bytes = readBytes(is);

      OutputStream os = mRegistrar.activeContext().getContentResolver().openOutputStream(uri);
      if (os != null) {
        os.write(bytes);
        os.close();
        return true;
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return false;
  }

  public byte[] readBytes(InputStream inputStream) throws IOException {
    // this dynamically extends to take the bytes you read
    ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

    // this is storage overwritten on each iteration with bytes
    int bufferSize = 1024;
    byte[] buffer = new byte[bufferSize];

    // we need to know how may bytes were read to write them to the byteBuffer
    int len = 0;
    while ((len = inputStream.read(buffer)) != -1) {
      byteBuffer.write(buffer, 0, len);
    }

    // and then we can return your byte array.
    return byteBuffer.toByteArray();
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void clearExternalShareFolder() {
    File folder = getExternalShareFolder();
    if (folder.exists()) {
      for (File file : folder.listFiles()) {
        file.delete();
      }
      folder.delete();
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private File copyToExternalShareFolder(File file) throws IOException {
    File folder = getExternalShareFolder();
    if (!folder.exists()) {
      folder.mkdirs();
    }

    File newFile = new File(folder, file.getName());
    copy(file, newFile);
    return newFile;
  }

  private boolean fileIsOnExternal(File file) {
    try {
      String filePath = file.getCanonicalPath();
      File externalDir = Environment.getExternalStorageDirectory();
      return externalDir != null && filePath.startsWith(externalDir.getCanonicalPath());
    } catch (IOException e) {
      return false;
    }
  }

  @NonNull
  private File getExternalShareFolder() {
    return new File(mRegistrar.context().getExternalCacheDir(), "share");
  }

  private static void copy(File src, File dst) throws IOException {
    final InputStream in = new FileInputStream(src);
    try {
      OutputStream out = new FileOutputStream(dst);
      try {
        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
          out.write(buf, 0, len);
        }
      } finally {
        out.close();
      }
    } finally {
      in.close();
    }
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