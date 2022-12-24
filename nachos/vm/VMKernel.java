package nachos.vm;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		swapFile = ThreadedKernel.fileSystem.open("swapFile", true);
		freeSwapPages = new LinkedList<>();
		invertedPageTable = new HashMap<>();
		swapFileCount = 0;
		victimPage = 0;
		pinCount = 0;
		initLock();
	}

	private static void initLock() {
		IPTLock = new Lock();
		freeSwapPageLock = new Lock();
		swapFileLock = new Lock();
		victimLock = new Lock();
		pinLock = new Lock();
		pFExceptionLock = new Lock();
		fullPinCV = new Condition(pFExceptionLock);
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	public static int getFreeSwapPage() {
		freeSwapPageLock.acquire();

		if (!freeSwapPages.isEmpty()) {
			int freeSwapFile = freeSwapPages.poll();
			freeSwapPageLock.release();
			return freeSwapFile;
		}
		int freeSwapFile = swapFileCount;
		++swapFileCount;

		freeSwapPageLock.release();
		Lib.debug(dbgProcess, "free swap file is " + freeSwapFile);
		return freeSwapFile;
	}

	public static void addFreeSwapPage(int page) {
		freeSwapPageLock.acquire();

		freeSwapPages.offer(page);

		freeSwapPageLock.release();

		return;
	}

	static class PageInfo {
		protected VMProcess vmProcess;
		protected int vpn;
		public boolean pinned;

		public PageInfo() {
			vmProcess = null;
			vpn = -1;
			pinned = false;
		}

		public PageInfo(VMProcess vmProcess, int vpn) {
			this.vmProcess = vmProcess;
			this.vpn = vpn;
			this.pinned = false;
		}

		public PageInfo(VMProcess vmProcess, int vpn, boolean pinned) {
			this.vmProcess = vmProcess;
			this.vpn = vpn;
			this.pinned = pinned;
		}

		public void setVMProcess(VMProcess vmProcess) {
			this.vmProcess = vmProcess;
		}

		public VMProcess getVMProcess() {
			return vmProcess;
		}

		public void setVpn(int vpn) {
			this.vpn = vpn;
		}

		public int getVpn() {
			return vpn;
		}

	}

	public static PageInfo getPageInfo(int ppn) {
		IPTLock.acquire();

		PageInfo pageInfo = invertedPageTable.get(ppn);

		IPTLock.release();
		return pageInfo;
	}

	public static void removePPageFromIPT(int ppn) {
		IPTLock.acquire();

		invertedPageTable.put(ppn, null);

		IPTLock.release();
	}

	public static void addPPageToIPT(int ppn, VMProcess vmProcess, int vpn) {
		IPTLock.acquire();

		PageInfo pageInfo = new PageInfo(vmProcess, vpn);
		invertedPageTable.put(ppn, pageInfo);

		IPTLock.release();
	}

	public static void addPPageToIPT(int ppn, VMProcess vmProcess, int vpn, boolean pinned) {
		IPTLock.acquire();

		PageInfo pageInfo = new PageInfo(vmProcess, vpn, pinned);
		invertedPageTable.put(ppn, pageInfo);

		IPTLock.release();
	}

	public static int getIPTSize() {
		IPTLock.acquire();

		int IPTSize = invertedPageTable.size();

		IPTLock.release();
		return IPTSize;
	}

	public static void readSwapFile(int spn, byte[] data) {
		swapFileLock.acquire();

		swapFile.read(spn * Processor.pageSize, data, 0, data.length);

		swapFileLock.release();
	}

	public static void writeSwapFile(int spn, byte[] data) {
		swapFileLock.acquire();

		swapFile.write(spn * Processor.pageSize, data, 0, data.length);

		swapFileLock.release();
	}

	public static OpenFile getSwapFile() {
		return swapFile;
	}

	public static int getVictimPage() {
		victimLock.acquire();

		int victim = victimPage;

		victimLock.release();
		return victim;
	}

	public static void updateVictimPage() {
		victimLock.acquire();

		victimPage = (++victimPage) % Machine.processor().getNumPhysPages();

		victimLock.release();
	}

	public static void pinPage(int ppn) {
		Lib.assertTrue(pinCount < Machine.processor().getNumPhysPages());
		// pinLock.acquire();

		getPageInfo(ppn).pinned = true;
		++pinCount;

		// pinLock.release();
	}

	public static void unpinPage(int ppn) {
		// VMKernel.pageReplacementLock.acquire();
		pinLock.acquire();

		int oldPinCount = pinCount;
		getPageInfo(ppn).pinned = false;
		--pinCount;
		if (Machine.processor().getNumPhysPages() == oldPinCount) {
			VMKernel.fullPinCV.wakeAll();
		}

		pinLock.release();
		// VMKernel.pageReplacementLock.release();
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	private static final char dbgProcess = 'a';

	private static OpenFile swapFile;

	private static int swapFileCount;

	private static LinkedList<Integer> freeSwapPages;

	private static Map<Integer, PageInfo> invertedPageTable;

	private static int victimPage;

	public static int pinCount;

	private static Lock IPTLock;

	private static Lock freeSwapPageLock;

	private static Lock swapFileLock;

	private static Lock victimLock;

	public static Lock pinLock;

	public static Lock pFExceptionLock;

	public static Condition fullPinCV;

}
