/* =====================================================================================================
 * The information on the phone that we care about: Missed calls, UnreadSMS and battery state
 * =====================================================================================================
 * By: Zakie Mashiah 
 * =====================================================================================================
 */
package com.zakiem.myPhoneInfo;

import android.os.Bundle;


public class PhoneInfoDataOnly
{
	int m_missedCalls;
	int m_unreadSMS;
	int m_batteryPercentile;
	boolean m_batteryFull;
	
	public static final String PHONEINFO_DATA_MC = "mc";
    public static final String PHONEINFO_DATA_US = "us";
    public static final String PHONEINFO_DATA_BP = "bp";
    public static final String PHONEINFO_DATA_BF = "bf";

	public PhoneInfoDataOnly() { m_missedCalls = 0; m_unreadSMS = 0; m_batteryPercentile = 0; m_batteryFull = false; }
	public PhoneInfoDataOnly(int mc, int us, int bp, boolean bf) { set(mc, us, bp, bf); }
	public boolean equal(PhoneInfoDataOnly d) {
		if ((d.m_batteryFull == m_batteryFull) &&
			(d.m_batteryPercentile == m_batteryPercentile) &&
			(d.m_missedCalls == m_missedCalls) &&
			(d.m_unreadSMS == m_unreadSMS) )
			return true;
		return false;
	}
		public void set(int mc, int us, int bp, boolean bf) {
			m_missedCalls = mc;
			m_unreadSMS = us;
			m_batteryPercentile = bp;
			m_batteryFull = bf;
			
		}
		public void set(PhoneInfoDataOnly d) {
			m_missedCalls = d.m_missedCalls;
			m_unreadSMS = d.m_unreadSMS;
			m_batteryPercentile = d.m_batteryPercentile;
			m_batteryFull = d.m_batteryFull;
		}
		
		public void set(Bundle b) {
			m_missedCalls = b.getInt(PHONEINFO_DATA_MC);
        	m_unreadSMS = b.getInt(PHONEINFO_DATA_US);
        	m_batteryPercentile = b.getInt(PHONEINFO_DATA_BP);
        	m_batteryFull = b.getBoolean(PHONEINFO_DATA_BF);
		}
		
		public Bundle get() {
			Bundle bundle = new Bundle();
	        bundle.putInt(PHONEINFO_DATA_MC, m_missedCalls);
	        bundle.putInt(PHONEINFO_DATA_US, m_unreadSMS);
	        bundle.putInt(PHONEINFO_DATA_BP, m_batteryPercentile);
	        bundle.putBoolean(PHONEINFO_DATA_BF, m_batteryFull);
	        return bundle;
		}
}
