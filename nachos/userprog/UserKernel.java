package nachos.userprog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
	/**
	 * Allocate a new user kernel.
	 */
	public UserKernel() {
		super();
	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		initLock();
		PIDCounter = 0;
		existingProcessesNum = 0;
		PIDUserProcessMap = new HashMap<>();
		initFreePPages();

		console = new SynchConsole(Machine.console());

		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() {
				exceptionHandler();
			}
		});
	}

	private static void initLock() {
		freePPagelock = new Lock();
		PIDLock = new Lock();
		PIDMapLock = new Lock();
		existingProcessesNumLock = new Lock();
		initProcessLock = new Lock();
	}

	public static void addPIDUserProcessMap(int PID, UserProcess userProcess) {
		PIDMapLock.acquire();

		PIDUserProcessMap.put(PID, userProcess);

		PIDMapLock.release();
	}

	public static void removePIDUserProcessMap(int PID) {
		PIDMapLock.acquire();
		System.out.println("remove....");
		PIDUserProcessMap.remove(PID);

		PIDMapLock.release();
	}

	public static UserProcess getUserProcess(int PID) {
		PIDMapLock.acquire();

		UserProcess userProcess = PIDUserProcessMap.get(PID);

		PIDMapLock.release();
		return userProcess;
	}

	private void initFreePPages() {
		freePPages = new LinkedList<>() {
			{
				int numPhysPages = Machine.processor().getNumPhysPages();
				for (int i = 0; i < numPhysPages; i++) {
					offer(i);
				}
			}
		};
	}

	public static int getFreePPage() {
		int page = -1;
		freePPagelock.acquire();

		if (!freePPages.isEmpty()) {
			page = freePPages.poll();
		}

		freePPagelock.release();

		return page;
	}

	public static void addFreePPage(int page) {
		freePPagelock.acquire();

		freePPages.offer(page);

		freePPagelock.release();

		return;
	}

	public static int getPID() {
		PIDLock.acquire();

		int PID = PIDCounter;
		++PIDCounter;

		PIDLock.release();
		return PID;
	}

	public static int getExistingProcessesNum() {
		// existingProcessesNumLock.acquire();

		int num = existingProcessesNum;

		// existingProcessesNumLock.release();
		return num;
	}

	public static void increaseExistingProcessesNum() {
		existingProcessesNumLock.acquire();

		++existingProcessesNum;

		existingProcessesNumLock.release();
	}

	public static void decreaseExistingProcessesNum() {
		// existingProcessesNumLock.acquire();

		--existingProcessesNum;

		// existingProcessesNumLock.release();
	}

	/**
	 * Test the console device.
	 */
	public void selfTest() {
		super.selfTest();

		System.out.println("Testing the console device. Typed characters");
		System.out.println("will be echoed until q is typed.");

		char c;

		do {
			c = (char) console.readByte(true);
			console.writeByte(c);
		} while (c != 'q');

		System.out.println("");
	}

	/**
	 * Returns the current process.
	 * 
	 * @return the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;
		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever a
	 * user instruction causes a processor exception.
	 * 
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of
	 * the exception (see the <tt>exceptionZZZ</tt> constants in the
	 * <tt>Processor</tt> class). If the exception involves a bad virtual
	 * address (e.g. page fault, TLB miss, read-only, bus error, or address
	 * error), the processor's BadVAddr register identifies the virtual address
	 * that caused the exception.
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
		process.handleException(cause);
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 * 
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		UserProcess process = UserProcess.newUserProcess();

		UserKernel.addPIDUserProcessMap(process.getPID(), process);

		String shellProgram = Machine.getShellProgramName();
		if (!process.execute(shellProgram, new String[] {})) {
			System.out.println("Could not find executable '" +
					shellProgram + "', trying '" +
					shellProgram + ".coff' instead.");
			shellProgram += ".coff";
			if (!process.execute(shellProgram, new String[] {})) {
				System.out.println("Also could not find '" +
						shellProgram + "', aborting.");
				Lib.assertTrue(false);
			}

		}

		KThread.currentThread().finish();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	/** Globally accessible reference to the synchronized console. */
	public static SynchConsole console;

	// dummy variables to make javac smarter
	private static Coff dummy1 = null;

	// a linkedlist to store free physical pages
	private static LinkedList<Integer> freePPages;

	private static Lock freePPagelock;

	public static Lock PIDLock;

	private static Lock PIDMapLock;

	public static Lock existingProcessesNumLock;

	public static Lock initProcessLock;

	private static int PIDCounter;

	private static int existingProcessesNum;

	private static Map<Integer, UserProcess> PIDUserProcessMap;
}
