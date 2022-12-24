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
    char *str = "\nroses\n\n";

    while (*str)
    {
        int r = write(1, str, 20);
        if (r != 20)
        {
            printf("failed to write character (r = %d)\n", r);
            exit(-1);
        }
        str += 20;
    }

    return 0;
}
