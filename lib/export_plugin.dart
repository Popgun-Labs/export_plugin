import 'dart:async';
import 'dart:io';
import 'dart:ui';

import 'package:flutter/services.dart';

class ExportPlugin {
  static const MethodChannel _channel =
      const MethodChannel('export_plugin');

  static Future<String> saveToDisk(
      File file, {
        String mimeType,
        String path,
        String title,
        Rect sharePositionOrigin,
      }) {
    assert(file != null);
    assert(file.existsSync());

    final Map<String, dynamic> params = <String, dynamic>{
      'path': file.path,
      'mimeType': mimeType ?? _mimeTypeForFile(file),
      'title' : title
    };

    if (sharePositionOrigin != null) {
      params['originX'] = sharePositionOrigin.left;
      params['originY'] = sharePositionOrigin.top;
      params['originWidth'] = sharePositionOrigin.width;
      params['originHeight'] = sharePositionOrigin.height;
    }

    return _channel.invokeMethod('export', params);
  }

  static String _mimeTypeForFile(File file) {
    assert(file != null);
    final String path = file.path;

    final int extensionIndex = path.lastIndexOf("\.");
    if (extensionIndex == -1 || extensionIndex == 0) {
      return null;
    }

    final String extension = path.substring(extensionIndex + 1);
    switch (extension) {
    // image
      case 'jpeg':
      case 'jpg':
        return 'image/jpeg';
      case 'gif':
        return 'image/gif';
      case 'png':
        return 'image/png';
      case 'svg':
        return 'image/svg+xml';
      case 'tif':
      case 'tiff':
        return 'image/tiff';
    // audio
      case 'aac':
        return 'audio/aac';
      case 'oga':
        return 'audio/ogg';
      case 'wav':
        return 'audio/wav';
    // video
      case 'avi':
        return 'video/x-msvideo';
      case 'mpeg':
        return 'video/mpeg';
      case 'ogv':
        return 'video/ogg';
    // other
      case 'csv':
        return 'text/csv';
      case 'htm':
      case 'html':
        return 'text/html';
      case 'json':
        return 'application/json';
      case 'pdf':
        return 'application/pdf';
      case 'txt':
        return 'text/plain';
    }
    return null;
  }
}
