/* =============================================================================
 * Accessory for displaying information from cellular phone on a display
 * =============================================================================
 * By: Zakie Mashiah zmashiah@gmail.com											
 * ============================================================================= */



#include "IDisplay.h"
#include "MaxSevenSegment.h"

#define DISPLAY_CLK_PIN     6
#define DISPLAY_CS_PIN      5
#define DISPLAY_DATA_PIN    4




MaxSevenSegment::MaxSevenSegment() : LedControl(DISPLAY_DATA_PIN, DISPLAY_CLK_PIN, DISPLAY_CS_PIN, 1) { intensity = 8;}

void MaxSevenSegment::shutdown(boolean b) { LedControl::shutdown(0, b); }

void MaxSevenSegment::clear() { LedControl::clearDisplay(0); }

void MaxSevenSegment::print(PhoneBasicInfo *pPhoneInfo)
{
    char buff[10];
    const char *fmt;
    int b, s, c;

    // if  battery is 100% see if we have enough room for 3 digits or not for battery display
    b = pPhoneInfo->battery;
    s = pPhoneInfo->unreadSMS;
    c = pPhoneInfo->missedCalls;
    if ((b == 100) && ((s < 10) || (c < 10)) )
    {
        if (pPhoneInfo->unreadSMS < 10)
            fmt = "%3d%2d %2d";
        else
            fmt = "%3d %2d%2d";
    }
    else
    {
        // Either not 100% battery or we do not have enough room for 3 digits of battery
        b = limit99(b);
        if (pPhoneInfo->batteryFull)
            fmt = "F  %2d %2d";
        else
            fmt = "%2d %2d %2d";
    }
    s = limit99(s);
    c = limit99(c);
    sprintf(buff, fmt, b, s, c);
    print(buff);
}



void MaxSevenSegment::print(const char *str)
{
    int i;

    for(i=0; (i<8) && (str[i] != 0); i++)
        setChar(0, i, str[i], false);
}

char *MaxSevenSegment::getDisplayInfo()
{
    static const char info[] = "{\"type\":\"7seg\", "		// JSON document with display information
                               "\"intensity\":%d, "
                               "\"height\":1, "
                               "\"width\":8 }\n";
   static char buffer[70];
   
   sprintf(buffer, info, (int)intensity);
   return buffer;
}


void MaxSevenSegment::setIntensity(int inten)
{
    intensity = inten;
    LedControl::setIntensity(0, inten);

}

void MaxSevenSegment::showConnected() { print("connectd"); }
void MaxSevenSegment::showDisconnected() { print("dcon"); }
void MaxSevenSegment::init()
{
	shutdown(false);
	setIntensity(8);
	clear();
	showDisconnected();
}

void MaxSevenSegment::tick() { }