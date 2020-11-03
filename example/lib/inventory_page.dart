import 'dart:async';

import 'package:flutter/material.dart';
import 'package:tsl_rfid_lib/tsl_rfid_lib.dart';

class InventoryPage extends StatefulWidget {
  const InventoryPage({Key key}) : super(key: key);

  @override
  _InventoryPageState createState() => _InventoryPageState();
}

class _InventoryPageState extends State<InventoryPage> {
  String _isConnected = "";

  // Define some variables, which will be required later
  List<String> _transponders = [];

  bool _fastId = true;
  List<String> get transponders => [..._transponders];
  List<String> _barcodes = [];
  List<String> get barcodes => [..._barcodes];
  StreamSubscription<String> _connectionStateSub;

  @override
  initState() {
    super.initState();
    startConnectionState();
    runReaderConnectionState();
    runRfidTagMessage();
    runBarcodeMessage();
    _streamController = StreamController<String>();
  }

  // Future<void> startinventory() async {
  //   TslProtocolsPlugin.onInventoryChanged().listen((transponder) {
  //     setState(() {
  //      transponders.add(transponder);
  //     });
  //   });
  // }
  Future _getThingsOnStartup() async {
    var a = await TslRfidProtocols.isconnected;
    setState(() {
      _isConnected = a ? "Connected" : "Disconnected";
      _streamController.add(_isConnected);
    });
  }

  Future<void> startConnectionState() async {
    _connectionStateSub =
        TslRfidProtocols.onConnectionStateChanged.listen((isConnected) {
      setState(() {
        _isConnected = isConnected;
        _streamController.add(_isConnected);
      });
    });
  }

  //bool running = false;

  Future<void> _checkFastId(bool a) async {
    await TslRfidProtocols.useFastId(a);
    setState(() {
      _fastId = a;
    });
  }

  void _connectReader() async {
    await TslRfidProtocols.connectReader();
  }

  StreamController<String> _streamController;
  @override
  Widget build(BuildContext context) {
    // _getThingsOnStartup();
    // This method is rerun every time setState is called, for instance as done
    // by the _incrementCounter method above.
    //
    // The Flutter framework has been optimized to make rerunning build methods
    // fast, so that you can just rebuild anything that needs updating rather
    // than having to individually change instances of widgets.
    return Scaffold(
      appBar: AppBar(
        // Here we take the value from the MyHomePage object that was created by
        // the App.build method, and use it to set our appbar title.
        title: Center(
          child: Text(TslRfidProtocols.resultText != ""
              ? TslRfidProtocols.resultText
              : "Inventory Page"),
        ),
      ),
      body: Center(
        // Center is a layout widget. It takes a single child and positions it
        // in the middle of the parent.
        child: Column(
          // Column is also a layout widget. It takes a list of children and
          // arranges them vertically. By default, it sizes itself to fit its
          // children horizontally, and tries to be as tall as its parent.
          //
          // Invoke "debug painting" (press "p" in the console, choose the
          // "Toggle Debug Paint" action from the Flutter Inspector in Android
          // Studio, or the "Toggle Debug Paint" command in Visual Studio Code)
          // to see the wireframe for each widget.
          //
          // Column has various properties to control how it sizes itself and
          // how it positions its children. Here we use mainAxisAlignment to
          // center the children vertically; the main axis here is the vertical
          // axis because Columns are vertical (the cross axis would be
          // horizontal).
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            StreamBuilder(
              stream: _streamController.stream,
              initialData: "",
              builder: (BuildContext context, AsyncSnapshot<String> snapshot) {
                if (snapshot.hasData) {
                  return Text(snapshot.data);
                }
                return Text('Disconnect!');
              },
            ),

            Text("Press button to run playground"),
            Text("-"),
            // Text(logs),
            Text(eventLogs),
            //    Expanded(
            //     child: ListView(
            //   padding: EdgeInsets.all(10.0),
            //   children: transponders.reversed.map((data) {
            //     return Dismissible(
            //       key: Key(data.epc),
            //       child: ListTile(
            //         title: Text(data.rssi),
            //         leading: Text(data.tagsSeen.toString()),
            //       ),
            //     );
            //   }).toList(),
            // )),
            Expanded(
                child: ListView.builder(
                    padding: const EdgeInsets.all(8),
                    itemCount: transponders.length,
                    itemBuilder: (BuildContext context, int index) {
                      return Container(
                        height: 50,
                        child: ListTile(
                          title: Text(transponders[index]),
                          // leading:
                          //     Text(transponders[index]..toString()),
                        ),
                      );
                    })),
            Expanded(
                child: ListView.builder(
                    padding: const EdgeInsets.all(8),
                    itemCount: barcodes.length,
                    itemBuilder: (BuildContext context, int index) {
                      return Container(
                        height: 50,
                        child: ListTile(
                          title: Text(barcodes[index]),
                          // leading:
                          //     Text(transponders[index]..toString()),
                        ),
                      );
                    })),
            FlatButton(
              onPressed: () {
                if(_fastId){
                  setState(() {
                    _fastId = !_fastId;
                  });
                return _checkFastId(false);
                }
                else {
                   setState(() {
                    _fastId = !_fastId;
                  });
                return _checkFastId(true);
                }
              
              },
              child: Text(_fastId ? 'Disable FastId' : 'Enable FastId'),
            ),

            Row(
              children: <Widget>[
                FlatButton(
                  onPressed: _getThingsOnStartup,
                  child: Text("Check Connect"),
                ),
              ],
            ),
          ],
        ),
      ),

      floatingActionButton: FloatingActionButton(
        onPressed: _connectReader,
        tooltip: 'Connect',
        child: Icon(Icons.add),
      ), // This trailing comma makes auto-formatting nicer for build methods.
    );
  }

  /////////////// Playground ///////////////////////////////////////////////////
  String eventLogs = "";

  // Call inside a setState({ }) block to be able to reflect changes on screen
  void eventLog(String logString) {
    eventLogs += logString.toString() + "\n";
  }

  // // Main function called when playground is run
  // bool eventIsRunning = false;
  // void runEventPlayground() async {
  //   if (eventIsRunning) return;
  //   eventIsRunning = true;

  //   var eventCancel = TslProtocolsPlugin.startListeningToEvents((msg) {
  //     setState(() {
  //       eventLog(msg);
  //     });
  //   });
  //   await Future.delayed(Duration(seconds: 4));
  //   eventCancel();
  //   eventIsRunning = false;
  // }

  void runRfidTagMessage() async {
    TslRfidProtocols.startListeningToRfidTagMessages.listen((tranponder) {
      _transponders.add(tranponder);
    });
  }

  void runBarcodeMessage() async {
    TslRfidProtocols.startListeningToBarcodeMessages.listen((tranponder) {
      _transponders.add(tranponder);
    });
  }
  void runReaderConnectionState() async {
    TslRfidProtocols.onConnectionStateChanged.listen((connection) {
      setState(() {
        _isConnected = connection;
      });
    });
  }

 
  @override
  void dispose() {
    // TODO: implement dispose
    super.dispose();
    if (_connectionStateSub != null) {
      _connectionStateSub.cancel();
    }
  }
}
