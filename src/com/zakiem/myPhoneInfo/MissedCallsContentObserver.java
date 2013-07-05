/* =====================================================================================================
 * Content observer for the missed calls log
 * =====================================================================================================
 * By: Zakie Mashiah 
 * =====================================================================================================
 */
package com.zakiem.myPhoneInfo;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.provider.CallLog.Calls;
import android.util.Log;

public class MissedCallsContentObserver extends ContentObserver
{
	private static final String _TAG = "PhoneInfoMC";
	private Handler 			m_handler;
	private ContentResolver 	m_contentResolver;
	private int 				m_missedCalls;
	
    public MissedCallsContentObserver(ContentResolver cr, Handler h)
    {
        super(null);
        m_contentResolver = cr;
        m_handler = h;
    }
    
    public int getMissedCalls()
    {
    	final String[] projection = null;
        final String selection = null;
        final String[] selectionArgs = null;
        final String sortOrder = android.provider.CallLog.Calls.DATE + " DESC";
        Cursor cursor = null;
        int count = 0;
        
        try
        {
        	ContentResolver cr = m_contentResolver;
        	if (cr != null)
        	{
        		cursor = cr.query(Calls.CONTENT_URI, projection,  selection, selectionArgs, sortOrder);
        		while (cursor.moveToNext())
        		{
        			//String callLogID = cursor.getString(cursor.getColumnIndex(android.provider.CallLog.Calls._ID));
        			String callNumber = cursor.getString(cursor.getColumnIndex(android.provider.CallLog.Calls.NUMBER));
        			String callDate = cursor.getString(cursor.getColumnIndex(android.provider.CallLog.Calls.DATE));
        			String callType = cursor.getString(cursor.getColumnIndex(android.provider.CallLog.Calls.TYPE));
        			String isCallNew = cursor.getString(cursor.getColumnIndex(android.provider.CallLog.Calls.NEW));
        			if(Integer.parseInt(callType) == android.provider.CallLog.Calls.MISSED_TYPE && Integer.parseInt(isCallNew) > 0)
        			{
        				Log.v(callDate, "Missed Call Found: " + callNumber);
        				count++;
        			}
                }
        		m_missedCalls = count;
        	}
        }
        catch(Exception ex)
        {
            Log.e("ERROR: " + ex.toString(), "");
        }
        finally
        {
        	if (cursor != null)
        		cursor.close();
        }
    	return m_missedCalls;
    }

    @Override public void onChange(boolean selfChange) {
    	Log.d(_TAG, "onChange");
        Cursor cursor = m_contentResolver.query(
            Calls.CONTENT_URI, 
            null, 
            Calls.TYPE +  " = ? AND " + Calls.NEW + " = ?", 
            new String[] { Integer.toString(Calls.MISSED_TYPE), "1" }, 
            Calls.DATE + " DESC ");

        //this is the number of missed calls
        //for your case you may need to track this number
        //so that you can figure out when it changes
        m_missedCalls = cursor.getCount(); 
        cursor.deactivate();
        Log.d(_TAG, m_missedCalls + " missed calls");
        m_handler.obtainMessage(PhoneInfoServer.CONTENTO_INFOCHANGED, PhoneInfoServer.CONTENTO_MC, m_missedCalls).sendToTarget();
        Log.d(_TAG, "done");
    }
}