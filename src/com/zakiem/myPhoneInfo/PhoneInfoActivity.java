/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/* =====================================================================================================
 * The UI Activity of the application that connects a Bluetooth accessory to and Android phone and
 * displays information on battery, missed calls and unread SMS messages
 * The Activity activates the service coded in PhoneInfoServer.java that keeps running all the time
 * =====================================================================================================
 * By: Zakie Mashiah 
 * =====================================================================================================
 */

package com.zakiem.myPhoneInfo;


import com.zakiem.myPhoneInfo.PhoneInfoServer.AccessoryConnState;

import myPhoneInfo.test.zakiem.R;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The main activity
 **
 */
public class PhoneInfoActivity extends Activity
{
	private static final String TAG = "PhoneInfoActivity";
	public  static final String BROADCAST_SET_DISPLAY_INTENTSITY = "com.zakiem.myPhoneInfo.SET_DISPLAY_INTENSITY";
	public  static final String DISPLAY_INTENSITY = "DisplayIntensity";

	// Intent request codes for DeviceList Activity
    private static final int REQUEST_ENABLE_BT = 3;

    // Message types sent from the PhoneInfoServer Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_REFRESHSCREEN = 6;
    private BluetoothAdapter m_btAdapter = null;
    private PhoneInfoDataOnly m_currentData;
    private int m_displayIntensity = 8;

    // Name of the connected device
    private String m_connectedDeviceName = null;

    private Intent m_serviceIntent;
    private Intent m_talkToService;
    
    // The different UI elements
	private TextView m_batteryView;
	private TextView m_unreadSMSView;
	private TextView m_missedCallsView;
	private SeekBar  m_displayInensitySeekBar;
	private ImageView m_btStatus;

    // The Handler that gets information back from the PhoneInfoServer
    public class FromServiceBR extends BroadcastReceiver{
    	private AccessoryConnState connectionStateCode(int s)
    	{
    		switch(s)
    		{
    		case 0: return AccessoryConnState.None;
    		case 1: return AccessoryConnState.Listen;
    		case 2: return AccessoryConnState.Connecting;
    		case 3: return AccessoryConnState.Connected;
    		}
    		// Should never be here
    		Log.e(TAG, "Got BT connection change message to unknown state");
    		return AccessoryConnState.None;
    	}
        @Override public void onReceive(Context context, Intent intent)
        {
        	String action = intent.getAction();
        	Bundle bundle;
        	if (action.equals(PhoneInfoServer.BROADCAST_ACCESORY_DEVICE_NAME))
        	{
        		bundle = intent.getBundleExtra(PhoneInfoServer.DEVICE_NAME);
        		String dn = bundle.getString(PhoneInfoServer.DEVICE_NAME);
        		if (dn != null)
        		{
        			m_connectedDeviceName = dn;
        			if (m_connectedDeviceName == null)
        				dn = "unknown";
        			Log.d(TAG, "Setting device name to: " + dn);
        			m_btStatus.setImageResource(R.drawable.ic_btc); // If we are getting device name, we are connected
        			Toast.makeText(getApplicationContext(), "Connected to: " + dn, Toast.LENGTH_SHORT).show();
        		}
        		else
        			Log.d(TAG, "Error setting device name: No device name");
        	}
        	else if (action.equals(PhoneInfoServer.BROADCAST_DISPLAY_INTENSITY))
        	{
        		bundle = intent.getBundleExtra(PhoneInfoServer.DISPLAY_INTENSITY);
        		m_displayIntensity = bundle.getInt(PhoneInfoServer.DISPLAY_INTENSITY);
        		m_displayInensitySeekBar.setProgress(m_displayIntensity);	// Update the seek-bar control
        		Log.d(TAG, "Device current intensity is:" + m_displayIntensity);
    			m_btStatus.setImageResource(R.drawable.ic_btc); // If we are getting device name, we are connected
        	}
        	else if (action.equals(PhoneInfoServer.BROADCAST_PHONE_BASIC_INFO))
        	{
        		bundle = intent.getBundleExtra(PhoneInfoServer.PHONE_BASIC_INFO);
        		PhoneInfoDataOnly newData = new PhoneInfoDataOnly();
            	newData.set(bundle);
            	if (m_currentData.equal(newData) == false )
            	{
            		m_currentData.set(newData);
            		Log.d(TAG, "refreshing screen");
        			m_btStatus.setImageResource(R.drawable.ic_btc); // If we are getting device name, we are connected
            		showOnScreen();
            	}
            	else
            		Log.d(TAG, "New information received. No change");
        	}
        	else if (action.equals(PhoneInfoServer.BROADCAST_STATE_CHANGE))
        	{
        		bundle = intent.getBundleExtra(PhoneInfoServer.STATE_CHANGE);
        		int newstate = bundle.getInt(PhoneInfoServer.STATE_CHANGE);
        		AccessoryConnState acs = connectionStateCode(newstate);
        		switch (acs)
        		{
        		case Connected:
        			Log.d(TAG, "Handler: state connected");
                	m_btStatus.setImageResource(R.drawable.ic_btc);
                	String dn = (m_connectedDeviceName == null) ? "unknown" : m_connectedDeviceName;
                	Toast.makeText(getApplicationContext(), dn + " is now connected", Toast.LENGTH_SHORT).show();
                    break;
                case Connecting:
                	Log.d(TAG, "Handler: state connecting");
                	m_btStatus.setImageResource(R.drawable.ic_btnc);
                	m_connectedDeviceName = null;
                    break;
                case Listen:
                	Log.d(TAG, "Handler: state listen");
                	m_btStatus.setImageResource(R.drawable.ic_btnc);
                	m_connectedDeviceName = null;
                    break;
                case None:
                	Log.d(TAG, "Handler: state none");
                	m_btStatus.setImageResource(R.drawable.ic_btnc);
                	m_connectedDeviceName = null;
                    break;
                }
        	}
        	else if (action.equals(PhoneInfoServer.BROADCAST_TOAST))
        	{
        		bundle = intent.getBundleExtra(PhoneInfoServer.TOAST);
        		String message = bundle.getString(PhoneInfoServer.TOAST);
        		if (message != null)
        		{
        			Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
        			Log.d(TAG, "Showing toast message " + message);
        		}
        		else
        			Log.d(TAG, "null message to toast, ignoring");
        	}
        	else
        		Log.e(TAG, "Unknown broadcast message received");
        }
    };
    
    private FromServiceBR m_serviceBroadcastReceiver = new FromServiceBR();


    /* =======================================================================================
     * The Activity lifecycle functions
     * =======================================================================================
     */
    
    @Override public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.phone_info_layout);
        m_btStatus = (ImageView)this.findViewById(R.id.ic_bt_status);

        Log.i(TAG, "Starting PhoneInfo Activity");
       
        // Set the BT adapter
        m_btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (m_btAdapter == null)
        {
        	Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
        	finish();
        	return;
        }

        // Setup the data and views/layout
        m_currentData = new PhoneInfoDataOnly();
        setViews();

        // Set the service intent and broadcast receiver
        m_serviceIntent = new Intent(this, PhoneInfoServer.class);
        m_talkToService = new Intent(PhoneInfoActivity.BROADCAST_SET_DISPLAY_INTENTSITY);
    }

    // Function to be called onCreate to set all the views of the layout for UI
    public void setViews()
    {
		m_batteryView = (TextView)findViewById(R.id.batteryInfo);
		m_unreadSMSView = (TextView)findViewById(R.id.unreadSMSInfo);
		m_missedCallsView = (TextView)findViewById(R.id.missedCallsInfo);
		m_displayInensitySeekBar = (SeekBar)findViewById(R.id.displayInentisty);
		m_displayInensitySeekBar.setProgress(m_displayIntensity);
		m_displayInensitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
			{
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch)
				{
					m_displayIntensity = (progress < 0) ? 0 : (progress > 15) ? 15 : progress; // value between 0 to 15 (0x00 - 0x0F)
					m_talkToService.putExtra(DISPLAY_INTENSITY, m_displayIntensity);
					sendBroadcast(m_talkToService);
				}
				public void onStopTrackingTouch(SeekBar seekBar) {}
				public void onStartTrackingTouch(SeekBar seekBar)
				{
					seekBar.setProgress(m_displayIntensity);
				}
			}
		); 
    }
    
    
    @Override protected void onStart()
    {
        super.onStart();

        Log.d(TAG, "onStart()");
        if (m_btAdapter.isEnabled() == false)
        {
        	Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        	startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
        else
        {
        	Log.d(TAG, "Starting phoneInfo");
        	startService(m_serviceIntent);
        }
    }
    
    
    
    private void showOnScreen()
    {
    	PhoneInfoDataOnly d = m_currentData;
    	String status = "Battery: ";
        status += (d.m_batteryFull) ? "Full" : Integer.toString(d.m_batteryPercentile) + "%";
        m_batteryView.setText(status);

        status = "Unread SMS: ";
        status += Integer.toString(d.m_unreadSMS);
        m_unreadSMSView.setText(status);

        status = "Missed Calls: ";
        status += Integer.toString(d.m_missedCalls);
        m_missedCallsView.setText(status);
    }
    
        
    @Override public void onDestroy()
    {
    	Log.d(TAG, "onDestroy");
    	if (m_serviceBroadcastReceiver != null)
    		unregisterReceiver(m_serviceBroadcastReceiver);
    	super.onDestroy();
    }

        
    @Override protected void onRestart()
    {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    
    @Override public void onResume()
    {
        super.onResume();
        Log.d(TAG, "onResume()");
        IntentFilter ifFromService = new IntentFilter();
        ifFromService.addAction(PhoneInfoServer.BROADCAST_ACCESORY_DEVICE_NAME);
        ifFromService.addAction(PhoneInfoServer.BROADCAST_DISPLAY_INTENSITY);
        ifFromService.addAction(PhoneInfoServer.BROADCAST_PHONE_BASIC_INFO);
        ifFromService.addAction(PhoneInfoServer.BROADCAST_STATE_CHANGE);
        ifFromService.addAction(PhoneInfoServer.BROADCAST_TOAST);
        this.registerReceiver(m_serviceBroadcastReceiver, ifFromService);
        showOnScreen();
    }
    
    /* =======================================================================================
     * Menu constants and code
     * =======================================================================================
     */
    private static final String _ExitMenu = "Exit";
    private static final String _ShowMACMenu = "Show MAC";
    
    @Override public boolean onCreateOptionsMenu(Menu menu)
    {
    	menu.add(_ShowMACMenu);
		menu.add(_ExitMenu);
		return true;
	}

    
    @Override public boolean onOptionsItemSelected(MenuItem item)
    {
    	if (item.getTitle() == _ShowMACMenu)
    	{
    		if (m_btAdapter != null)
    		{
    			String s = m_btAdapter.getAddress();
    			if (s != null)
    				Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    		}
    	}
    	else if (item.getTitle() == _ExitMenu)
    	{
			finish();
			stopService(m_serviceIntent);
			System.exit(0);
		}
		return true;
	}
 }
