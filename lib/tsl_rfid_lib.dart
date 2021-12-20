import 'dart:async';

import 'package:flutter/services.dart';

enum Databank { electronicProductCode, transponderIdentifier, reserved, user }

const Map<Databank, int> _indexedBanks = {
  Databank.electronicProductCode: 0,
  Databank.transponderIdentifier: 1,
  Databank.reserved: 2,
  Databank.user: 3,
};

class TslRfidProtocols {
  static const String NAMESPACE = "com.zeintek.tsl_rfid_lib";
  static const _channel = const MethodChannel('callbacks');
  static const EventChannel _connectionStateChannel =
      EventChannel('$NAMESPACE/connectionState');
  static const EventChannel _rfidTagMessageChannel =
      EventChannel('$NAMESPACE/rfidTagMessage');
  static const EventChannel _barcodeMessageChannel =
      EventChannel('$NAMESPACE/barcodeMessage');
  static const EventChannel _rssiSignalStrengthChannel =
      EventChannel('$NAMESPACE/rssiSignalStrength');
  static Stream<String>? _transpoders;
  static Stream<String>? _barcodes;
  static Stream<String>? _connectionState;
  static Stream<String>? _rssiSignalStrength;
  static String _resultTextMessage = "";

  static Future<void> _methodCallHandler(MethodCall call) async {
    switch (call.method) {
      case 'resultText':
        _resultTextMessage = call.arguments as String;
        break;

      default:
        print(
            'TslRfidProtocols: Ignoring invoke from native. This normally shouldn\'t happen.');
        break;
    }
  }

  static Future<bool> get isconnected async {
    return await _channel.invokeMethod("isConnected") ?? false;
  }

  static String get resultText {
    _channel.setMethodCallHandler(_methodCallHandler);
    return _resultTextMessage;
  }

  static Future<void> connectReader() => _channel.invokeMethod("connectReader");

  static Future<void> useFastId(bool isChecked) =>
      _channel.invokeMethod("useFastId", isChecked);

  static Future<bool> triggerScan() => throw UnimplementedError();

  static Future<void> testAntenna() => _channel.invokeMethod("testAntenna");

  static Future<int> getAntennaMinPowerLevel() async {
    return await _channel.invokeMethod("getAntennaMinPowerLevel");
  }

  static Future<int> getAntennaMaxPowerLevel() async {
    return await _channel.invokeMethod("getAntennaMaxPowerLevel");
  }

  static Future<int> getAntennaPowerLevel() async {
    return await _channel.invokeMethod("getAntennaPowerLevel");
  }

  static Future<bool> setAntennaPowerLevel(int powerLevel) async {
    return await _channel.invokeMethod("setAntennaPowerLevel", powerLevel);
  }

  static Future<bool> getDeviceProperties() async {
    return await _channel.invokeMethod("getDeviceProperties");
  }

  static Future<String> getConnectedReaderName() async {
    return await _channel.invokeMethod("getConnectedReaderName");
  }

  //what does type does this return?
  //what name do we give to this function
  /*static Future<bool> setTargetTagEpc(String epcToEncode) async {
		final bool encodeTagWithEpc = await _channel.invokeMethod("setTargetTagEpc", epcToEncode);
		return encodeTagWithEpc;
	}*/

  //does this function not have a return type?
  static void writeData({
    required String oldValue,
    required String newValue,
    required Databank bank,
    required int startAddress,
    required int readCount,
  }) {
    _channel.invokeMethod('writeData', <String, dynamic>{
      'old': oldValue,
      'new': newValue,
      'bank': _indexedBanks[bank],
      'startAddress': startAddress,
      'readCount': readCount,
    });
  }

  static Stream<String> get onConnectionStateChanged {
    _connectionState ??=
        _connectionStateChannel.receiveBroadcastStream().cast();
    return _connectionState!;
  }

  static Stream<String> get startListeningToRfidTagMessages {
    _transpoders ??= _rfidTagMessageChannel.receiveBroadcastStream().cast();
    return _transpoders!;
  }

  static Stream<String> get startListeningToBarcodeMessages {
    _barcodes ??= _barcodeMessageChannel.receiveBroadcastStream().cast();
    return _barcodes!;
  }

  static Stream<String> get startListeningToRssiSignalStrength {
    _rssiSignalStrength ??=
        _rssiSignalStrengthChannel.receiveBroadcastStream().cast();
    return _rssiSignalStrength!;
  }
}
