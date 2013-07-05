/* =============================================================================
 * Accessory for displaying information from cellular phone on a display
 * =============================================================================
 * By: Zakie Mashiah zmashiah@gmail.com											
 * ============================================================================= */


#include "LedControl.h"
#include "IDisplay.h"
#include "MaxLedMatrix.h"

#define SWAP_DISP_EVERY 2000
#define EMPTY_BAT_SIGN_THRESHOLD 15

/* ==================================================================================
 * connection pins
 * ==================================================================================
 */
#define DISPLAY_CLK_PIN     6
#define DISPLAY_CS_PIN      5
#define DISPLAY_DATA_PIN    4

#define SMSDISP_DATA_PIN	7
#define SMSDISP_CS_PIN		8
#define SMSDISP_CLK_PIN		9
#define CALLSDISP_DATA_PIN	10
#define CALLSDISP_CS_PIN	11
#define CALLSDISP_CLK_PIN   12



/* ==================================================================================
 * Class that extends LedControl to have string printing
 * ================================================================================== */

MaxLedMatrix::MaxLedMatrix()
{ 
    last_bat = -1;
    battery.init(DISPLAY_DATA_PIN, DISPLAY_CLK_PIN, DISPLAY_CS_PIN);
    sms.init(SMSDISP_DATA_PIN, SMSDISP_CLK_PIN, SMSDISP_CS_PIN);
    calls.init(CALLSDISP_DATA_PIN, CALLSDISP_CLK_PIN, CALLSDISP_CS_PIN);
    intensity = 8;
	setIntensity(intensity);
	lowBatDisplayToggle = false;
}


void MaxLedMatrix::init()
{
	shutdown(false);
	setIntensity(8);
	clear();
	showDisconnected();
}

void MaxLedMatrix::print(PhoneBasicInfo *pPhoneInfo)
{
	last_bat = pPhoneInfo->battery;
	if (last_bat < EMPTY_BAT_SIGN_THRESHOLD)
		showEmptyBattery(last_bat);
	else
		if (last_bat > 99)
			showFullBattery(last_bat);
		else
			showSmallFontNumber(&battery, last_bat);
	showNumber(&sms, pPhoneInfo->unreadSMS);
	showNumber(&calls, pPhoneInfo->missedCalls);
}

void MaxLedMatrix::showConnected()
{
	clear();
	// Show nice graphics of two arrows pointing one at the other. In any way this will be changed to
	// actual data rather quickly
	battery.setColumn(0, 3, 0x08);
	battery.setColumn(0, 4, 0x1E);
	battery.setColumn(0, 5, 0x08);

	calls.setColumn(0, 3, 0x20);
	calls.setColumn(0, 4, 0x78);
	calls.setColumn(0, 5, 0x20);
}

void MaxLedMatrix::showDisconnected()
{
	last_bat = -1;

	clear();
	// Show a nice graphics for disconnect, something like S of the Kiss band (nenver been a fan of them!)
	sms.setColumn(0, 0, 0xE7);
	sms.setColumn(0, 1, 0xF3);
	sms.setColumn(0, 2, 0xF9);
	sms.setColumn(0, 3, 0xF3);
	sms.setColumn(0, 4, 0xE7);
	sms.setColumn(0, 5, 0xCF);
	sms.setColumn(0, 6, 0xE7);
	sms.setColumn(0, 7, 0xF3);
}

void MaxLedMatrix::shutdown(boolean b)
{
  battery.shutdown(0, b);
  sms.shutdown(0, b);
  calls.shutdown(0, b);
}

void MaxLedMatrix::clear()
{
  battery.clearDisplay(0);
  sms.clearDisplay(0);
  calls.clearDisplay(0);
}


void MaxLedMatrix::showNumber(LedControl *lc, int num)
{
	// "Large Font" numbers (digits)
	static byte digit0[] = { B00111100, B01000010, B01000010, B01000010, B01000010, B01000010, B00111100 };
	static byte digit1[] = { B00011000, B00101000, B00001000, B00001000, B00001000, B00001000, B00011100 };
	static byte digit2[] = { B01111100, B00000010, B00000010, B00111110, B01000000, B01000000, B00111110 };
	static byte digit3[] = { B00111100, B01000010, B00000010, B00111100, B00000010, B01000010, B00111100 };
	static byte digit4[] = { B01000010, B01000010, B01000010, B00111110, B00000010, B00000010, B00000010 };
	static byte digit5[] = { B00111110, B01000000, B01000000, B00111100, B00000010, B00000010, B01111100 };
	static byte digit6[] = { B00111100, B01000010, B01000000, B01111100, B01000010, B01000010, B00111100 };
	static byte digit7[] = { B01111110, B00000010, B00000010, B00000010, B00000010, B00000010, B00000010 };
	static byte digit8[] = { B00111100, B01000010, B01000010, B00111100, B01000010, B01000010, B00111100 };
	static byte digit9[] = { B00111100, B01000010, B01000010, B00111110, B00000010, B00000010, B00111100 };
	static byte *digits[] = { digit0, digit1, digit2, digit3, digit4, digit5, digit6, digit7, digit8, digit9 };
	
	if (num > 9)
	{
		showSmallFontNumber(lc, num);
		return;
	}

	byte *ptrU = digits[num];
	byte val;
	

    lc->setColumn(0, 7, 0);
	for (int i=7; i >=0; i--)
	{
		val = ptrU[i];
		lc->setColumn(0, 6-i, val);
	}
}


void MaxLedMatrix::showSmallFontNumber(LedControl *lc, int num)
{
	// "small font" number (digits)
	static byte digit0[] = { B111, B101, B101, B101, B111 };
	static byte digit1[] = { B110, B010, B010, B010, B111 };
	static byte digit2[] = { B111, B001, B111, B100, B111 };
	static byte digit3[] = { B111, B001, B111, B001, B111 };
	static byte digit4[] = { B101, B101, B111, B001, B001 };
	static byte digit5[] = { B111, B100, B111, B001, B111 };
	static byte digit6[] = { B111, B100, B111, B101, B111 };
	static byte digit7[] = { B111, B001, B001, B001, B001 };
	static byte digit8[] = { B111, B101, B111, B101, B111 };
	static byte digit9[] = { B111, B101, B111, B001, B001 };
	static byte *digits[] = { digit0, digit1, digit2, digit3, digit4, digit5, digit6, digit7, digit8, digit9 };
	if (num < 100)
	{
		int dec = num / 10;
		int unit = num % 10;
		byte *ptrD = digits[dec];
		byte *ptrU = digits[unit];
		byte val;
		
		for (int i=7; i>5; i--)
			lc->setColumn(0, i, 0);
		for (int i=4; i >=0; i--)
		{
			val = ptrU[i] | ( ptrD[i] << 4 );	// combine bits from both digits
			lc->setColumn(0, 5-i, val);
		}
        lc->setColumn(0, 0, 0);
	}
    else
    {
		for(int i=0; i < 8; i++)				// We have 2 digits only, if 100 or more, just show completely full 8x8 on matrix
			lc->setColumn(0, i, 0xFF);
     }
}

void MaxLedMatrix::setIntensity(int inten)
{
    intensity = inten;
    battery.setIntensity(0, inten);
    sms.setIntensity(0, inten);
    calls.setIntensity(0, inten);

}

char *MaxLedMatrix::getDisplayInfo()
{
    static const char info[] = "{\"type\":\"8x8\", "		// JSON document with display information
                               "\"intensity\":%d, "			// Intensity is taken from current display intensity and set as integer
                               "\"height\":1, "				// We have 1 row of numbers
                               "\"width\":3 }\n";			// and 3 positions
   static char buffer[70];
   
   sprintf(buffer, info, (int)intensity);
   return buffer;
}


void MaxLedMatrix::tick()
{
	static unsigned long last = 0L;
	unsigned long now = millis();
	if (now-last > SWAP_DISP_EVERY)
	{
		last = now;
		lowBatDisplayToggle = !lowBatDisplayToggle;
		showEmptyBattery(-1);
	}
}


void MaxLedMatrix::showEmptyBattery(int bat)
{
	if (bat > 0)
		last_bat = bat;
	if (last_bat > EMPTY_BAT_SIGN_THRESHOLD)
		return;
	if (last_bat < 0)
		return;

	static byte emptyBDraw[] = { B00111100, B00100100, B00100100, B00100100, B00100100, B00100100, B00111100, B00011000 };
	if (lowBatDisplayToggle)
		for(int i=0; i<8; i++)
			battery.setColumn(0,i, emptyBDraw[i]);
	else
		showSmallFontNumber(&battery, last_bat);
}

void MaxLedMatrix::showFullBattery(int bat)
{
	static byte fullBDraw[] = { B00111100, B00111100, B00111100, B00111100, B00111100, B00111100, B00111100, B00011000 };
	for(int i=0; i<8; i++)
		battery.setColumn(0,i, fullBDraw[i]);
}
