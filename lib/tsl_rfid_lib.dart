import 'dart:async';

import 'package:flutter/foundation.dart';
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
	static const _eventChannel = const EventChannel('events');
	static const EventChannel _connectionStateChannel = const EventChannel('$NAMESPACE/connectionState');
	static const EventChannel _rfidTagMessageChannel = const EventChannel('$NAMESPACE/rfidTagMessage');
	static const EventChannel _barcodeMessageChannel = const EventChannel('$NAMESPACE/barcodeMessage');
	static const EventChannel _rssiSignalStrengthChannel = const EventChannel('$NAMESPACE/rssiSignalStrength');
	static Stream<String> _transpoders;
	static Stream<String> _barcodes;
	static Stream<String> _connectionState;
	static Stream<String> _rssiSignalStrength;
	static String _resultTextMessage = "";
	
	static Future<void> _methodCallHandler(MethodCall call) async {
		
		switch (call.method) {
			
			case 'resultText':
				_resultTextMessage = call.arguments as String;
				break;
			
			default:
				print('TslRfidProtocols: Ignoring invoke from native. This normally shouldn\'t happen.');
				break;
			
		}
		
	}
	
	static Future<bool> get isconnected async {
		final bool isConnected = await _channel.invokeMethod("isConnected");
		return isConnected;
	}
	
	static String get resultText {
		_channel.setMethodCallHandler(_methodCallHandler);
		return _resultTextMessage;
	}
	
	static Future<String> get platformVersion async {
		final String version = await _channel.invokeMethod('getPlatformVersion');
		return version;
	}
	
	static Future<void> connectReader() async {
		await _channel.invokeMethod("connectReader");
	}
	
	static Future<bool> useFastId(bool isChecked) async {
		final bool isFastIdSet = await _channel.invokeMethod("useFastId", isChecked);
		return isFastIdSet;
	}
	
	static Future<bool> triggerScan() async {
		final bool scanTriggered = await _channel.invokeMethod("triggerScan");
		return scanTriggered;
	}
	
	static Future<bool> testAntenna() async {
		final bool testAntenna = await _channel.invokeMethod("testAntenna");
		return testAntenna;
	}
	
	static Future<int> getAntennaMinPowerLevel() async {
		final int antennaMinPowerLevel = await _channel.invokeMethod("getAntennaMinPowerLevel");
		return antennaMinPowerLevel;
	}
	
	static Future<int> getAntennaMaxPowerLevel() async {
		final int antennaMaxPowerLevel = await _channel.invokeMethod("getAntennaMaxPowerLevel");
		return antennaMaxPowerLevel;
	}
	
	static Future<int> getAntennaPowerLevel() async {
		final int antennaMaxPowerLevel = await _channel.invokeMethod("getAntennaPowerLevel");
		return antennaMaxPowerLevel;
	}
	
	static Future<bool> setAntennaPowerLevel(int powerLevel) async {
		final bool setAntennaPowerLevel = await _channel.invokeMethod("setAntennaPowerLevel", powerLevel);
		return setAntennaPowerLevel;
	}
	
	static Future<bool> getDeviceProperties() async {
		final bool getDeviceProperties = await _channel.invokeMethod("getDeviceProperties");
		return getDeviceProperties;
	}
	
	static Future<String> getConnectedReaderName() async {
		final String getDeviceReaderName = await _channel.invokeMethod("getConnectedReaderName");
		return getDeviceReaderName;
	}
	
	//what does type does this return?
	//what name do we give to this function
	/*static Future<bool> setTargetTagEpc(String epcToEncode) async {
		final bool encodeTagWithEpc = await _channel.invokeMethod("setTargetTagEpc", epcToEncode);
		return encodeTagWithEpc;
	}*/
	
	//does this function not have a return type?
	static void writeData({
		@required String oldValue,
		@required String newValue,
		@required Databank bank,
		@required int startAddress,
		@required int readCount,
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
		_connectionState ??= _connectionStateChannel.receiveBroadcastStream().map((connectionState) => connectionState);
		return _connectionState;
	}
	
	static Stream<String> get startListeningToRfidTagMessages {
		_transpoders ??= _rfidTagMessageChannel.receiveBroadcastStream().map<String>((data) => data);
		return _transpoders;
	}
	
	static Stream<String> get startListeningToBarcodeMessages {
		_barcodes ??= _barcodeMessageChannel.receiveBroadcastStream().map<String>((data) => data);
		return _barcodes;
	}
	
	static Stream<String> get startListeningToRssiSignalStrength {
		_rssiSignalStrength ??= _rssiSignalStrengthChannel.receiveBroadcastStream().map<String>((data) => data);
		return _rssiSignalStrength;
	}
	
}
