package nachos.vm;

import java.util.Iterator;
import java.util.Map;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {

	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		// return super.loadSections();

		// initialize pageTable. If it failed, return false.
		if (!init()) {
			return false;
		}
		return true;
	}

	private boolean init() {
		pageTable = new TranslationEntry[numPages];
		spnArr = new int[numPages];
		for (int i = 0; i < numPages; i++) {
			int pPageNum = -1;
			pageTable[i] = new TranslationEntry(i, pPageNum, false, false, false, false);
			spnArr[i] = -1;
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		VMKernel.pFExceptionLock.acquire();

		Lib.debug(dbgProcess, "unloadSections in VMProcessor");
		int pageTableSize = pageTable.length;
		for (int i = 0; i < pageTableSize; i++) {
			if (pageTable[i].valid && -1 != pageTable[i].ppn) {

				UserKernel.addFreePPage(pageTable[i].ppn);
				VMKernel.removePPageFromIPT(pageTable[i].ppn);
			}

			if (-1 != spnArr[i]) {
				VMKernel.addFreeSwapPage(spnArr[i]);
			}

		}

		// // close all opened files
		// VMKernel.getSwapFile().close();
		// for (Iterator<Map.Entry<Integer, OpenFile>> it = fdMap.entrySet().iterator();
		// it.hasNext();) {
		// Map.Entry<Integer, OpenFile> entry = it.next();
		// handleClose(entry.getKey());
		// }

		Lib.debug(dbgProcess, "finish unloadSections in VMProcessor");
		// super.unloadSections();

		VMKernel.pFExceptionLock.release();
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
			case Processor.exceptionPageFault:
				int faultingVAddr = processor.readRegister(Processor.regBadVAddr);
				handlePageFaultException(faultingVAddr, false);
				break;

			default:
				Lib.debug(dbgProcess, "handle default exception in VMProcessor");
				super.handleException(cause);
				break;
		}
	}

	/*
	 * I. From where to load the pages?
	 * 1. From a file
	 * a. Swap File
	 * b. Executable (coff) file
	 * 2. Zero-fill: Populate the page with all 0s.
	 * II. Distinguish coff pages vs. stack pages?
	 * 1. If the page is in swap file, no need to check if it is a coff page or a
	 * stack page.
	 * a. Load from swap file
	 * 2. If the page is not in swap file
	 * a. Zero-fill or load from coff?
	 * b. Depends on whether the page is a coff page or stack page (Next page)
	 * 1. If the faulting VPN is in range [numPages - stackPages, numPages - 1],
	 * then it is a stack page
	 * a. Recall UserProcess.load()
	 * b. If a stack page not in swapFile, zero-fill it
	 * 2. Otherwise it is a coff page.
	 * a. If a coff page not in swapFile, load from coff file
	 * b. How exactly? -> UserProcess.loadSections()
	 * 
	 */

	protected void handlePageFaultException(int faultingVAddr, boolean isReadWrite) {
		// If the demanded page is found in memory: Normal memory access(No page fault)
		// If the pages is not in memory(page fault occurs):
		// If there are free physical pages available
		// Load the demanded page into a free page frame
		// If no free page frames are available - Page replacement
		// Evict a page to free up a page frame
		// Load the required page into the freed page frame

		Lib.debug(dbgProcess, "in handlePageFaultException, PID:" + this.getPID());
		if (!isReadWrite) {
			VMKernel.pinLock.acquire();
		}
		VMKernel.pFExceptionLock.acquire();

		boolean isInSwapFile = checkSwapFile(faultingVAddr);
		if (isInSwapFile) {
			Lib.debug(dbgProcess, "is In SwapFile");
			loadFromSwapFile(faultingVAddr);
		} else {
			Lib.debug(dbgProcess, "is not In SwapFile");
			handleFaultingPage(faultingVAddr);
		}

		VMKernel.pFExceptionLock.release();
		if (!isReadWrite) {
			VMKernel.pinLock.release();
		}

	}

	protected boolean checkSwapFile(int faultingVAddr) {
		int faultingVPN = Processor.pageFromAddress(faultingVAddr);
		if (!pageTable[faultingVPN].valid && -1 != spnArr[faultingVPN]) {
			return true;
		}
		return false;
	}

	protected void loadFromSwapFile(int faultingVAddr) {
		Lib.debug(dbgProcess, "is in loadFromSwapFile");
		int faultingVPN = Processor.pageFromAddress(faultingVAddr);
		byte[] data = new byte[pageSize];
		int spn = spnArr[faultingVPN];

		// VMKernel.pFExceptionLock.acquire();

		Lib.debug(dbgProcess, "pageTable[faultingVPN].dirty: " + pageTable[faultingVPN].dirty);
		pageTable[faultingVPN] = new TranslationEntry(faultingVPN, findPPN(faultingVPN), true, false,
				false,
				false);

		Lib.debug(dbgProcess, "don't release the swap page: " + spn);

		VMKernel.readSwapFile(spn, data);
		// VMKernel.addFreeSwapPage(spn);

		writePhysicalMemory(faultingVPN, data);
		Lib.debug(dbgProcess, "finish loadFromSwapFile");
		// VMKernel.pFExceptionLock.release();

	}

	protected String isCoffOrStackPage(int faultingVPN) {
		if (faultingVPN >= numPages - 1 - stackPages && faultingVPN <= numPages - 1) {
			return "stack or arg";
		} else {
			return "coff";
		}
	}

	protected void handleFaultingPage(int faultingVAddr) {
		Lib.debug(dbgProcess, "is In handleFaultingPage");
		int faultingVPN = Processor.pageFromAddress(faultingVAddr);
		String faultingPageType = isCoffOrStackPage(faultingVPN);
		byte[] data = new byte[pageSize];

		boolean foundSection = false;

		// VMKernel.pFExceptionLock.acquire();

		if (faultingPageType.equals("coff")) {
			Lib.debug(dbgProcess, "is coff");
			// load sections
			for (int s = 0; s < coff.getNumSections(); s++) {
				CoffSection section = coff.getSection(s);

				Lib.debug(dbgProcess, "\tinitializing " + section.getName()
						+ " in handlePageFaultException. section (" + section.getLength() + " pages)");

				for (int i = 0; i < section.getLength(); i++) {
					int vpn = section.getFirstVPN() + i;
					Lib.debug(dbgProcess, "vpn: " + vpn + ", faultingVPN = " + faultingVPN);
					if (vpn != faultingVPN) {
						continue;
					}
					Lib.debug(dbgProcess, "found section");
					foundSection = true;
					Lib.debug(dbgProcess, "pageTable[faultingVPN].dirty: " + pageTable[faultingVPN].dirty);
					pageTable[faultingVPN] = new TranslationEntry(pageTable[faultingVPN].vpn, findPPN(faultingVPN),
							true,
							section.isReadOnly(),
							false,
							pageTable[faultingVPN].dirty);

					section.loadPage(i, pageTable[faultingVPN].ppn);
					VMKernel.addPPageToIPT(pageTable[faultingVPN].ppn, this, faultingVPN);

					break;
				}

				if (foundSection) {
					break;
				}
			}
		} else if (faultingPageType.equals("stack or arg")) {
			Lib.debug(dbgProcess, "is stack or arg, faultingVPN = " + faultingVPN);
			// zero-fill it: set every byte on the page to the value 0
			Lib.debug(dbgProcess, "pageTable[faultingVPN].dirty: " + pageTable[faultingVPN].dirty);
			pageTable[faultingVPN] = new TranslationEntry(pageTable[faultingVPN].vpn, findPPN(faultingVPN), true,
					pageTable[faultingVPN].readOnly,
					false, pageTable[faultingVPN].dirty);
			writePhysicalMemory(faultingVPN, data);

			Lib.debug(dbgProcess, "finish stack");
		}

		// VMKernel.pFExceptionLock.release();

	}

	protected void readPhysicalMemory(int ppn, byte[] data) {
		// byte[] memory = Machine.processor().getMemory();
		int pos = ppn * pageSize;
		System.arraycopy(Machine.processor().getMemory(), pos, data, 0, pageSize);
		Lib.debug(dbgProcess, "in read Physical memory. Pos = " + pos);
	}

	protected void writePhysicalMemory(int vpn, byte[] data) {
		// Lib.debug(dbgProcess, "in writePhysicalMemory, is pinned? " +
		// VMKernel.getPageInfo(pageTable[vpn].ppn).pinned);
		// byte[] memory = Machine.processor().getMemory();
		int pos = pageTable[vpn].ppn * pageSize;
		System.arraycopy(data, 0, Machine.processor().getMemory(), pos, pageSize);
		VMKernel.addPPageToIPT(pageTable[vpn].ppn, this, vpn);
		Lib.debug(dbgProcess, "pageTable[vpn].dirty: " + pageTable[vpn].dirty);
	}

	protected int findPPN(int vpn) {
		int ppn = UserKernel.getFreePPage();
		Lib.debug(dbgProcess, "ppn: " + ppn);
		if (ppn == -1) {
			// no free page frames are available - Page replacement
			ppn = pageReplacement();
			Lib.debug(dbgProcess, "new ppn: " + ppn);
		}

		return ppn;
	}

	protected int pageReplacement() {
		Lib.debug(dbgProcess, "is in page replacement, pinCount = " + VMKernel.pinCount);

		while (VMKernel.pinCount >= Machine.processor().getNumPhysPages()) {
			VMKernel.fullPinCV.sleep();
		}

		int victimPage = chooseVictimPage();
		eviction(victimPage);

		return victimPage;
	}

	// "Clock Algorithm" aka "second chance page replacement algorithm"
	// https://www.geeksforgeeks.org/second-chance-or-clock-page-replacement-policy/
	protected int chooseVictimPage() {
		Lib.debug(dbgProcess, "is in choose victim page");
		VMProcess victimProcess = VMKernel.getPageInfo(VMKernel.getVictimPage()).getVMProcess();
		int victimVpn = VMKernel.getPageInfo(VMKernel.getVictimPage()).getVpn();
		Lib.debug(dbgProcess, "victimPage in VMKernel: " + VMKernel.getVictimPage());
		while (victimProcess.pageTable[victimVpn].used || VMKernel.getPageInfo(VMKernel.getVictimPage()).pinned) {
			victimProcess.pageTable[victimVpn].used = false;
			VMKernel.updateVictimPage();
			victimProcess = VMKernel.getPageInfo(VMKernel.getVictimPage()).getVMProcess();
			victimVpn = VMKernel.getPageInfo(VMKernel.getVictimPage()).getVpn();
			Lib.debug(dbgProcess, "victimPage in VMKernel: " + VMKernel.getVictimPage() + " "
					+ victimProcess.pageTable[victimVpn].used);
		}
		int victim = VMKernel.getVictimPage();
		Lib.debug(dbgProcess, "victim: " + victim);
		VMKernel.updateVictimPage();
		return victim;
	}

	protected void eviction(int victimPage) {
		Lib.debug(dbgProcess, "is in eviction. victim page is " + victimPage);
		byte[] data = new byte[pageSize];
		VMProcess victimProcess = VMKernel.getPageInfo(victimPage).getVMProcess();
		int victimVpn = VMKernel.getPageInfo(victimPage).getVpn();
		Lib.debug(dbgProcess, "victim vpn is " + victimVpn);
		Lib.debug(dbgProcess, "victimProcess.pageTable[victimVpn].dirty: " + victimProcess.pageTable[victimVpn].dirty);
		// if (victimProcess.pageTable[victimVpn].dirty &&
		// !victimProcess.pageTable[victimVpn].readOnly) {
		if (victimProcess.pageTable[victimVpn].dirty && !victimProcess.pageTable[victimVpn].readOnly) {
			Lib.debug(dbgProcess, "evicted page is dirty");
			readPhysicalMemory(victimPage, data);
			swapOut(data, victimProcess, victimVpn);
		}

		victimProcess.pageTable[victimVpn].ppn = -1;
		victimProcess.pageTable[victimVpn].valid = false;
		victimProcess.pageTable[victimVpn].used = false;
		VMKernel.removePPageFromIPT(victimPage);
	}

	protected void swapOut(byte[] data, VMProcess victimProcess, int victimVpn) {
		Lib.debug(dbgProcess, "is in swapOut");
		int spn = victimProcess.spnArr[victimVpn];
		if (-1 == spn) {
			spn = VMKernel.getFreeSwapPage();
			victimProcess.spnArr[victimVpn] = spn;
		}
		VMKernel.writeSwapFile(spn, data);
		Lib.debug(dbgProcess, "victimProcess.pageTable[victimVpn].dirty: " + victimProcess.pageTable[victimVpn].dirty);
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
		Lib.debug(dbgProcess, "is in readVirtualMemory");
		return virtualMemoryHelper(vaddr, data, offset, length, false);
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
		Lib.debug(dbgProcess, "is in writeVirtualMemory");
		return virtualMemoryHelper(vaddr, data, offset, length, true);
	}

	private int virtualMemoryHelper(int vaddr, byte[] data, int offset, int length, boolean isWrite) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		if (vaddr < 0 || vaddr >= numPages * pageSize || length == 0) {
			return 0;
		}

		int endAddr = vaddr + length;
		int startVPage = Processor.pageFromAddress(vaddr),
				endVPage = Processor.pageFromAddress(endAddr - 1),
				totalAmount = 0;
		for (int vpn = startVPage; vpn <= endVPage; vpn++) {
			if (vpn >= numPages || (isWrite && pageTable[vpn].readOnly)) {
				return totalAmount;
			}

			VMKernel.pinLock.acquire();

			if (!pageTable[vpn].valid || pageTable[vpn].ppn == -1) {
				// Lib.debug(dbgProcess, "makeAddress = " + Processor.makeAddress(vpn, 0));
				// Lib.debug(dbgProcess, "vaddr = " + vaddr);
				Lib.debug(dbgProcess, "pageTable[vpn] is invalid in helper");
				handlePageFaultException(vaddr, true);
				Lib.debug(dbgProcess, "after handle pageFaultException, is valid? " + pageTable[vpn].valid
						+ ", ppn is " + pageTable[vpn].ppn);

			}

			VMKernel.pinPage(pageTable[vpn].ppn);

			VMKernel.pinLock.release();

			pageTable[vpn].used = true;
			if (isWrite) {
				pageTable[vpn].dirty = true;
			}
			Lib.debug(dbgProcess, "ppn in helper: " + pageTable[vpn].ppn + ". vpn: " + vpn);
			int pPageOffset = Processor.offsetFromAddress(vaddr),
					pos = pageTable[vpn].ppn * pageSize + pPageOffset,
					amount = Math.min(endAddr, (vpn + 1) * pageSize) - vaddr;

			// byte[] memory = Machine.processor().getMemory();
			arrayCopy(data, offset, Machine.processor().getMemory(), pos, amount, isWrite);

			VMKernel.unpinPage(pageTable[vpn].ppn);
			vaddr = (vpn + 1) * pageSize;
			totalAmount += amount;
			offset += amount;

		}

		return totalAmount;
	}

	private void arrayCopy(byte[] data, int offset, byte[] memory, int pos, int amount, boolean isWrite) {
		if (isWrite) {
			System.arraycopy(data, offset, memory, pos, amount);
		} else {
			System.arraycopy(memory, pos, data, offset, amount);
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

	public int[] spnArr;

}
