package com.zeintek.tsl_rfid_lib;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.zeintek.tsl_rfid_lib.TslCore.SignalPercentageConverter;
import com.zeintek.tsl_rfid_lib.TslCore.TslProtocolsModel;
import com.zeintek.tsl_rfid_lib.TslCore.WeakHandler;
import com.uk.tsl.rfid.DeviceListActivity;
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander;
import com.uk.tsl.rfid.asciiprotocol.DeviceProperties;
import com.uk.tsl.rfid.asciiprotocol.device.ConnectionState;
import com.uk.tsl.rfid.asciiprotocol.device.IAsciiTransport;
import com.uk.tsl.rfid.asciiprotocol.device.ObservableReaderList;
import com.uk.tsl.rfid.asciiprotocol.device.Reader;
import com.uk.tsl.rfid.asciiprotocol.device.ReaderManager;
import com.uk.tsl.rfid.asciiprotocol.device.TransportType;
import com.uk.tsl.rfid.asciiprotocol.enumerations.TriState;
import com.uk.tsl.rfid.asciiprotocol.parameters.AntennaParameters;
import com.uk.tsl.rfid.asciiprotocol.responders.LoggerResponder;
import com.uk.tsl.utils.Observable;

import java.lang.reflect.Method;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import io.flutter.BuildConfig;
import io.flutter.Log;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.embedding.engine.plugins.lifecycle.FlutterLifecycleAdapter;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import static com.uk.tsl.rfid.DeviceListActivity.EXTRA_DEVICE_ACTION;
import static com.uk.tsl.rfid.DeviceListActivity.EXTRA_DEVICE_INDEX;
import static io.flutter.plugin.common.PluginRegistry.ActivityResultListener;
import static io.flutter.plugin.common.PluginRegistry.NewIntentListener;

/** TslRfidProtocolsPlugin */
public class TslRfidProtocolsPlugin implements
		FlutterPlugin,
		MethodCallHandler,
		StreamHandler,
		ActivityAware,
		NewIntentListener,
		DefaultLifecycleObserver {
	private FlutterPluginBinding pluginBinding;
	private Lifecycle lifecycle; //This is null when not using v2 embedding;
	public static final boolean D = com.uk.tsl.rfid.asciiprotocol.BuildConfig.DEBUG;
	private Registrar registrar;
	private Context applicationContext;
	private BroadcastReceiver chargingStateChangeReceiver;
	private MethodChannel mCallbacksChannel;
	private MethodCall callBack;
	private Result resultCallBack;
	private EventChannel rfigTagMessageEventChannel;
	private EventChannel barcodeMessageEventChannel;
	private EventChannel connectionStateEventChannel;
	private final TslRfidProtocolsPlugin.Callback listener = new TslRfidProtocolsPlugin.Callback();
	private static final String TAG = TslRfidProtocolsPlugin.class.getSimpleName();
	private static final String MYNAMESPACE = "com.zeintek.tsl_rfid_lib";
	private TslProtocolsModel mModel;
	private Reader mReader = null; //The Reader currently in use
	private Reader mLastUserDisconnectedReader = null;
	private boolean mIsSelectingReader = false;
	public static EventChannel.EventSink mRfidTagMessageEmitter;
	public static EventChannel.EventSink mBarcodeMessageEmitter;
	private EventChannel.EventSink mConnectionStateEmitter;
	private int mPowerLevel = AntennaParameters.MaximumCarrierPower; //The current setting of the power level
	private SignalPercentageConverter mPercentageConverter = new SignalPercentageConverter();
	private EventChannel signalStrengthEventChannel;
	private EventChannel.EventSink mSignalStrengthEmitter;
	private BroadcastReceiver epcChangeReceiver;
	private BroadcastReceiver barcodeChangeReceiver;
	private boolean isTagFinderOn = false;
	private ActivityPluginBinding activityBinding;
	private Activity mActivity;
	
	@Override
	public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
		
		onAttachedToEngine(flutterPluginBinding.getApplicationContext(), flutterPluginBinding.getBinaryMessenger());
		
	}
	
	private void onAttachedToEngine(final Context applicationContext, BinaryMessenger messenger) {
		
		this.applicationContext = applicationContext;
		
		if(mGenericModelHandler == null) {
			mGenericModelHandler = new GenericHandler(this);
		} else {
			mGenericModelHandler.setTarget(this);
		}
		
		this.mCallbacksChannel = new MethodChannel(messenger, "callbacks");
		mCallbacksChannel.setMethodCallHandler(this);
		
		this.rfigTagMessageEventChannel = new EventChannel(messenger, MYNAMESPACE + "/rfidTagMessage");
		
		rfigTagMessageEventChannel.setStreamHandler(new StreamHandler() {
			
			@Override
			public void onListen(Object args, final EventChannel.EventSink eventSink) {
				mRfidTagMessageEmitter = eventSink;
				
				mModel.setEnabledInventory(true);
				
				epcChangeReceiver = createEpcChangeReceiver(eventSink);
				
				applicationContext.registerReceiver(epcChangeReceiver, new IntentFilter("epc_receive"));
			}
			
			@Override
			public void onCancel(Object args) {
				applicationContext.unregisterReceiver(epcChangeReceiver);
				
				epcChangeReceiver = null;
				
				mModel.setEnabledInventory(false);
				
			}
		});
		
		this.barcodeMessageEventChannel = new EventChannel(messenger, MYNAMESPACE + "/barcodeMessage");
		
		barcodeMessageEventChannel.setStreamHandler(new StreamHandler() {
			
			@Override
			public void onListen(Object args, final EventChannel.EventSink eventSink) {
				mBarcodeMessageEmitter = eventSink;
				
				mModel.setEnabledInventory(true);
				
				barcodeChangeReceiver = createBarcodeChangeReceiver(eventSink);
				
				applicationContext.registerReceiver(barcodeChangeReceiver, new IntentFilter("bc_receive"));
			}
			
			@Override
			public void onCancel(Object args) {
				applicationContext.unregisterReceiver(barcodeChangeReceiver);
				
				barcodeChangeReceiver = null;
				
				mModel.setEnabledInventory(false);
				
			}
			
		});
		
		this.connectionStateEventChannel = new EventChannel(messenger, MYNAMESPACE + "/connectionState");
		
		connectionStateEventChannel.setStreamHandler(this);
		
		this.signalStrengthEventChannel = new EventChannel(messenger, MYNAMESPACE + "/rssiSignalStrength");
		
		signalStrengthEventChannel.setStreamHandler(new StreamHandler() {
			
			@Override
			public void onListen(Object args, final EventChannel.EventSink eventSink) {
				mSignalStrengthEmitter = eventSink;
			}
			
			@Override
			public void onCancel(Object args) {
				mSignalStrengthEmitter = null;
			}
		});
		
		// Ensure the shared instance of AsciiCommander exists
		AsciiCommander.createSharedInstance(applicationContext);
		
		AsciiCommander commander = getCommander();
		
		// Ensure that all existing responders are removed
		commander.clearResponders();
		
		// Add the LoggerResponder - this simply echoes all lines received from the reader to the log
		// and passes the line onto the next responder
		// This is added first so that no other responder can consume received lines before they are logged.
		commander.addResponder(new LoggerResponder());
		
		// Add a synchronous responder to handle synchronous commands
		commander.addSynchronousResponder();
		
		// Create the single shared instance for this ApplicationContext
		ReaderManager.create(applicationContext);
		
		// Add observers for changes
		ReaderManager.sharedInstance().getReaderList().readerAddedEvent().addObserver(mAddedObserver);
		ReaderManager.sharedInstance().getReaderList().readerUpdatedEvent().addObserver(mUpdatedObserver);
		ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().addObserver(mRemovedObserver);
		
		initInventory();
		
	}
	
	public static void registerWith(Registrar registrar) {
		
		final TslRfidProtocolsPlugin instance = new TslRfidProtocolsPlugin();
		registrar.addNewIntentListener(instance);
		instance.mActivity = registrar.activity();
		instance.onAttachedToEngine(registrar.context(), registrar.messenger());
		
		//if (instance.mActivity.getIntent() != null) {
		//	instance.handleIntent(instance.mActivity.getIntent());
		//}
		
	}
	
	private void initInventory(){
		
		mModel = new TslProtocolsModel();
		
		mModel.setCommander(getCommander());
		
		mModel.setHandler(mGenericModelHandler);
		
	}
	
	@Override
	public void onMethodCall(final MethodCall call, final MethodChannel.Result rawResult) {
		final MethodChannel.Result result = new MethodResultWrapper(rawResult);
		
		switch (call.method) {
			
			case "isConnected":
				result.success(getCommander().isConnected());
				return;
			
			case "connectReader":
				connectReader();
				return;
			
			case "useFastId":
				boolean currentFastIdIsChecked = (boolean) call.arguments;
				mModel.getCommand().setUsefastId(currentFastIdIsChecked ? TriState.YES : TriState.NO);
				mModel.updateConfiguration();
				return;
			
			case "testAntenna":
				mModel.testForAntenna();
				return;
			
			case "getAntennaMinPowerLevel":
				result.success(getCommander().getDeviceProperties().getMinimumCarrierPower());
				return;
			
			case "getAntennaMaxPowerLevel":
				result.success(getCommander().getDeviceProperties().getMaximumCarrierPower());
				return;
			
			case "getAntennaPowerLevel":
				result.success(mModel.getCommand().getOutputPower());
				return;
			
			case "triggerScan":
				//not sure how to implement this
				return;
			
			case "setAntennaPowerLevel":
				int powerLevel = (int) call.arguments;
				mModel.getCommand().setOutputPower(powerLevel);
				mModel.updateConfiguration();
				return;
			
			case "getDeviceProperties":
				result.success(getCommander().getDeviceProperties());
				return;
			
			case "getConnectedReaderName":
				//this is not working
				//result.success(getCommander().getDeviceProperties());
				return;
			
			case "writeData":
                String oldValue = (String) call.argument("old");
                String newValue = (String) call.argument("new");
                int bank = (int) call.argument("bank");
                int startAddress = (int) call.argument("startAddress");
                int readCount = (int) call.argument("readCount");
                mModel.writeData(oldValue, newValue, bank, startAddress, readCount);
                return;
			
			default:
				result.notImplemented();
				break;
			
		}
		
		//if (call.method.equals("isConnected")) {
		//	result.success(getCommander().isConnected());
		//} else if(call.method.equals("connectReader")){
		//	connectReader();
		//} else if(call.method.equals("setTargetTagEpc")){
		//	String value = (String) call.arguments;
		//	mModel.setTargetTagEpc(value);
		//} else if(call.method.equals("useFastId")){
		//	boolean currentFastIdIsChecked = (boolean) call.arguments;
		//	mModel.getCommand().setUsefastId(currentFastIdIsChecked ? TriState.YES : TriState.NO);
		//	mModel.updateConfiguration();
		//} else {
		//	result.notImplemented();
		//}
		
	}
	
  	//MethodChannel.Result wrapper that responds on the platform thread.
	private static class MethodResultWrapper implements MethodChannel.Result {
		private final MethodChannel.Result methodResult;
		private final Handler handler;
		
		MethodResultWrapper(final MethodChannel.Result result) {
			this.methodResult = result;
			this.handler = new Handler(Looper.getMainLooper());
		}
		
		@Override
		public void success(final Object result) {
		
			this.handler.post(
				new Runnable() {
					
					@Override
					public void run() {
						MethodResultWrapper.this.methodResult.success(result);
					}
					
				}
			);
			
		}
		
		@Override
		public void error(
			final String errorCode,
			final String errorMessage,
			final Object errorDetails
		) {
			
			this.handler.post(
				
				new Runnable() {
					
					@Override
					public void run() {
						MethodResultWrapper.this.methodResult.error(errorCode, errorMessage, errorDetails);
					}
					
				}
				
			);
			
		}
		
		@Override
		public void notImplemented() {
			
			this.handler.post(
				
				new Runnable() {
					
					@Override
					public void run() {
					MethodResultWrapper.this.methodResult.notImplemented();
					}
					
				}
			);
			
		}
		
	}
	
	@Override
	public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
		MethodChannel channel = this.mCallbacksChannel;
		
		if (channel != null) {
			channel.setMethodCallHandler(null);
		}
		
		this.callBack = (MethodCall)null;
		this.resultCallBack = (Result)null;
		this.mCallbacksChannel = (MethodChannel)null;
		
	}
	
	@Override
	public void onListen(Object arguments, EventChannel.EventSink events) {
		mConnectionStateEmitter= events;
		
		LocalBroadcastManager.getInstance(applicationContext).registerReceiver(
                mCommanderMessageReceiver, new IntentFilter(AsciiCommander.STATE_CHANGED_NOTIFICATION));
		
	}
	
	@Override
	public void onCancel(Object arguments) {
		mConnectionStateEmitter = null;
		
		LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(mCommanderMessageReceiver);
		
	}
	
	//@return the current AsciiCommander/
	protected AsciiCommander getCommander() {
		return AsciiCommander.sharedInstance();
	}
	
	//----------------------------------------------------------------------------------------------
	// ReaderList Observers
	//----------------------------------------------------------------------------------------------
	Observable.Observer<Reader> mAddedObserver = new Observable.Observer<Reader>() {
		
		@Override
		public void update(Observable<? extends Reader> observable, Reader reader) {
			// See if this newly added Reader should be used
			AutoSelectReader(true);
		}
		
	};
	
	Observable.Observer<Reader> mUpdatedObserver = new Observable.Observer<Reader>() {
		
		@Override
		public void update(Observable<? extends Reader> observable, Reader reader) {
			
			// Is this a change to the last actively disconnected reader
			if(reader == mLastUserDisconnectedReader) {
				// Things have changed since it was actively disconnected so
				// treat it as new
				mLastUserDisconnectedReader = null;
			}
			
			// Was the current Reader disconnected i.e. the connected transport went away or disconnected
			if( reader == mReader && !reader.isConnected() ) {
				
				// No longer using this reader
				mReader = null;
				
				// Stop using the old Reader
				getCommander().setReader(mReader);
				
			} else {
				// See if this updated Reader should be used
				// e.g. the Reader's USB transport connected
				AutoSelectReader(true);
			}
			
		}
		
	};
	
	Observable.Observer<Reader> mRemovedObserver = new Observable.Observer<Reader>() {
		
		@Override
		public void update(Observable<? extends Reader> observable, Reader reader) {
			
			// Is this a change to the last actively disconnected reader
			if( reader == mLastUserDisconnectedReader ) {
				
				// Things have changed since it was actively disconnected so treat it as new
				mLastUserDisconnectedReader = null;
				
			}
			
			// Was the current Reader removed
			if( reader == mReader) {
				
				mReader = null;
				
				// Stop using the old Reader
				getCommander().setReader(mReader);
				
			}
			
		}
		
	};
	
	//Set the seek bar to cover the range of the currently connected device The power level is set to the new maximum power
	private void setPowerBarLimits() {
		
		DeviceProperties deviceProperties = getCommander().getDeviceProperties();
		
		mPowerLevel = deviceProperties.getMaximumCarrierPower();
		
	}
	
	//Set the state for the UI controls
	private void UpdateUI() {
		
		//boolean isConnected = getCommander().isConnected();
		//TODO: configure UI control state
		boolean isConnected = getCommander().isConnected();
		boolean canIssueCommand = isConnected & !mModel.isBusy();
		
	}
	
	private void AutoSelectReader(boolean attemptReconnect) {
		
		ObservableReaderList readerList = ReaderManager.sharedInstance().getReaderList();
		
		Reader usbReader = null;
		
		if( readerList.list().size() >= 1) {
			
			// Currently only support a single USB connected device so we can safely take the
			// first CONNECTED reader if there is one
			for (Reader reader : readerList.list()) {
				
				if (reader.hasTransportOfType(TransportType.USB)) {
					usbReader = reader;
					break;
				}
			
			}
		}
		
		if( mReader == null ) {
			
			if( usbReader != null && usbReader != mLastUserDisconnectedReader) {
				
				// Use the Reader found, if any
				mReader = usbReader;
				getCommander().setReader(mReader);
				
			}
		} else {
			
			// If already connected to a Reader by anything other than USB then
			// switch to the USB Reader
			IAsciiTransport activeTransport = mReader.getActiveTransport();
			
			if ( activeTransport != null && activeTransport.type() != TransportType.USB && usbReader != null) {
				
				mReader.disconnect();
				
				mReader = usbReader;
				
				// Use the Reader found, if any
				getCommander().setReader(mReader);
				
			}
			
		}
		
		// Reconnect to the chosen Reader
		if(mReader != null && !mReader.isConnecting() && (mReader.getActiveTransport() == null || mReader.getActiveTransport().connectionStatus().value() == ConnectionState.DISCONNECTED)) {
			
			// Attempt to reconnect on the last used transport unless the ReaderManager is cause of OnPause (USB device connecting)
			if( attemptReconnect ) {
				
				if( mReader.allowMultipleTransports() || mReader.getLastTransportType() == null ) {
					// Reader allows multiple transports or has not yet been connected so connect to it over any available transport
					mReader.connect();
				} else {
					// Reader supports only a single active transport so connect to it over the transport that was last in use
					mReader.connect(mReader.getLastTransportType());
				}
				
			}
			
		}
		
	}
	
	private BroadcastReceiver createBarcodeChangeReceiver(final EventChannel.EventSink events) {
		
		return new BroadcastReceiver() {
			
			@Override
			public void onReceive(Context context, Intent intent) {
				
				String bc = intent.getExtras().get("bc").toString();
				
				if(bc != null & mRfidTagMessageEmitter != null) {
					
					if (!bc.equals("No transponders seen")) {
						events.success(bc);
						Log.d(TAG,"Zeintek BC: "+ bc);
					}
					
				}
				
				//UpdateUI();
				
			}
		};
		
	};
	
	private BroadcastReceiver createEpcChangeReceiver(final EventChannel.EventSink events) {
		
		return new BroadcastReceiver() {
			
			@Override
			public void onReceive(Context context, Intent intent) {
				
				if (D) {
					android.util.Log.d(getClass().getName(), "AsciiCommander state changed - isConnected: " + getCommander().isConnected());
				}
				
				String tag = intent.getExtras().get("epc").toString();
				
				if(tag != null & mRfidTagMessageEmitter != null) {
					
					if (!tag.equals("No transponders seen")) {
						events.success(tag);
						Log.d(TAG,"Zeintek EPC: "+ tag);
					}
				}
				
				//UpdateUI();
				
			}
			
		};
		
	};
	
	//Handle the messages broadcast from the AsciiCommander
	private BroadcastReceiver mCommanderMessageReceiver = new BroadcastReceiver() {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			
			if (D) { 
				android.util.Log.d(getClass().getName(), "AsciiCommander state changed - isConnected: " + getCommander().isConnected());
			}
			
			String connectionStateMsg =intent.getExtras().get(AsciiCommander.REASON_KEY).toString();
			
			//String epc = intent.getStringExtra("epc");
			
			Log.d(TAG,"My Connection: "+connectionStateMsg);
			
			mConnectionStateEmitter.success(connectionStateMsg);
			
			displayReaderState();
			
			if(getCommander().isConnected()) {	
				
				// Update for any change in power limits
				setPowerBarLimits();
				
				// This may have changed the current power level setting if the new range is smaller than the old range
				// so update the model's inventory command for the new power value
				mModel.getCommand().setOutputPower(mPowerLevel);
				
				mModel.resetDevice();
				mModel.updateConfiguration();
				
			}
			
			UpdateUI();
			
		}
		
	};
	
	@Override
	public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
		
		Log.i(TAG, "attaching Notificare plugin to activity");
		
		this.activityBinding = binding;
		
		ActivityPluginBinding activityBinding = this.activityBinding;
		
		lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(binding);
		
		lifecycle.addObserver(this);
		
		activityBinding.addActivityResultListener((ActivityResultListener)this.listener);
		
	}
	
	@Override
	public void onDetachedFromActivityForConfigChanges() {
		
		onDetachedFromActivity();
		
		ActivityPluginBinding activityBinding = this.activityBinding;
		
		if (activityBinding != null) {
			activityBinding.removeActivityResultListener((ActivityResultListener)this.listener);
		}
		
		this.callBack = (MethodCall)null;
		this.resultCallBack = (Result)null;
		this.activityBinding = (ActivityPluginBinding)null;
		
	}
	
	@Override
	public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
		onAttachedToActivity(binding);
	}
	
	@Override
	public void onDetachedFromActivity() {
		Log.i(TAG, "detaching Notificare plugin from activity");
		this.onDetachedFromActivityForConfigChanges();
	}
	
	@Override
	public boolean onNewIntent(Intent intent) {
		return false;
	}
	
	private static class GenericHandler extends WeakHandler<TslRfidProtocolsPlugin> {
		
		public GenericHandler(TslRfidProtocolsPlugin t) {
			super(t);
		}
		
		@Override
		public void handleMessage(Message msg, TslRfidProtocolsPlugin t) {
		
			try {
				
				switch (msg.what) {
					
					case TslProtocolsModel.BUSY_STATE_CHANGED_NOTIFICATION:
						
						if( t.mModel.error() != null ) {
			  				//  t.appendMessage("\n Task failed:\n" + t.mModel.error().getMessage() + "\n\n");
						}
						
						//if(t.isTagFinderOn){
						//	t.UpdateUI();
						//}
						
						break;
					
					case TslProtocolsModel.MESSAGE_NOTIFICATION:
						
						// Examine the message for prefix
						String message = (String)msg.obj;
						
						if( message.startsWith("ER:")) {
							
							//Intent i = new Intent("er_receive");
							//i.putExtra("er", message.substring(3));
							//t.applicationContext.sendBroadcast(i);
							t.mCallbacksChannel.invokeMethod("resultText",message.substring(3));
							//t.mResultTextView.setText( message.substring(3));
							
						} else if( message.startsWith("BC:")) {
							
							Intent i = new Intent("bc_receive");
							
							i.putExtra("bc", message);
							
							t.applicationContext.sendBroadcast(i);
							
							//if(t.mBarcodeMessageEmitter != null) {
							//	t.mBarcodeMessageEmitter.success(message);
							//}
							
						} else {
							
							Intent i = new Intent("epc_receive");
							
							i.putExtra("epc", message);
							
							t.applicationContext.sendBroadcast(i);
							
							//if(t.mRfidTagMessageEmitter != null) {
							//	t.mRfidTagMessageEmitter.success(message);
							//}
							
						}
						
						//t.UpdateUI();
						
						break;
					
					//case TslProtocolsModel.ASYNC_SWITCH_STATE_NOTIFICATION:
					//    if (D) { android.util.Log.d(getClass().getName(), "Async: " + (SwitchState)msg.obj); }
					//    t.updateAsynchronousStateDisplay((SwitchState)msg.obj);
					//    break;
					//
					//case TslProtocolsModel.POLLED_SWITCH_STATE_NOTIFICATION:
					//    if (D) { android.util.Log.d(getClass().getName(), "Polled: " + (SwitchState)msg.obj); }
					//    t.updatePolledStateDisplay((SwitchState)msg.obj);
					//    break;
					
					default:
						//
						break;
				
				}
			
			} catch (Exception e) {
				//
			}
			
		}
		
	};
	
	// The handler for model messages
	private static GenericHandler mGenericModelHandler;
	
	private void displayReaderState() {
		
		if(mConnectionStateEmitter != null) {
			
			String connectionMsg = "";

			Log.w(TAG, "MACAQUE is:" + getCommander().getConnectionState().toString());
			
			switch (getCommander().getConnectionState()) {
				
				case CONNECTED:
					Log.w(TAG, "MACAQUE is CONNECTED");
					new Handler(Looper.getMainLooper()).post(() -> mConnectionStateEmitter.success(getCommander().getConnectedDeviceName()));
					break;
				
				case CONNECTING:
					Log.w(TAG, "MACAQUE is CONNECTING");
					new Handler(Looper.getMainLooper()).post(() -> mConnectionStateEmitter.success("Connecting..."));
					break;
				
				default:
					Log.w(TAG, "MACAQUE is DEFAULT");
					new Handler(Looper.getMainLooper()).post(() -> mConnectionStateEmitter.success("Disconnected"));
					break;
				
			}
			
		}
		
	}
	
	private void isConnected(Object args, MethodChannel.Result result) {
		result.success(getCommander().isConnected());
	}
		
	private void useFastId(Object args, MethodChannel.Result result) {
		
		boolean currentFastIdIsChecked = (boolean) args;
		mModel.getCommand().setUsefastId(currentFastIdIsChecked ? TriState.YES : TriState.NO);
		mModel.updateConfiguration();
		
	}
	
	private void connectReader() {
		
		if(mActivity == null){
			mActivity = activityBinding.getActivity();
		}
		
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			
			@Override
			public void run() {
				
				try {
					
					// Launch the DeviceListActivity to see available Readers
					mIsSelectingReader = true;
					int index = -1;
					
					if( mReader != null ) {
						index = ReaderManager.sharedInstance().getReaderList().list().indexOf(mReader);
					}
					
					Intent selectIntent = new Intent(applicationContext, DeviceListActivity.class);
					
					if( index >= 0 ) {
						selectIntent.putExtra(EXTRA_DEVICE_INDEX, index);
					}
					
					mActivity.startActivityForResult(selectIntent, DeviceListActivity.SELECT_DEVICE_REQUEST);
					
					//UpdateUI();
					
				} catch (Exception ex) {
					
					Log.e(TAG, ex.getMessage(), ex);
					
					if(D) Log.e(TAG, "Unable to obtain Reader!");
					
					//InvokeIsConnect();
					
				}
				
			}
			
		});
		
	}
	
	public final class Callback implements ActivityResultListener {
		
		public boolean onActivityResult(int requestCode, int resultCode,@Nullable Intent data) {
			
			if (BuildConfig.DEBUG) {
				Log.d(TAG, "onActivityResult");
			};
			if (requestCode == DeviceListActivity.SELECT_DEVICE_REQUEST) {
				
				// When DeviceListActivity returns with a device to connect
				if (resultCode == Activity.RESULT_OK) {
					
					if (data != null) {
						
						int readerIndex = data.getExtras().getInt(EXTRA_DEVICE_INDEX);
						
						Reader chosenReader = ReaderManager.sharedInstance().getReaderList().list().get(readerIndex);
						
						int action = data.getExtras().getInt(EXTRA_DEVICE_ACTION);
						// If already connected to a different reader then disconnect it
						if (mReader != null) {
							
							if (action == DeviceListActivity.DEVICE_CHANGE || action == DeviceListActivity.DEVICE_DISCONNECT) {
								
								mReader.disconnect();
								
								if (action == DeviceListActivity.DEVICE_DISCONNECT) {
									mLastUserDisconnectedReader = mReader;
									mReader = null;
								}
								
							}
							
							displayReaderState();
							
						}
						
						// Use the Reader found
						if (action == DeviceListActivity.DEVICE_CHANGE || action == DeviceListActivity.DEVICE_CONNECT) {
							
							mReader = chosenReader;
							
							mLastUserDisconnectedReader = null;
							getCommander().setReader(mReader);
							
							displayReaderState();
							
						}
						
					}
					
				}
				
				return true;
				
			}
			
			displayReaderState();
			
			return false;
			
		}
		
	}
	
	@Override
	public void onResume(@NonNull LifecycleOwner owner) {
		mModel.getCommand().setUsefastId(TriState.YES);
		
		//Register to receive notifications from the AsciiCommander
		//this.addBroadCast(context);
		//Remember if the pause/resume was caused by ReaderManager - this will be cleared when ReaderManager.onResume() is called
		boolean readerManagerDidCauseOnPause = ReaderManager.sharedInstance().didCauseOnPause();
		
		// The ReaderManager needs to know about Activity lifecycle changes
		ReaderManager.sharedInstance().onResume();
		
		//The Activity may start with a reader already connected (perhaps by another App)
		//Update the ReaderList which will add any unknown reader, firing events appropriately
		ReaderManager.sharedInstance().updateList();
		
		// Locate a Reader to use when necessary
		AutoSelectReader(!readerManagerDidCauseOnPause);
		
		mIsSelectingReader = false;
		
		displayReaderState();
		
		if(isTagFinderOn){
			UpdateUI();
		}
		
		if(getCommander().isConnected()){
			new Handler(Looper.getMainLooper()).post(() -> mConnectionStateEmitter.success(getCommander().getConnectedDeviceName()));
		} else {
			
			if(mConnectionStateEmitter != null) {
				new Handler(Looper.getMainLooper()).post(() -> mConnectionStateEmitter.success("Disconnect"));
			}
			
		}
		
	}
	
	@Override
	public void onPause(@NonNull LifecycleOwner owner) {
		mModel.setEnabledInventory(false);
		//Disconnect from the reader to allow other Apps to use it
		//unless pausing when USB device attached or using the DeviceListActivity to select a Reader
		
		if( !mIsSelectingReader && !ReaderManager.sharedInstance().didCauseOnPause() && mReader != null ) {
			mReader.disconnect();
		}
		
		ReaderManager.sharedInstance().onPause();
		
	}
	
	@Override
	public void onDestroy(@NonNull LifecycleOwner owner) {
		// Remove observers for changes
		ReaderManager.sharedInstance().getReaderList().readerAddedEvent().removeObserver(mAddedObserver);
		ReaderManager.sharedInstance().getReaderList().readerUpdatedEvent().removeObserver(mUpdatedObserver);
		ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().removeObserver(mRemovedObserver);
		
	}
	
}
