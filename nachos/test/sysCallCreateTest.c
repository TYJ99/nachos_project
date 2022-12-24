/*
 * sysCallCreat.c
 * Test basic the open system call.
 * What we want to test:
 * similar to open, but creates a new file if it does not exist;
 * opens a file if it already exists;
 * returns an error if the file cannot be created or opened.
 */

#include "stdio.h"
#include "stdlib.h"

int bigbuf1[1024];
int bigbuf2[1024];
int bigbufnum = 1024;

int do_creat(char *fname)
{
    int fd;

    printf("creating %s...\n", fname);
    fd = creat(fname);
    if (fd >= 0)
    {
        printf("...passed (fd = %d)\n", fd);
    }
    else
    {
        printf("...failed (%d)\n", fd);
        exit(-1001);
    }
    return fd;
}

int main()
{
    char name[] = "abcdefghijklmnopqrstuvwxyz";
    int i, numFile = 3;
    printf("create several files.\n");
    for (i = 0; i < numFile; ++i)
    {
        char c = name[i];
        char str1[2] = {c, '\0'};
        char str2[5] = "";
        strcpy(str2, str1);
        int fd = do_creat(str2);
    }

    printf("create several files with same name.\n");

    for (i = numFile; i < numFile + 4; ++i)
    {
        char c = name[numFile + 4];
        char str1[2] = {c, '\0'};
        char str2[5] = "";
        strcpy(str2, str1);
        int fd = do_creat(str2);
    }

    printf("create a file with invalid name.\n");
    do_creat("");
    return 0;
}
