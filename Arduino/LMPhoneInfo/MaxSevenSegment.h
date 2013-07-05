/* =============================================================================
 * Accessory for displaying information from cellular phone on a display
 * =============================================================================
 * By: Zakie Mashiah zmashiah@gmail.com											
 * ============================================================================= */


#ifndef __MAXSEVENSEGMENT_H
#define __MAXSEVENSEGMENT_H

#include "LedControl.h"
#include "phoneInfo.h"
#include "IDisplay.h"

class MaxSevenSegment : public IDisplayAdapter, public LedControl
{
    byte intensity;
public:
    MaxSevenSegment();
    virtual void init();
    virtual void print(PhoneBasicInfo *ppi);
	virtual void showConnected();
	virtual void showDisconnected();
    virtual void shutdown(boolean b);
    virtual void clear();
    virtual char *getDisplayInfo();
    virtual void setIntensity(int intensity);
    virtual void tick();
private:
    void print(const char *str);
    int limit99(int x) { return (x > 99) ? 99 : x; }
    void showSmallFontNumber(LedControl *lc, int bank, int num);
};

#endif
