Group Member: Yen-Ju Tseng(A59005785)  
## Section 1  
### Implementation  
1. System call **create**:  
  a. add handleCreate to handleSyscall  
  b. In handleCreate, use readVirtualMemoryString to get file name and check if the file name is valid.  
  c. get OpenFile from UserKernel.fileSystem.open(fName, isCreat). (isCreat == true)  
  d. update the file descriptor map.  
2. System call **open**:  
  a. add handleOpen to handleSyscall  
  b. In handleOpen, use readVirtualMemoryString to get file name and check if the file name is valid.  
  c. get OpenFile from UserKernel.fileSystem.open(fName, isCreat). (isCreat == false)  
  d. update the file descriptor map.   
3. System call **read**:  
  a. add handleRead to handleSyscall  
  b. In handleRead, check if fileDescriptor is valid, if file descriptor map contains fileDescriptor, and if count >= 0.  
  c. read page size data from file each time and write the data to virtual memory by nachos.machine.OpenFile.read(byte[] buf, int offset, int length) and writeVirtualMemory(bufferAddr, data, 0, numReadBytes) until count bytes;  
  d. return the total number of bytes were read.    
4. System call **write**:  
  a. add handleWrite to handleSyscall  
  b. In handleWrite, check if fileDescriptor is valid, if file descriptor map contains fileDescriptor, and if count >= 0.  
  c. read page size data from virtual memory each time and write the data to the file by nachos.machine.OpenFile.write(byte[] buf, int offset, int length) and readVirtualMemory(bufferAddr, data, 0, numReadBytes) until count bytes;  
  d. return the total number of bytes were written.  
5. System call **close**:  
  a. add handleClose to handleSyscall  
  b. In handleClose, check if fileDescriptor is valid, if file descriptor map contains fileDescriptor and not null.  
  c. close the file by nachos.machine.OpenFile.close().  
  d. update file descriptor map.
6. System call **unlink**:  
  a. add handleUnlink to handleSyscall  
  b. In handleUnlink, use readVirtualMemoryString to get file name and check if the file name is valid.  
  c. remove the file by ThreadedKernel.fileSystem.remove(fName). if it succeed, return 0. Else, return -1.  
### Testing  
**open**: opens an existing file and returns a file descriptor for it, and an error otherwise; opening the same file multiple times returns different file descriptors for each open; opening files does not interfere with stdin and stdout; each process can use 16 file descriptors.

**creat**: similar to open, but creates a new file if it does not exist; opens a file if it already exists; returns an error if the file cannot be created or opened.

**read**: works on both stdin and on files; checks arguments for correctness (valid file descriptor, file buffer pointer, count); the data read is actually the data stored in the file; the number of bytes read may be less than count.

**write**: works on both stdout and on files; checks arguments for correctness (valid file descriptor, file buffer pointer, count); the data written is actually stored in the file; the number of bytes written matches count.

**close**: file must be opened; uses the file system to close the file; frees up the file descriptor so that it can be used again; reading or writing to a file descriptor that was closed returns an error.

**unlink**: returns success only if the file system successfully removes the file, and an error otherwise; validates the file name argument.
  
## Section 2  
### Implementation  
1. In loadSections, Initialize the page table and return false if pages the process needed are more than free pages.  
Update page table: vpn, ppn, readonlt status.  
2. In unloadSections, realease pageTable and close all opened files.  
3. Modify readVirtualMemory and writeVirtualMemory. Both virtual and physical memory are split into pageSize chuncks.  
Use pageTable to store the status between vpn and ppn so that they work with multiple user processes.  
### Testing  
make sure the tests used in section1 still work after section 2 is done.  

## Section 3  
### Implementation  
In UserKernel, create PIDUserProcessMap (key: PID, value: UserProcess), int PIDCounter, and int existingProcessesNum.  
Use locks when any process want to update them.   

For each process, add int parent and HashSet childProcesses to store its parent and child processes. Moreover, add Integer exitStatus to store its exit status.  
1. System call **exec**:  
  a. add handleExec to handleSyscall  
  b. In handleExec, use readVirtualMemoryString to get program name and check if the file name is valid.  
  c. get argv by readVirtualMemory, Lib.bytesToInt, and readVirtualMemoryString and store them in a string array.  
  d. create a new child process.  
  e. update PIDUserProcessMap in UserKernel.  
  f. update parent in child process and childProcesses in parent process.  
  g. return child process PID.  
2. System call **join**:  
  a. add handleJoin to handleSyscall  
  b. In handleJoin, make sure only the parent process can call join on its child process.  
  c. Use KThread.join() to implement join.  
  d. Remove child process from the HashSet childProcesses in its parent process so that it will fail if it wants to join the same child process again.  
  e. If child process's exit status is null(exit abnormally), return 0.  
  f. Write child process's exit status to the \*status.  
  g. return 1 if child process's exit status is valid.  
3. System call **exit**:  
  a. Add handleExit to handleSyscall  
  b. In handleExit, release page table and close all opened files by unloadSections().  
  c. Close coff by coff.close().  
  d. If the process exit normally(abnormalExit == true), update exitStatus(exitStatus = status). Otherwise, leave exitStatus to be null.  
  e. Set every child process to null since Children of this process no longer have a parent.  
  f. Decrease the ExistingProcessesNum in UserKernel. If ExistingProcessesNum == 0, call Kernel.kernel.terminate() to stop the machine.  
  g. Call KThread.finish() at the end to finish the thread of the process. 
4. System call **halt**:  
  a. Make sure that only the root process(PID == 0) can successfully halt.  
### Testing  
**exec**: creates and runs a new process; each new process gets its own PID; can create multiple processes as long as there is sufficient physical memory; arguments are passed correctly from parent to child; validates parameters (file name, arguments).  

**exit**: open files are closed; physical pages are released so that they can be reused by a later process; the last process to exit calls Kernel.kernel.terminate.  

**join**: join works whether or not child has finished by the time the parent calls join; can only join to a direct child of the parent, otherwise return an error; join returns 0 if the child terminates due to an exception.  

**halt**: Only the root process can successfully halt.  
