/* =============================================================================
 * Accessory for displaying information from cellular phone on a display
 * =============================================================================
 * By: Zakie Mashiah zmashiah@gmail.com											
 * ============================================================================= */

#ifndef __IDISPLAY_H
#define __IDISPLAY_H

#include <Arduino.h>
#include "phoneInfo.h"

class IDisplayAdapter
{
public:
	virtual void init() = 0;
	virtual void print(PhoneBasicInfo *ppi) = 0;
    virtual void showConnected() = 0;
	virtual void showDisconnected() = 0;
	virtual void shutdown(boolean b) = 0;
	virtual void clear() = 0;
	virtual void setIntensity(int intensity) = 0;
	virtual char *getDisplayInfo() = 0;
	virtual void tick() = 0;
};

#endif
