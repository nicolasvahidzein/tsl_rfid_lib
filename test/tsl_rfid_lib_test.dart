import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tsl_rfid_lib/tsl_rfid_lib.dart';

void main() {
  const MethodChannel channel = MethodChannel('tsl_rfid_lib');

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
    expect(await TslRfidLib.platformVersion, '42');
  });
}
