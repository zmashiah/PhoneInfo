#PhoneInfo Application#

This app runs on Android phones and sends missed calls, unread SMS and battery status via bluetooth to an Arduino device that shows the data on a screen.

This code includes an Android application and an Arduino based code. 

Details of assembly and all can be found here:
http://www.instructables.com/id/Missed-calls-and-SMS-Notifier-Accessory/

##Folders##
Arduino		The Arduino based code
all others	The Eclipse project

##Hardware Components##
You will need a 5V based Arduino (any type), RN-42 based Bluetooth module (SMD or with breakout board) and some wires to
connect them all. If you are picking the bare module of RN-42, two LEDs and resistors will be required. If your Arduino does
not provide a 3.3V supply, a 3.3V regulator (78L33 for example). The display can be one of the following:
1. 3x MAX 7219 based 8x8 LED Matrix
2. MAX 7219 based 8 digits 7 Segment display
(only one of the above is required)

###Complete part list (3x LED Matrix display and simple breakout board for RN-42)###
1x	Arduino (Uno, Nano, Pro-Mini)
1x	RN-42 Module
2x	120 Ohm Resistor
1x	Blue 3mm LED
1x	White 3mm LED
1x	78L33 3,3Volt Regulator
3x	8x8 LED Matrix display with MAX7219


An FTDI will be useful during the assembly.

###Connecting HW###
Connect the RN-42 Tx to Arduino pin 3
Connect the RN-42 Rx to Arduino pin 2
Connect the RN-42 Gnd to Arduino Gnd
Connect the 78L33 input to Arduino 5V supply out
Connect the 78L33 Gnd to Arduino Gnd
Connect the 78L33 output to RN-42 Vcc
Connect resistor to RN-42 pin 19 and second lead to blue LED positive
Connect resistor to RN=42 pin 21 and second lead to white LED positive
Connect the blue LED negative lead to Gnd
Connect the white LED negative lead to Gnd
Connect all display matrix Vcc to Arduino 5V
Connect all display matrix Gnd to Arduino Gnd
Connect first display CLK pin to Arduino pin 6
Connect first display CS pin to Arduino pin 5
Connect first display Din pin to Arduino pin 4
Connect 2nd display CLK, CS, Din to Arduino pins 9, 8, 7 respectively
Connect 3rd display CLK, CS, Din to Arduino pins 12, 11, 10 respectively

If you choose the 7 Segment based display, it still works fine, and the connection details can be found
in the file MaxSevenSegment.cpp  which is essentially like connecting the first display from above.

##Loading SW##
Arduino IDE 1.05 can be used easily to load program to Arduino.
The APK file for Android is all you need too for the phone.


##Setting up Bluetooth##
 Before assembly, need to configue the BT module as following:
 Command Response                Comment
 $$$                             Enter command mode for BT module
	CMD
 SN,<friendly name>		Set the name of the accessory to something you want
 	AOK			For example SO,MyPi will set the BT device name to MyPi. This is how your phone will see it
 SR,<phone BT MAC address>      Set the default device to connect to**
        AOK
 SM,3                           Set BT to be in auto-connecting mode
        AOK
 SO,Z                           We want to get CONNECT and DISCONNECT messages from BT module
        AOK
 SJ,0200			Increase page scan time for compatability with Android devices
	AOK
 SI,0200			Increase inquiry scan time for compatability with Android devices
	AOK
 SU,9600			Set baud rate to 9600. With SoftwareSerial, you can't use 115200, it is totally not relaible
	AOK
 R,1				Reboot the unit. After reboot all parameters will take affect, including the new baud rate
	Reboot!

  Close the terminal and open it again with 9600 baud rate
  Type $$$ then D<CR> to see it works well. Then ---<CR> to exit command mode

  ** There are several ways to get the BT MAC address of the phone. If you do not
     have it displayed on the phone somewhere, do the following
      $$$
      I
     here if BT module is in range it should be displayed

  To configue the module you can either use and FTDI or an Arduino Mega with
  code that gets from one port (Serial connected to the PC) and sends over to
  Serial1connected to the BT module.
