/* ============================================================================= */
/*	The information about phone we will be using								 */
/* ============================================================================= */
/* By: Zakie Mashiah zmashiah@gmail.com											 */
/* ============================================================================= */

#ifndef __PHONEINFO_H
#define __PHONEINFO_H

typedef struct
{
    int battery;
    bool batteryFull;
    int unreadSMS;
    int missedCalls;
} PhoneBasicInfo;


#endif
