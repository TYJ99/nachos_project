/*
 * write1.c
 *
 * Write a string to stdout, one byte at a time.  Does not require any
 * of the other system calls to be implemented.
 *
 * Geoff Voelker
 * 11/9/15
 */

#include "syscall.h"

int main(int argc, char *argv[])
{
    char *str = "\nThe 2022-23 Memphis Grizzlies City Edition uniform celebrates the legacy and future of Memphis Hip-Hop, the neighborhoods and people from which the Big Memphis sound flows from, and the fabric of Memphis today. Highlighted by chrome inspired detailing, diamond textures and Memphis unapologetic and unique style, the uniform serves as a tribute to the artists and albums that define Memphis hip-hop and its raw sound.shaped wordmark draws inspiration from local hip-hop album art and along with the number set incorporates a diamond texture outlined by a chrome inspired detailing while framing the word Grizzlies in the iconic Beale Street Blue.\n The right uniform panel and its asymmetry aligns with Memphis unique style and the current and throwback uniform systems dating back to the inaugural seasons in Memphis (2001) and Vancouver (1995). The MG pattern and underlying diamond texture form the jersey wordmark & number set pulled through this panel, the neck and left short leg trim, embodying the big style and swag of Memphis and the team.\nAs with the asymmetrical uniform silhouette, the oversized bear icon on the short connects to the current and historical uniform systems. The chrome-inspired detailing highlighted on the bear head and trim throughout the uniform represents the hustle of Memphis artists, whether selling mixtape cassettes out of trunks or delivering Memphis sound to the world today.\n-The stylized M on the buckle, represents Memphis iconic M-Bridge highlighted with the chrome detailing and diamond texture throughout the uniform.\n-Just above the jersey tag, a Grizz grill along with For The M is emblemized to represent the fierce pride Memphians rep for their music, their teams, and their city.\n\n";
    while (*str)
    {
        int r = write(1, str, 1);
        if (r != 1)
        {
            printf("failed to write character (r = %d)\n", r);
            exit(-1);
        }
        str++;
    }

    return 0;
}
