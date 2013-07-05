/* ==================================================================================
 * Accessory for displaying information from cellular phone on a display
 * ==================================================================================
 * Program using a Bluetooth module to communicate with and Android phone and get
 * basic information to be displayed on dedicated screen of the accessory
 * Bluetooth module is RN-42 based BlueSMiRF Silver module:
 * Refrence http://www.sparkfun.com/datasheets/Wireless/Bluetooth/rn-bluetooth-um.pdf
 * Code supports 8 digits 7-Segment display or  3x 8x8 LED Matrix display (MAX7219 based)
 * The display is abstracted using virtual functions (Interface) so that it can easily
 * connect any other display or LED Matrix that will implement the simple interface
 * defined in IDisplay.h
 *
 * Display format (7 Segment)
 * ===========================
 *  BB_SS_CC
 *  Which means only 6 out of the 8 digits are actually used here.
 *  BB      Percentile of battery. If battery is 100% but not Full,
 *          will still show 99. If Full, will show F
 *  _       Represent space (nothing on)
 * SS       Number of unread SMS messages. I hope you are not going
 *          to get more than 99 messages before reading them
 *          If more than 99, will still show 99
 * CC       Number of missed calls. Same here hope you are not going
 *          to get more than 99 calls without answering them.
 *          If more than 99, will still show 99
 * If phone is not connected, display will show all decimal points on (7Segment)
 * or three - (LedMatrix display)
 *
 *
 * Display format (3 units of 8x8 LED Matrix)
 * ==========================================
 *	bb		Small font display of battery on first block. If value is 100, and image of full battery
 *			is displayed. If value is below 15 (see define) it will swap empty battery image display 
 *			and exact percetile
 *	S		Number of unread SMS calls. If value is below 10 (0..9) then big font number is displayed.
 *			If value is 10 or above, the middle block will switch to smaller font. If value is 100 or above,
 *			the entire 8x8 will light up.
 *	C		Displays number of missed calls, with Same behavior as for unread SMS in terms of font size.
 * If phone is connected and no data arrived, a two arrows pointing at each other will be shown. If phone
 * is not available, out of range for example, then the display will show graphics that indicates that.
 *
 *
 * Preparing the bluetooth module
 * ==============================
 * Before assembly, need to configue the BT module as following:
 *      Command Response                Comment
 *      $$$                             Enter command mode for BT module
 *              CMD
 *		SN,<friendly name>				Set the name of the accessory to something you want
 *				AOK						For example SO,MyPi will set the BT device name to MyPi. This is how your phone will see it
 *      SR,<phone BT MAC address>       Set the default device to connect to**
 *              AOK
 *      SM,3                            Set BT to be in auto-connecting mode
 *              AOK
 *      SO,Z                            We want to get CONNECT and DISCONNECT messages from BT module
 *              AOK
 *		SJ,0200							Increase page scan time for compatability with Android devices
 *				AOK
 *		SI,0200							Increase inquiry scan time for compatability with Android devices
 *				AOK
 *		SU,9600							Set baud rate to 9600. With SoftwareSerial, you can't use 115200, it is totally not relaible
 *				AOK
 *		R,1								Reboot the unit. After reboot all parameters will take affect, including the new baud rate
 *				Reboot!
 *  Close the terminal and open it again with 9600 baud rate
 *  Type $$$ then D<CR> to see it works well. Then ---<CR> to exit command mode
 *
 *  ** There are several ways to get the BT MAC address of the phone. If you do not
 *     have it displayed on the phone somewhere, do the following
 *      $$$
 *      I
 *     here if BT module is in range it should be displayed
 *
 *  To configue the module you can either use and FTDI or an Arduino Mega with
 *  code that gets from one port (Serial connected to the PC) and sends over to
 *  Serial1connected to the BT module.
 *
 * ==================================================================================
 * By: Zakie Mashiah
 *      You may copy or reuse or modify any portions of this code if you present
 *      visibule credit to the author.
 * ==================================================================================
 *  Revision History
 *  24-May-2012		Initial version
 *	05-Jul-2013		Make adoptions to new firmwares from Rover Networks. Added
 *					support for display with 3 units pf 8x8 LED matrix (7219 driven)
 * ==================================================================================
 */
#include <Arduino.h>
#include <SoftwareSerial.h>

#include "LedControl.h"
#include "phoneInfo.h"
#include "IDisplay.h"
#include "MaxSevenSegment.h"
#include "MaxLedMatrix.h"

/* ==================================================================================
 * connection pins
 * ==================================================================================
 */
#define BTSERIAL_TX 2
#define BTSERIAL_RX 3
SoftwareSerial btSerial(BTSERIAL_RX, BTSERIAL_TX);

HardwareSerial debugSerial = Serial;

/* ==================================================================================
 * Class to use as parser and container for data from phone
 * ================================================================================== */
class PhoneInfoSerial
{
private:
    unsigned int pos;
    PhoneBasicInfo	phoneInfo;
    #define			COMM_BUFFER_SIZE 100
    char			buffer[COMM_BUFFER_SIZE];
    bool			connectedState;
    char			*version;
    MaxLedMatrix	display; // You can easily change the display type to be MaxSevenSegment and it will compile
    //MaxSevenSegment display; // uncomment this line and comment the one above for 7Segment display
    void showOnDebugSerial();
    void showOnDisplay();
public:
    PhoneInfoSerial();
    void begin();
    void add(int val);
    void show();
    void process();
    void processBTStatus();
    void connectionState();
    void setIntensity();
	char *getVersion();
    void tick();
};

PhoneInfoSerial::PhoneInfoSerial()
{
    memset(&phoneInfo, 0, sizeof(phoneInfo));
    memset(buffer, 0, sizeof(buffer));
    pos = 0;
    connectedState = false;
    version = "{\"version\":\"2.0\"}";
}

// Adds a character from communication to buffer of read message
void PhoneInfoSerial::add(int val)
{
    if (pos >= COMM_BUFFER_SIZE)
        pos = 0; // Overwrite buffer if we got to the end of space in the buffer
    buffer[pos] = val;
    pos++;
    debugSerial.print((char)val);
}

void PhoneInfoSerial::showOnDebugSerial()
{
    // On serial deubgging screen
    debugSerial.print("Battery:      ");
    debugSerial.print(phoneInfo.battery);
    if (phoneInfo.batteryFull)
        debugSerial.println(" Full");
    else
        debugSerial.println();
    debugSerial.print("Unread SMS:   "); debugSerial.println(phoneInfo.unreadSMS);
    debugSerial.print("Missed calls: "); debugSerial.println(phoneInfo.missedCalls);
    debugSerial.println("--------------------");
}

void PhoneInfoSerial::showOnDisplay() { display.print(&phoneInfo); }

void PhoneInfoSerial::show()
{
    int num;
    int full = 0;

    debugSerial.println();   // Take new line on debugging screen

    // Parse data on buffer
    num = sscanf(buffer, "B%d/%dS%dC%d",
        &phoneInfo.battery, &full, &phoneInfo.unreadSMS, &phoneInfo.missedCalls);
    if (num == 4)
    {
        phoneInfo.batteryFull = (full != 0) ? true : false; // Complete the parsing by storing the value of boolean
        connectedState = true; // We are definitely connected

        showOnDebugSerial();
        showOnDisplay();
    }
    else
        debugSerial.println("Wrong buffer format");
}

void PhoneInfoSerial::process()
{
    debugSerial.println();
    switch (buffer[0])
    {
        /* Protocol convention:
         *       Capital letters represent phone sending data to accessory
         *       Lower case letter represent a phone quering the accessory
         * 
         *  B phone reporting on {B}attery, SMS, Missed Calls information
         *       B<percentile>/<full/not-full>S<unread SMS count>C<missed calls count>
         *  n phone asking for device {N}ame
         *  v phone asking for {V}erstion
         *  i phone asking for d{I}splay information and geomtry
         *  I phone is setting {I}ntensity of display
         *       I<intensity 0..F>
         * 
         *   Bluetooth serial port adapter status
         *  Z<CONNECTED/DISCONNECTED>    Connected or Disconnected status from BT module
		 *
		 * Example I: Phone sending 100% battery, Full, 2 SMS and 3 missed calls
		 *		B100/1S2C3
		 * Example II: Phone is sending 55% battery, not full, 1 SMS and 0 calls
		 *		B55/0S1C0
         */
        case 'B': show(); break;
        case 'n': btSerial.println("{\"name\":\"PhoneInfo Accessory\"}"); break;
        case 'v': btSerial.println(version); break;
        case 'i': btSerial.println(display.getDisplayInfo()); break;
        case 'I': setIntensity(); break;
    }
    memset(buffer, 0, sizeof(buffer));
    pos = 0;
}

void PhoneInfoSerial::processBTStatus()
{
    debugSerial.print("\r\n\tBTStatus:");
    debugSerial.print(buffer);
	debugSerial.print("->");
    if (buffer[0] == 'Z')
    {
        if (strcmp(buffer, "ZCONNECT") == 0)
        {
			debugSerial.println("con");
            connectedState = true;
            process();
            display.showConnected();
        }
        else
		{
            if (strcmp(buffer, "ZDISCONNECT") == 0)
            {
				debugSerial.println("dis");
                connectedState = false;
                process();
                display.showDisconnected();
            }
		}
        memset(buffer, 0, sizeof(buffer));
        pos = 0;
		return;
    }
	debugSerial.println("ign");
}



void PhoneInfoSerial::setIntensity()
{
    int inten, num;

    num = sscanf(buffer, "I%x", &inten);
    if (num == 1)
    {
        if ((inten >= 0) && (inten < 16))
            display.setIntensity(inten);
    }
    else
        debugSerial.print("Wrong intensity value");
}



void PhoneInfoSerial::begin()
{
    display.init();
}

void PhoneInfoSerial::tick() { display.tick(); }

char *PhoneInfoSerial::getVersion() { return version; }


class RoverNetworks42
{
private:
	char *enterCMD;
	char *exitCMD;
	char *version;
public:
	RoverNetworks42();
	void init(char *ver);
	void commandToBT(const char *cmd);
	void blueToothStatusDebug();
	void shortBTCommand(const char *cmd);
	void blueToothSettings();
};

/* ==================================================================================
 * Class to use as helper for sending commands to RN-42 bluetooth module 
 * ================================================================================== */
RoverNetworks42::RoverNetworks42()
{
	enterCMD = "$$$";
	exitCMD  = "---\r";
}

void RoverNetworks42::init(char *ver) { version = ver; }

void RoverNetworks42::commandToBT(const char *cmd)
{
	int c;
	unsigned long now, future;
	
	debugSerial.println(cmd);
	btSerial.print(cmd);
	now = millis();
	for (future = now + 2500; now < future ; now = millis() )
	{
		if (btSerial.available())
		{
			c = btSerial.read();
			debugSerial.print((char)c);
		}
	}
}


void RoverNetworks42::blueToothStatusDebug()
{
	static const char *queryBTStatus[] = { "D\r", "E\r", "GB\r", "GF\r", "GR\r", NULL };
	int i;

	for(i=0; queryBTStatus[i] != NULL; i++)
	{
		shortBTCommand(queryBTStatus[i]);
 	}
}

void RoverNetworks42::shortBTCommand(const char *cmd)
{
	commandToBT(enterCMD);
	commandToBT(cmd);
	commandToBT(exitCMD);
}

void RoverNetworks42::blueToothSettings()
{
	char ubtCommand[7]; // Buffer to hold command that is parameterized by the user
	int val;
	
	val = debugSerial.read();
	switch(val)
	{
	case 'A': shortBTCommand("SA,2\r"); break;
	case 'C': shortBTCommand("C\r"); break;
	case 'D': blueToothStatusDebug(); break;
	case 'H':
	case '?':
		debugSerial.println("Help on BT Setting:\r\n"
                       "===================\r\n"
					   "A\tSends SA,2\r\n"
					   "B\tShow BT MAC address\r\n"
					   "C\tConnect\r\n"
					   "D\tDisplay BT module setting\r\n"
					   "H ?\tHelp screen\r\n"
					   "I\tDisply SI value\r\n"
					   "J\tDisplay SJ value\r\n"
					   "P\tIncrease pairing reliability\r\n"
					   "R\tReboot BT module\r\n"
					   "V\tShow version\r\n"
					   "x\teXit command mode\r\n"
					   "<n>\tChange mode to SM,n (n is 0..6)\r\n"				   
					);
		break;
	case 'B':
	case 'I':
	case 'J':
		sprintf(ubtCommand, "G%c\r", val);
		shortBTCommand(ubtCommand);
		break;
	case 'P':
		commandToBT(enterCMD);
		commandToBT("SI,0200\r");
		commandToBT("SJ,0200\r");
		commandToBT(exitCMD);
		break;
	case 'R': commandToBT(enterCMD); commandToBT("R,1\r"); delay(500); break;
	case 'V': debugSerial.println(version); break;
	case 'x': btSerial.print(exitCMD); break;
	case '1':
	case '2':
	case '3':
	case '4':
	case '5':
	case '6':
		sprintf(ubtCommand, "SM,%c\r", val);
		shortBTCommand(ubtCommand);
		break;
	}
}

/* ================================================================================== */
/*                      Global Variables                                              */
/* ================================================================================== */
static int ledPin = 13;
static PhoneInfoSerial phoneInfo;
static RoverNetworks42 btHelper;


void setup()
{
    debugSerial.begin(115200);
    debugSerial.println("\nStarting");
	
	btHelper.init(phoneInfo.getVersion());
    btSerial.begin(9600);

	pinMode(ledPin, OUTPUT);
    phoneInfo.begin();
    
	debugSerial.println("Setup done");
}


void loop()
{
    int val = 0;

    if (btSerial.available() )
    {
        val = btSerial.read();
        if ( (val == '\r') || (val == '\n') )
            phoneInfo.process();
        else
        {
            if (val < 0x20)    // Ignore control characters
                return;
            phoneInfo.add(val);
            if (val == 'T')
                phoneInfo.processBTStatus();	// Both Zconnect and Zdisconnect ends with T
												// this is the only occurence expected for the character T in the stream
        }
    }
	if (debugSerial.available()) // If we got something on debug terminal, change BT settings
		btHelper.blueToothSettings();
	phoneInfo.tick();
}
