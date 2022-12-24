package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		// int numPhysPages = Machine.processor().getNumPhysPages();
		// pageTable = new TranslationEntry[numPhysPages];
		// for (int i = 0; i < numPhysPages; i++)
		// pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		UserKernel.initProcessLock.acquire();

		PID = UserKernel.getPID();
		childProcesses = new HashSet<>();
		exitStatus = null;
		abnormalExit = false;
		parent = null;
		initFD();
		UserKernel.increaseExistingProcessesNum();

		UserKernel.initProcessLock.release();
	}

	private void initFD() {
		fdMap = new HashMap<Integer, OpenFile>() {
			{
				put(0, UserKernel.console.openForReading());
				put(1, UserKernel.console.openForWriting());
			}
		};
		nextFD = 2;
		freeFDs = new LinkedList<Integer>();
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		String name = Machine.getProcessClassName();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader. Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals("nachos.userprog.UserProcess")) {
			return new UserProcess();
		} else if (name.equals("nachos.vm.VMProcess")) {
			return new VMProcess();
		} else {
			return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr     the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 *                  including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data  the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr  the first byte of virtual memory to read.
	 * @param data   the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 *               array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		return virtualMemoryHelper(vaddr, data, offset, length, false);
	}

	private int virtualMemoryHelper(int vaddr, byte[] data, int offset, int length, boolean isWrite) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0 || vaddr >= numPages * pageSize || length == 0) {
			return 0;
		}
		int endAddr = vaddr + length;
		int startVPage = Processor.pageFromAddress(vaddr),
				endVPage = Processor.pageFromAddress(endAddr - 1),
				totalAmount = 0;
		for (int vpn = startVPage; vpn <= endVPage; vpn++) {
			if (vpn >= numPages || (isWrite && pageTable[vpn].readOnly) || !pageTable[vpn].valid) {
				return totalAmount;
			}
			int pPageOffset = Processor.offsetFromAddress(vaddr),
					pos = pageTable[vpn].ppn * pageSize + pPageOffset,
					amount = Math.min(endAddr, (vpn + 1) * pageSize) - vaddr;

			arrayCopy(data, offset, memory, pos, amount, isWrite);

			vaddr = (vpn + 1) * pageSize;
			totalAmount += amount;
			offset += amount;
		}
		// int amount = Math.min(length, memory.length - vaddr);
		// System.arraycopy(memory, vaddr, data, offset, amount);
		return totalAmount;
	}

	private void arrayCopy(byte[] data, int offset, byte[] memory, int pos, int amount, boolean isWrite) {
		if (isWrite) {
			System.arraycopy(data, offset, memory, pos, amount);
		} else {
			System.arraycopy(memory, pos, data, offset, amount);
		}
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data  the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr  the first byte of virtual memory to write.
	 * @param data   the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 *               memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		return virtualMemoryHelper(vaddr, data, offset, length, true);
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	private boolean initPageTable() {
		pageTable = new TranslationEntry[numPages];
		// System.out.println("numPages: " + numPages);
		for (int i = 0; i < numPages; i++) {
			int pPageNum = UserKernel.getFreePPage();
			if (pPageNum == -1) {
				return false;
			}
			pageTable[i] = new TranslationEntry(i, pPageNum, true, false, false, false);
		}
		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// initialize pageTable. If it failed, return false.
		if (!initPageTable()) {
			return false;
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				int ppn = pageTable[vpn].ppn;
				pageTable[vpn].readOnly = section.isReadOnly();
				section.loadPage(i, ppn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		Lib.debug(dbgProcess, "unloadSections in UserProcessor");
		int pageTableSize = pageTable.length;
		for (int i = 0; i < pageTableSize; i++) {
			UserKernel.addFreePPage(pageTable[i].ppn);
			// if (pageTable[i] != null) {
			// UserKernel.addFreePPage(pageTable[i].ppn);
			// pageTable[i] = null;
			// }
		}
		// close all opened files
		for (Iterator<Map.Entry<Integer, OpenFile>> it = fdMap.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, OpenFile> entry = it.next();
			handleClose(entry.getKey());
		}

	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		if (PID != 0) {
			return -1;
		}
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
		// Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.
		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		unloadSections();
		coff.close();

		// UserKernel.PIDLock.acquire();

		if (!abnormalExit) {
			exitStatus = status;
		}

		// // Wake up the parent if it is sleeping. statusBlocked = 3
		// if (parent != null && parent.thread.getStatus() == 3) {
		// parent.thread.ready();
		// }

		// Children of this process no longer have a parent.
		for (int childPID : childProcesses) {
			UserKernel.getUserProcess(childPID).parent = null;
		}

		UserKernel.existingProcessesNumLock.acquire();

		UserKernel.decreaseExistingProcessesNum();

		// Last exiting process should cause machine to stop.
		if (UserKernel.getExistingProcessesNum() == 0) {
			UserKernel.existingProcessesNumLock.release();
			Kernel.kernel.terminate();
		}

		UserKernel.existingProcessesNumLock.release();

		// UserKernel.PIDLock.release();
		KThread.finish(); // Finish the thread of the process

		return 0;
	}

	/**
	 * Handle the creat() system call.
	 */
	private int handleCreate(int vFileAddr) {
		return createOpenHelper(vFileAddr, "handleCreate", true);
	}

	private int handleOpen(int vFileAddr) {
		return createOpenHelper(vFileAddr, "handleOpen", false);
	}

	// For read(), the address(bufferAddr) is pointing to where we put the data,
	// after we read the data from the file.
	private int handleRead(int fileDescriptor, int bufferAddr, int count) {
		if (fileDescriptor < 0 || !fdMap.containsKey(fileDescriptor) || count < 0) {
			return -1;
		}
		int totalReadBytes = 0;
		while (count > 0) {
			byte[] data = new byte[pageSize];
			int currCount = Math.min(count, pageSize);
			int numReadBytes = fdMap.get(fileDescriptor).read(data, 0, currCount);
			if (numReadBytes == 0) {
				break;
			}

			if (numReadBytes == -1) {
				return -1;
			}

			int actualWriteToVM = writeVirtualMemory(bufferAddr, data, 0, numReadBytes);

			// On error, -1 is returned, and the new file position is undefined. This can
			// happen if part of the buffer is read-only or invalid.
			if (actualWriteToVM < numReadBytes) {
				return -1;
			}
			totalReadBytes += actualWriteToVM;
			bufferAddr += actualWriteToVM;
			count -= pageSize;
		}
		return totalReadBytes;
	}

	// For write(), the address(bufferAddr) is pointing to where we get the data,
	// before we write the data to the file.
	private int handleWrite(int fileDescriptor, int bufferAddr, int count) {
		if (fileDescriptor < 0 || !fdMap.containsKey(fileDescriptor) || count < 0) {
			return -1;
		}
		int totalWrittenBytes = 0;
		while (count > 0) {
			byte[] data = new byte[pageSize];
			int currCount = Math.min(count, pageSize);
			int actualReadFromVM = readVirtualMemory(bufferAddr, data, 0, currCount);

			// On error, -1 is returned, and the new file position is undefined. This can
			// happen if part of the buffer is invalid.
			if (actualReadFromVM < currCount) {
				return -1;
			}
			int numWrittenBytes = fdMap.get(fileDescriptor).write(data, 0, currCount);
			if (numWrittenBytes == -1) {
				return -1;
			}
			if (numWrittenBytes < currCount) {
				return -1;
			}
			totalWrittenBytes += numWrittenBytes;
			bufferAddr += actualReadFromVM;
			count -= pageSize;
		}
		return totalWrittenBytes;
	}

	protected int handleClose(int fileDescriptor) {
		if (fileDescriptor < 0 || !fdMap.containsKey(fileDescriptor) || fdMap.get(fileDescriptor) == null) {
			return -1;
		}
		fdMap.get(fileDescriptor).close();
		fdMap.put(fileDescriptor, null);
		freeFDs.offer(fileDescriptor);
		return 0;
	}

	private int handleUnlink(int vFileAddr) {
		String fName = readVirtualMemoryString(vFileAddr, maxStringLen);
		if (fName.isEmpty() || fName == null) {
			Lib.debug(dbgProcess, "UserProcess.handleUnlink: No such file or invalid file.");
			System.out.println("UserProcess.handleUnlink: No such file or invalid file.");
			return -1;
		}
		if (!ThreadedKernel.fileSystem.remove(fName)) {
			return -1;
		}
		return 0;
	}

	private int handleExec(int vProgramAddr, int argc, int vArgvAddr) {
		if (argc < 0 || (argc > 0 && vArgvAddr == 0x0) || vProgramAddr == 0x0) {
			return -1;
		}

		String programName = readVirtualMemoryString(vProgramAddr, maxStringLen);
		if (programName.isEmpty() || programName == null || !programName.contains(".coff")) {
			Lib.debug(dbgProcess, "UserProcess.handleExec: No such file or invalid file.");
			System.out.println("UserProcess.handleExec: No such file or invalid file.");
			return -1;
		}

		byte[] argv = new byte[argc * pointerSize];
		String[] args = new String[argc];
		int actualReadFromVM = readVirtualMemory(vArgvAddr, argv, 0, argv.length);
		if (actualReadFromVM < argv.length) {
			return -1;
		}
		for (int i = 0; i < argc; ++i) {
			int start = i * pointerSize, end = start + pointerSize;
			int stringVAddr = Lib.bytesToInt(Arrays.copyOfRange(argv, start, end), 0);
			if (stringVAddr == 0x0) {
				return -1;
			}
			String arg = readVirtualMemoryString(stringVAddr, maxStringLen);
			args[i] = arg;
		}

		UserProcess childProcess = UserProcess.newUserProcess();

		if (!childProcess.execute(programName, args)) {
			Lib.debug(dbgProcess, "Could not find '" + programName + "', aborting.");
			System.out.println("Could not find '" + programName + "', aborting.");
			return -1;
		}
		UserKernel.addPIDUserProcessMap(childProcess.PID, childProcess);
		childProcess.parent = UserKernel.getUserProcess(PID);
		System.out.println("parent is " + UserKernel.getUserProcess(PID).thread.getName());
		childProcesses.add(childProcess.PID);

		return childProcess.PID;
	}

	private int handleJoin(int childProcessID, int childExitStatusVAddr) {
		if (!childProcesses.contains(childProcessID)) {
			Lib.debug(dbgProcess, "Child process " + childProcessID + " doesn't belong to current process " + PID);
			return -1;
		}
		UserProcess childProcess = UserKernel.getUserProcess(childProcessID);
		if (childProcess == null) {
			return 0;
		}
		childProcess.thread.join();
		childProcesses.remove(childProcessID);

		if (childProcess.exitStatus == null) {
			return 0;
		}
		if (childExitStatusVAddr != 0x0) {
			byte[] childExitStatus = Lib.bytesFromInt((int) childProcess.exitStatus);
			int actualWriteToVM = writeVirtualMemory(childExitStatusVAddr, childExitStatus, 0, childExitStatus.length);
			if (actualWriteToVM < childExitStatus.length) {
				return 0;
			}
		}
		return 1;
	}

	private int createOpenHelper(int vFileAddr, String sysCall, boolean isCreat) {
		String fName = readVirtualMemoryString(vFileAddr, maxStringLen);
		if (fName.isEmpty() || fName == null) {
			Lib.debug(dbgProcess, "UserProcess." + sysCall + ": No such file or invalid file.");
			System.out.println("UserProcess." + sysCall + ": No such file or invalid file.");
			return -1;
		}
		Lib.debug(dbgProcess, "UserProcess." + sysCall + "(" + fName + ")");
		System.out.println("UserProcess." + sysCall + "(" + fName + ")");

		OpenFile openFile = UserKernel.fileSystem.open(fName, isCreat);
		int fd = updateOpenFD(openFile);
		return fd;
	}

	private int updateOpenFD(OpenFile openFile) {
		boolean isFDMapFull = (nextFD >= maxFileTableSize && freeFDs.isEmpty());
		if (openFile == null || isFDMapFull) {
			return -1;
		}
		int freeFD = getFreeFD();
		fdMap.put(freeFD, openFile);
		return freeFD;
	}

	private int getFreeFD() {
		int freeFD = nextFD;
		if (!freeFDs.isEmpty()) {
			freeFD = freeFDs.poll();
		} else {
			++nextFD;
		}
		return freeFD;
	}

	public int getPID() {
		return PID;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0      the first syscall argument.
	 * @param a1      the second syscall argument.
	 * @param a2      the third syscall argument.
	 * @param a3      the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
			case syscallHalt:
				return handleHalt();
			case syscallExit:
				return handleExit(a0);
			case syscallCreate:
				return handleCreate(a0);
			case syscallOpen:
				return handleOpen(a0);
			case syscallRead:
				return handleRead(a0, a1, a2);
			case syscallWrite:
				return handleWrite(a0, a1, a2);
			case syscallClose:
				return handleClose(a0);
			case syscallUnlink:
				return handleUnlink(a0);
			case syscallExec:
				return handleExec(a0, a1, a2);
			case syscallJoin:
				return handleJoin(a0, a1);

			default:
				Lib.debug(dbgProcess, "Unknown syscall " + syscall);
				Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
			case Processor.exceptionSyscall:
				int result = handleSyscall(processor.readRegister(Processor.regV0),
						processor.readRegister(Processor.regA0),
						processor.readRegister(Processor.regA1),
						processor.readRegister(Processor.regA2),
						processor.readRegister(Processor.regA3));
				processor.writeRegister(Processor.regV0, result);
				processor.advancePC();
				break;

			default:
				Lib.debug(dbgProcess, "handle default exception in UserProcessor");
				Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);

				abnormalExit = true;
				handleExit(-1);

				Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
	protected UThread thread;

	// file descriptor table
	protected Map<Integer, OpenFile> fdMap;

	// next file descriptor
	private int nextFD;

	// child processes
	private Set<Integer> childProcesses;

	// exitStatus
	private Integer exitStatus;

	// free file descriptors
	private Queue<Integer> freeFDs;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final int maxStringLen = 256; // 256 bytes

	private static final int maxFileTableSize = 16;

	private static final int pointerSize = 4; // 4 bytes

	private static final int intSize = 4; // 4 bytes

	private int PID;

	private boolean abnormalExit;

	private UserProcess parent;

}
