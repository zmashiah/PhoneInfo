/* =====================================================================================================
 * The background service for communicating over Bluetooth with the accessory and listens on system
 * events related to Battery status, Incoming SMS messages and missed calls
 * Threading and listening on BT is taken from the Android developers sample code of BluetoothChat,
 * extended here to include registration of system events of intrest to us as well as back and forth
 * communication with the Activity UI
 * =====================================================================================================
 * By: Zakie Mashiah 
 * =====================================================================================================
 */
package com.zakiem.myPhoneInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import myPhoneInfo.test.zakiem.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.CallLog.Calls;
import android.util.Log;



public class PhoneInfoServer extends Service
{

	/* Variables and constants
	 * ============================================================
	 */
	private static final String TAG = "PhoneInfoServer";
	// The messages it will get in broadcast
	public  static final String BROADCAST_PHONE_BASIC_INFO = "com.zakiem.myPhoneInfo.PHONE_BASIC_INFO";
	public  static final String PHONE_BASIC_INFO = "BasicInfo";
	public  static final String BROADCAST_ACCESORY_DEVICE_NAME = "com.zakiem.myPhoneInfo.DEVICE_NAME";
	public  static final String DEVICE_NAME = "DeviceName";
	public  static final String BROADCAST_TOAST = "com.zakiem.myPhoneInfo.TOAST";
	public  static final String TOAST = "Toast";
	public  static final String BROADCAST_STATE_CHANGE = "com.zakiem.myPhoneInfo.STATE_CHANGE";
	public  static final String STATE_CHANGE = "StateChange";
    // Constants that indicate the current connection state
    public static final String BROADCAST_DISPLAY_INTENSITY = "com.zakiem.myPhoneInfo.DISPLAY_INTENSITY";
    public static final String DISPLAY_INTENSITY = "DisplayIntensity"; // The current intensity value in the display

    
	// and the associated intents
	private Intent m_phoneBasicInfoIntent;
	private Intent m_accesoryDevicNameIntent;
	private Intent m_toastIntent;
	private Intent m_stateChangeIntent;
	private Intent m_displayIntensityIntent;
	
	// The data of the phone we are interested in
	private PhoneInfoDataOnly m_currentData;		// The data we have on the phone and want to display
	private PhoneInfoDataOnly m_lastData;			// The data we sent last time
	private Handler m_batHandler = new Handler();	// Handler for battery broadcasts

	
	// BT members and constants
    private static final UUID BT_SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); // SPP UID
    private static final String NAME_INSECURE = "PhoneInfoAcc"; // Name for the SDP record when creating server socket
	private BluetoothAdapter m_Adapter;
    private AcceptThread m_InsecureAcceptThread;
    private ConnectedThread m_ConnectedThread;
    public enum AccessoryConnState { None, Listen, Connecting, Connected };
    private AccessoryConnState m_accessoryConnState;		// See STATE_xxx constants above

    // Constants used by ContentObservers (happy Contento) to notify us of changes
    public static final int CONTENTO_INFOCHANGED = 0x1010;
    public static final int CONTENTO_MC = 0x1; // Missed Calls
    public static final int CONTENTO_US = 0x2; // Unread SMS
    
    // Notification to system notification area
    private NotificationManager m_NotificationMgr;
	private static final CharSequence m_NotificationTickerTitle = "PhoneInfo";
	
	// Andeoid2 style notiifcation
	private Notification m_drawerNotification = new Notification(R.drawable.bt7seg, m_NotificationTickerTitle, System.currentTimeMillis());
	
	private Intent m_notificationIntent;
	private PendingIntent m_notificationPendingIntent;
	private static final int PHONE_INFO_NOTIFICATION = 2266; // My birthday 2/2/1966

	// Handler to listen to events from the content observes of SMS/Calls
    private final Handler m_contentObserversHandler = new Handler()
    	{
        	@Override public void handleMessage(Message msg)
        	{
        		Log.d(TAG, "Handling message " + msg.what + " from " + msg.arg2);
        		switch (msg.what)
        		{
        		case CONTENTO_INFOCHANGED:
        			sendToBTAccessory();
        			sendDataToActivity();
        		break;
        		}
        	}
    	};

    // Broadcast receiver for system battery notifications
    private BroadcastReceiver m_batteryReceiver = new BroadcastReceiver()
    	{
			@Override public void onReceive(Context context, Intent intent)
			{
				if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED))
				{
					m_currentData.m_batteryPercentile = intent.getIntExtra("level", 0);
					m_currentData.m_batteryFull = (intent.getIntExtra("status", -1) == BatteryManager.BATTERY_STATUS_FULL);
					m_batHandler.post(new Runnable()
					{ 
						public void run()
						{
							Log.d(TAG, "Battery onReceive broadcast receiver");
							// Could not find a way to get notified once SMS message is read,
							// so using battery receiver to poll for SMS data.
							// The other alternative was to periodically do that, but that is
							// actually adding more code and CPU consumption for no good reason
							// Battery apparently broadcasts itself every 30 seconds, so that
							// is good enough for us.
							m_currentData.m_unreadSMS = m_unreadSMSContentObserver.dogetUnreadSMS();
							sendToBTAccessory();
							sendDataToActivity();
						}
					}
				);
				}
			}
    	};
	
	// Broadcast receiver for events coming from the UI activity
	public class FromActivityBR  extends BroadcastReceiver
	{
		@Override public void onReceive(Context context, Intent intent)
		{
			if (intent.getAction().equals(PhoneInfoActivity.BROADCAST_SET_DISPLAY_INTENTSITY))
			{
				int intensity = intent.getIntExtra(PhoneInfoActivity.DISPLAY_INTENSITY, 8);
				setDisplayIntensity(intensity);
			}
			else
				Log.d(TAG, "Unknown action received from activity");
		}
	};
	
	FromActivityBR m_fromActivityBR = new FromActivityBR();
	
	// The content observer
	private ContentResolver m_contentResolver;
    private MissedCallsContentObserver m_missedCallsContentOberserver;
    private UnreadSMSContentObserver m_unreadSMSContentObserver;
    private boolean m_contentObserversRegistered = false;

    // Data from Accessory reply field each time the service connects to Accessory
    private AccessoryInfo m_accessoryInfo = new AccessoryInfo();
    
    /* Constructor and service life cycle methods
     * ============================================================
     * */
    public PhoneInfoServer() {	}


    @Override public void onCreate()
    {
    	// Setup the different intents
    	m_phoneBasicInfoIntent 		= new Intent(BROADCAST_PHONE_BASIC_INFO);
    	m_accesoryDevicNameIntent 	= new Intent(BROADCAST_ACCESORY_DEVICE_NAME);
    	m_toastIntent 				= new Intent(BROADCAST_TOAST);
    	m_stateChangeIntent 		= new Intent(BROADCAST_STATE_CHANGE);
    	m_displayIntensityIntent 	= new Intent(BROADCAST_DISPLAY_INTENSITY);

    	// The Bluetooth stuff
    	m_Adapter = BluetoothAdapter.getDefaultAdapter(); 
		m_accessoryConnState = AccessoryConnState.None;

		// our data
		m_currentData = new PhoneInfoDataOnly();

		// Content observers
		m_contentResolver 				= this.getContentResolver();
		m_missedCallsContentOberserver 	= new MissedCallsContentObserver(m_contentResolver, m_contentObserversHandler);
		m_unreadSMSContentObserver 		= new UnreadSMSContentObserver(m_contentResolver, m_contentObserversHandler);
		m_NotificationMgr 				= (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
    }
    
    @Override public int onStartCommand(Intent intent, int flags, int startId)
    {
    	this.registerReceiver(m_fromActivityBR, new IntentFilter(PhoneInfoActivity.BROADCAST_SET_DISPLAY_INTENTSITY));
    	start();
    	
    	// Make ourselves a foreground service, otherwise the system will kill us after a while
    	m_notificationIntent  = new Intent(this, PhoneInfoActivity.class);
    	m_notificationIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    	m_notificationPendingIntent = PendingIntent.getActivity(this, 0, m_notificationIntent, 0);
    	m_drawerNotification.setLatestEventInfo(this, m_NotificationTickerTitle, "Idle", m_notificationPendingIntent); 
    	m_drawerNotification.flags |= Notification.FLAG_NO_CLEAR;
    	startForeground(PHONE_INFO_NOTIFICATION, m_drawerNotification);
    	
    	return START_STICKY;
    }
    
    @Override public IBinder onBind(Intent intent)
    {
        // We don't provide binding, so return null
        return null;
    }
    
    @Override public void onDestroy()
    {
    	Log.d(TAG, "onDestroy");
    	stop();
    	if (m_fromActivityBR != null)
    	{
    		unregisterReceiver(m_fromActivityBR);
    		m_fromActivityBR = null;
    	}
    	if (m_contentResolver != null)
    	{
    		m_contentResolver.unregisterContentObserver(m_unreadSMSContentObserver);
    		m_contentResolver.unregisterContentObserver(m_missedCallsContentOberserver);
    		m_unreadSMSContentObserver = null;
    		m_missedCallsContentOberserver = null;
    	}
    	stopForeground(true);
    	stopSelf();
    }
    
    
    /* Notification handling for new connection and disconnection
     * ============================================================
     */
    private void sendBarNotification()
    {
    	Context context = this.getApplicationContext();
    	CharSequence content;
    	if (m_accessoryConnState == AccessoryConnState.Connected)
    		content = "Connected to: " + m_accessoryInfo.getAccessoryType();
    	else
    		content = "Disconnected";
    	m_NotificationMgr.cancelAll();
    	m_drawerNotification.setLatestEventInfo(context, m_NotificationTickerTitle, content, m_notificationPendingIntent);
    	m_NotificationMgr.notify(PHONE_INFO_NOTIFICATION, m_drawerNotification);
    }
    
    
    /* Methods to communicate with parent activity
     * ============================================================
     */
    /** Sends a string to Activity using broadcast
     * @param i	The intent of this broadcast
     * @param s The string to send
     * @param tokenName	the name of the token to use inside the bundle
     */
    private void _sendStringToActivity(Intent i, String s, String tokenName)
    {
    	Bundle bundle = new Bundle();
    	bundle.putString(tokenName, s);
    	i.putExtra(tokenName, bundle);
    	sendBroadcast(i);
    }
    
    /** Sends an integer to Activity using broadcast
     * @param	i	The intent of this broadcast
     * @param	num	The integer to send
     * @param	tokenName	The name of the token to use inside the bundle
     */
    private void _sendIntToActivity(Intent i, int num, String tokenName)
    {
    	Bundle bundle = new Bundle();
    	bundle.putInt(tokenName, num);
    	i.putExtra(tokenName, bundle);
    	sendBroadcast(i);
    }
    
    // Sends the entire PhoneInfoDataOnly class to Activity
    private void sendDataToActivity()
    {
    	Log.d(TAG, "Sending data to activity");
        Bundle bundle = m_currentData.get();
        m_phoneBasicInfoIntent.putExtra(PHONE_BASIC_INFO, bundle);
        sendBroadcast(m_phoneBasicInfoIntent);
    }
    
    private void sendToastToActivity(String s)
    {
    	Log.d(TAG, "Sending toast to activity");
    	_sendStringToActivity(m_toastIntent, s, TOAST);
    }
    
    // The bluetooth device name not the accessory name
    private void sendDeviceNameToActivity(String deviceName)
    {
    	Log.d(TAG, "Sending device name to activity");
    	_sendStringToActivity(m_accesoryDevicNameIntent, deviceName, DEVICE_NAME);
    }
    
    private void sendStateChangedToActivity()
    {
    	/*
        public static final int STATE_NONE = 0;       // we're doing nothing
        public static final int STATE_LISTEN = 1;     // now listening for incoming connections
        public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
        public static final int STATE_CONNECTED = 3;  // now connected to a remote device
		*/
    	Log.d(TAG, "Sending state change to activity");
    	int num = 0;
    	switch(m_accessoryConnState)
    	{
		case Connected: num = 3;  break;
		case Connecting: num = 2; break;
		case Listen: num = 1; break;
		case None: num = 0; break;
		default:
			break;
    	
    	}
    	_sendIntToActivity(m_stateChangeIntent, num, STATE_CHANGE);
    }
    
    private void sendDisplayIntensityToActivity(int intensity)
    {
    	_sendIntToActivity(m_displayIntensityIntent, intensity, DISPLAY_INTENSITY);
    }


    /* Methods to communicate with the BT device
     * =============================================== 
     */
    public void setDisplayIntensity(int di)
    {
    	Log.d(TAG, "Setting intensity to " + di);
       	ConnectedThread r;
    	
    	synchronized(this)
    	{
			if (m_accessoryConnState != AccessoryConnState.Connected)
				return;
			r = m_ConnectedThread;
		}
    	String s = "I" + Integer.toHexString(di) + '\r';
    	Log.d(TAG, "Sending " + s + " to BT accessory");
		r.write(s.getBytes());
    }

    // Sends the battery percentile/full missed calls and unread SMS data to accessory over BT
    private void sendToBTAccessory()
    {
		ConnectedThread r;

		synchronized(this)
		{
			if (m_accessoryConnState != AccessoryConnState.Connected)
				return;
			r = m_ConnectedThread;
		}
		if (m_currentData == null)
		{
			Log.e(TAG, "send() does not have any data");
			return;
		}

		// Fetch the values from the content observers
		m_currentData.m_missedCalls = m_missedCallsContentOberserver.getMissedCalls();
		m_currentData.m_unreadSMS = m_unreadSMSContentObserver.getUnreadSMS();

		// if no change in data, do not send (preserving battery power)
		if (m_lastData != null) // do we have last data?
			if (m_lastData.equal(m_currentData))
				return; // no change
			else // set to new values
				m_lastData.set(m_currentData);
		else // create last data
			m_lastData = new PhoneInfoDataOnly(m_currentData.m_missedCalls, m_currentData.m_unreadSMS, m_currentData.m_batteryPercentile, m_currentData.m_batteryFull);

		// Build the string to send, now you tell me isn't sprintf a more elegant way?
		String s = "B" + m_currentData.m_batteryPercentile + "/";
		s += m_currentData.m_batteryFull ? "1" : "0";
		s += "S" + m_currentData.m_unreadSMS;
		s += "C" + m_currentData.m_missedCalls + "\r";

		Log.d(TAG, "Sending " + s + "to BT accessory");

		// write through the writing thread
		r.write(s.getBytes());
	}
	

    // Sends 'i' to the accessory, which should result with information about the accessory
    private void queryBTAccessory()
    {
    	ConnectedThread r;
		
		synchronized(this)
		{
			if (m_accessoryConnState != AccessoryConnState.Connected)
				return;
			r = m_ConnectedThread;
		}

		Log.d(TAG, "Query BT accessory");
		String q = "i\r";
		
		// write through the writing thread
		r.write(q.getBytes());
    }

    
    /*
     * State get and set methods to handle state changes
     * ============================================================
     */
    private synchronized void setState(AccessoryConnState state)
    {
        m_accessoryConnState = state;

        Log.d(TAG, "Setting state to " + state);
        sendStateChangedToActivity();	// Tell the activity
        sendBarNotification();			// And change the drawer info
    }

    // Return the current connection state.
    public synchronized AccessoryConnState getState()
    {
        return m_accessoryConnState;
    }
    

    /*
     * Stating and stopping the service (internally to my class)
     * ============================================================
     */
     // Start the chat service. Specifically start AcceptThread to begin a
     // session in listening (server) mode. Called by service lifecycle method onStartCommand
    public synchronized void start()
    {
    	Log.d(TAG, "start() staring");
    	if (m_accessoryConnState == AccessoryConnState.Connected) // If we do not do that, accessory will disconnect and connect again on application resume
    		return;
    	
    	Log.d(TAG, "start() cleaning threads");
        // Cancel any thread currently running a connection
        if (m_ConnectedThread != null)
        {
        	m_ConnectedThread.cancel();
        	m_ConnectedThread = null;
        }

        // Register the content observers 
    	m_lastData = null;
    	if (m_contentObserversRegistered == false)
    	{ 
    		// Only once
    		if (m_contentResolver != null)
    		{
    			m_currentData.m_unreadSMS = m_unreadSMSContentObserver.dogetUnreadSMS();
    			m_currentData.m_missedCalls = m_missedCallsContentOberserver.getMissedCalls();

    			Log.d(TAG, "PhoneInfo.start registering content observers");
    			m_contentResolver.registerContentObserver(Calls.CONTENT_URI, true, m_missedCallsContentOberserver);
    			m_contentResolver.registerContentObserver(Uri.parse("content://sms/"), true, m_unreadSMSContentObserver);
    			m_contentResolver.registerContentObserver(Uri.parse("content://sms/inbox/"), true, m_unreadSMSContentObserver);
    	        // Put the Battery callbacks
    			this.registerReceiver(PhoneInfoServer.this.m_batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    			Log.d(TAG, "PhoneInfo.start done registration");
    			m_contentObserversRegistered = true;
    		}
    	}

        Log.d(TAG, "start() creating AcceptThread");
        setState(AccessoryConnState.Listen);
        if (m_InsecureAcceptThread == null)
        {
            m_InsecureAcceptThread = new AcceptThread();
            m_InsecureAcceptThread.start();
        }
        
        Log.d(TAG, "start() complete.");
    } 
    
    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType)
    {
    	Log.d(TAG, "PhoneInfo.connected(): socket type:" + socketType + "Canceling other threads");

        // Cancel any thread currently running a connection
        if (m_ConnectedThread != null)
        {
        	m_ConnectedThread.cancel();
        	m_ConnectedThread = null;
        }

        // Cancel the accept thread because we only want to connect to one device
        if (m_InsecureAcceptThread != null)
        {
            m_InsecureAcceptThread.cancel();
            m_InsecureAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        Log.d(TAG, "PhoneInfo.connected(): Creating connected thread");
        m_ConnectedThread = new ConnectedThread(socket, socketType);
        m_ConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Log.d(TAG, "PhoneInfo.connected(): Sending device name to calling activity");
        sendDeviceNameToActivity(device.getName());
        setState(AccessoryConnState.Connected);
        sendToBTAccessory();	// Start by sending data to accessory
        //queryBTAccessory();		// Query the the accessory for its information
    }
    
    // Stop all threads
    public synchronized void stop()
    {

    	Log.d(TAG, "PhoneInfo.stop(): stop all threads");

        if (m_ConnectedThread != null)
        {
            m_ConnectedThread.cancel();
            m_ConnectedThread = null;
        }

        if (m_InsecureAcceptThread != null)
        {
            m_InsecureAcceptThread.cancel();
            m_InsecureAcceptThread = null;
        }
        
        m_lastData = null;
        setState(AccessoryConnState.None);
    } 
    
    // Indicate that the connection was lost and notify the UI Activity.
    private void connectionLost()
    {
        // Send a failure message back to the Activity
    	sendToastToActivity("Device connection was lost");
    	setState(AccessoryConnState.None);
    	
        // Start this service over to so that we get the accepting thread
    	// back again
        PhoneInfoServer.this.start();
    }
    
    // We had a problem doing Accept
    private void acceptProblem()
    {
        sendToastToActivity("Could not create BT service.\nTry disable and enable BT");
    }

/* =====================================================================================
 * The different types of threads we will be managing: AcceptThread to wait for the BT
 * accessory to connect to the phone abd ConnectedThread that will do actual work with
 * the accessory once connected
 * =====================================================================================    
 */
/**
 * This thread runs while listening for incoming connections. It behaves
 * like a server-side client. It runs until a connection is accepted
 * (or until cancelled).
 */
private class AcceptThread extends Thread {
    // The local server socket
	static private final String TAG = "PhoneInfoAcceptThread";
    private final BluetoothServerSocket mmServerSocket;
    private String socketType;


    /** Creates an thread for accepting incoming Bluetooth connections
     * @param secure	Currently ignored, but suppose to represent the mode of socket.
     * All communication is currently done over insecure socket 
     */
    public AcceptThread()
    {
        BluetoothServerSocket tmp = null;
        socketType = "Insecure";

        // Create a new listening server socket
        try
        {
        	Log.d(TAG, "AcceptThread constructor trying to create listening socket");
            tmp = m_Adapter.listenUsingRfcommWithServiceRecord(NAME_INSECURE, BT_SPP_UUID);
            Log.d(TAG, "AcceptThread: Listening BT Socket " + socketType + " created");
        }
        catch (IOException e)
        {
            Log.e(TAG, "AcceptThread: Listening BT Socket Type: " + socketType + " listen() failed " + e.getMessage());
            acceptProblem();
        }
        mmServerSocket = tmp;
    }


    public void run()
    {
    	
    	if (mmServerSocket == null)
    	{
    		Log.e(TAG, "AcceptThread.run: No server socket");
    		return;
    	}
    	Log.d(TAG, "AcceptThread.run: socket type:" + socketType);
        setName("AcceptThread" + socketType);

        BluetoothSocket socket = null;

        // Listen to the server socket if we're not connected
        while (m_accessoryConnState != AccessoryConnState.Connected)
        {
            try
            {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                socket = mmServerSocket.accept();
                Log.d(TAG, "AcceptThread.run: returned from accept");
            }
            catch (IOException e)
            {
                Log.e(TAG, "AcceptThread.run: Socket Type: " + socketType + "accept() failed " + e.getMessage());
                break;
            }

            // If a connection was accepted
            if (socket != null)
            {
                synchronized (PhoneInfoServer.this)
                {
                	Log.d(TAG, "AcceptThread.run: Got accept request");
                    switch (m_accessoryConnState)
                    {
                    case Listen:
                    case Connecting:
                        // Situation normal. Start the connected thread.
                        connected(socket, socket.getRemoteDevice(), socketType);
                        break;
                    case None:
                    case Connected:
                        // Either not ready or already connected. Terminate new socket.
                        try { socket.close(); }
                        catch (IOException e) { Log.e(TAG, "Could not close unwanted socket" + e.getMessage()); }
                        break;
                    }
                }
                Log.d(TAG, "AcceptThread.run: state is " + m_accessoryConnState);
                if (m_accessoryConnState == AccessoryConnState.Connected)
                	sendToBTAccessory();
            }
        }
    }
 

    public void cancel()
    {
    	Log.d(TAG, "Canceling " + this + " Socket Type " + socketType); 
        try
        {
        	if (mmServerSocket != null)
        		mmServerSocket.close();
        }
        catch (IOException e) { Log.e(TAG, "AcceptThread.cancel: Socket Type" + socketType + "close() of server failed" + e.getMessage()); }
    }
}




/**
 * This thread runs during a connection with a remote device.
 * It handles all incoming and outgoing transmissions.
 */
private class ConnectedThread extends Thread
{
	static private final String TAG = "PhoneInfoConnectedThread";
    private final BluetoothSocket mm_socket;
    private final InputStream mm_inStream;
    private final OutputStream mm_outStream;


    public ConnectedThread(BluetoothSocket socket, String socketType)
    {
        Log.d(TAG, "create ConnectedThread: " + socketType);
        mm_socket = socket;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        // Get the BluetoothSocket input and output streams
        try
        {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
            Log.d(TAG, "In and out streams created");
        }
        catch (IOException e) {  Log.e(TAG, "temp sockets not created " + e.getMessage()); }

        mm_inStream = tmpIn;
        mm_outStream = tmpOut;
    }


    // this is where we will spend out time when connected to the accessory.
    // When we start, the Accessory will respond to the 'i' query send to it
    // from the connected() method of the service, but afterwards, the connected
    // thread will be blocked on the call for read() that should not return anything
    // as the accessory is not sending any data. Note the the Write will be called
    // though to update the accessory on any change to information
    public void run()
    {
        Log.d(TAG, "ConnectedThread.run");

        if (mm_inStream == null)
        {
        	Log.e(TAG, "ConnectedThread.run: no InStream");
        	return;        	
        }
        queryBTAccessory();		// Query the the accessory for its information
        // Keep listening to the InputStream while connected
        while (true)
        {
            try
            {
            	Log.d(TAG, "ConnectedThread.run: Reading from InStream socket");
                // Read from the InputStream
            	while (true)
            	{
            		int inB;
            		if (m_accessoryInfo.isAccessoryInfoAvailable())
            		{
            			// We suppose to get here only once, after receiving all data from
            			// the accessory reply to the 'i' query sent to it by 'connected' method
            			Log.d(TAG, "Accessory display" +
            					" type=" + m_accessoryInfo.getAccessoryType() +
            					" H=" + m_accessoryInfo.getAccessoryHeight() +
            					" W=" + m_accessoryInfo.getAccessoryWidth() );
            			sendDisplayIntensityToActivity(m_accessoryInfo.getAccessoryIntensity());
            			sendBarNotification();
            		}
            		inB = mm_inStream.read();
            		if (inB != -1)
            			m_accessoryInfo.addCharacter((byte)inB);
            	}
            }
            catch (IOException e)
            {
                Log.e(TAG, "InStream read exception, disconnected " + e.getMessage());
                connectionLost();
                break;
            }
        }
    }
    
    
    /**
     * Write to the connected OutStream.
     * @param buffer  The bytes to write
     */
    public void write(byte[] buffer)
    {
    	if (mm_outStream == null)
    	{
    		Log.e(TAG, "ConnectedThread.write: no OutStream");
    		return;
    	}
        try
        {
        	Log.d(TAG, "ConnectedThread.write: writing " + buffer.length + " bytes");
            mm_outStream.write(buffer);

            // Share the sent message back to the UI Activity
            //mHandler.obtainMessage(PhoneInfoActivity.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            Log.d(TAG, "ConnectedThread.write: sent to calling activity");
        }
        catch (IOException e) { Log.e(TAG, "Exception during write" + e.getMessage()); }
    }


    public void cancel()
    {
        try
        {
        	Log.d(TAG, "ConnectedThread.cancel: closing socket");
        	if (mm_socket != null)
        		mm_socket.close();
        }
        catch (IOException e) { Log.e(TAG, "ConnectedThread.cancel: socket.close() failed" + e.getMessage()); }
    }
}



} 