/* =====================================================================================================
 * Accessory Info holds and parses the information on the accessory like description, type and geometry
 * of the display.
 * =====================================================================================================
 * By: Zakie Mashiah 
 * =====================================================================================================
 */
package com.zakiem.myPhoneInfo;

import android.util.Log;
import com.google.gson.Gson;;

/* Input description
 * 		type=string
 * 		intensity=number
 * 		height=number
 * 		width=number
 */
public class AccessoryInfo
{
	private static final String _TAG = "PhoneInfoAccessoryInfo";
	
	public class AccessoryPacket
	{
		String type;
		int	intensity;
		int height;
		int width;
		
		public AccessoryPacket()
		{
			type = null;
			intensity = 0;
			height = 0;
			width = 0;
		}
	}
	
	private byte[] 				m_buffer;
	private int    				m_bufferPos;
	private AccessoryPacket 	m_data;
	private boolean				m_accessoryInfoAvailable;
	
	public AccessoryInfo()
	{
		m_data = new AccessoryPacket();
		m_buffer = new byte[128];
		m_accessoryInfoAvailable = false;
	}
	
	public String getAccessoryType() { return m_data.type; }
	public int getAccessoryIntensity() { return m_data.intensity; }
	public int getAccessoryHeight() { return m_data.height; }
	public int getAccessoryWidth() { return m_data.width; }
	public boolean isAccessoryInfoAvailable() { return m_accessoryInfoAvailable; }

	/** 
	 * Adds a character from the BT socket to internal m_buffer and process the m_buffer if character is \r
	 * @param b	The character from the BT socket
	 */
	public void addCharacter(byte b) {
		if (b == '\r')
			processBuffer();
		m_buffer[m_bufferPos] += b;
		m_bufferPos++;
		if (m_bufferPos >= 128)
		{
			m_bufferPos = 0;
			java.util.Arrays.fill(m_buffer, (byte)0);
		}
	}

	/**
	 * Process the current m_buffer from the BT socket
	 */
	public void processBuffer() {
			
		String streamLine = new String(m_buffer, 0, m_bufferPos);
		Gson g = new Gson();
		boolean parseOK = false;
		AccessoryPacket d = null;
		
		Log.d(_TAG, "processing " + streamLine);
		try
		{
			d = g.fromJson(streamLine, AccessoryPacket.class);
			parseOK = true;
		}
		catch (Exception e) { }
		
		if (parseOK == false)
		{
			Log.e(_TAG, "Parse error to info: " + streamLine);
			return;
		}
		
		m_data = d;
		m_accessoryInfoAvailable = true;
		
		// Clear the m_buffer
		m_bufferPos = 0;
		java.util.Arrays.fill(m_buffer, (byte)0);
	}
	

}
