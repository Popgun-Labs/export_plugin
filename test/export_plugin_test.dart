import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:export_plugin/export_plugin.dart';

void main() {
  const MethodChannel channel = MethodChannel('export_plugin');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPlatformVersion', () async {
    expect(await ExportPlugin.platformVersion, '42');
  });
}
