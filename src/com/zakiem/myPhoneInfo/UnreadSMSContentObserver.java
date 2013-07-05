/* =====================================================================================================
 * Content observer for the SMS messages
 * =====================================================================================================
 * By: Zakie Mashiah 
 * =====================================================================================================
 */

package com.zakiem.myPhoneInfo;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;


public class UnreadSMSContentObserver extends ContentObserver
{
	private static final String _TAG = "PhoneInfoSMS";
	private static final Uri SMS_INBOX = Uri.parse("content://sms/inbox");

	private ContentResolver m_contentResolver;
	private Handler m_handler;
	private int m_unreadSMS;
	
	
	public UnreadSMSContentObserver(ContentResolver cr, Handler h)
	{
		super(null);
		m_contentResolver = cr;
		m_handler = h;
	}
	
	public int getUnreadSMS() { return m_unreadSMS; }
	
	public int dogetUnreadSMS()
	{
    	if (m_contentResolver != null)
    	{
    		try
    		{
    			Cursor c = m_contentResolver.query(SMS_INBOX, null, "read = 0", null, null);
    			if (c != null)
    			{
    				m_unreadSMS = c.getCount(); 
    				c.deactivate();
    				Log.d(_TAG, m_unreadSMS + " unread SMS messages");
    			}
    		}
    		catch (Exception ex)
    		{
    			Log.e("ERROR: " + ex.toString(), "");
    		}
    	}
    	return m_unreadSMS;
	}
	
	@Override
	public void onChange(boolean selfChange)
	{
		Log.d(_TAG, "onChange");
    	if (m_contentResolver != null)
    	{
    		this.dogetUnreadSMS();
    		m_handler.obtainMessage(PhoneInfoServer.CONTENTO_INFOCHANGED, PhoneInfoServer.CONTENTO_US, m_unreadSMS).sendToTarget();
    		Log.d(_TAG, "done");
    	}
	}
}
