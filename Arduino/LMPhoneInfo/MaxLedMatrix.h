/* =============================================================================
 * Accessory for displaying information from cellular phone on a display
 * =============================================================================
 * By: Zakie Mashiah zmashiah@gmail.com											
 * ============================================================================= */


#ifndef __MAXLEDMATRIX_H
#define __MAXLEDMATRIX_H

#include "phoneInfo.h"
#include "IDisplay.h"

class MaxLedMatrix: public IDisplayAdapter
{
private:
    byte intensity;
	LedControl battery;
	LedControl sms;
	LedControl calls;
	boolean lowBatDisplayToggle;	// toggled by tick to display sign of battery or percentile number
	int      last_bat;		// Value of last time battery was set. If -1, Phone is disconnected
public:
				 MaxLedMatrix();
	virtual void init();
	virtual void print(PhoneBasicInfo *ppi);
	virtual void showConnected();
	virtual void showDisconnected();    
    virtual void shutdown(boolean b);
    virtual void clear();
    virtual void setIntensity(int intensity);
	virtual char *getDisplayInfo();
	virtual void tick();
private:
    int limit99(int x) { return (x > 99) ? 99 : x; }
	void showNumber(LedControl *lc, int num);
    void showSmallFontNumber(LedControl *lc, int num);
	void showEmptyBattery(int bat);
	void showFullBattery(int bat);
};

#endif
