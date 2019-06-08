package nachos.vm;

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


	//####################################################################
	// PA3 part 1
	//####################################################################
	/**
f	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {

		pageTable = new TranslationEntry[numPages];

		for(int vpn = 0; vpn < numPages; vpn++) {
			//####################################################################
			// PA3 part 2
			//####################################################################
			//	init the TranslationEntries as invalid.
			pageTable[vpn] = new TranslationEntry(vpn, vpn, false, false, false, false);
		}

		//Also do not initialize the page by, e.g., loading from the COFF file.
		// Instead, you will do this on demand when the process causes a page fault

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {

		VMKernel.lock.acquire();

		//release all used pages in phy memory if valid bit is true
		for(int vpn=0; vpn< pageTable.length; vpn++){
			if(pageTable[vpn].valid ==  true){
				VMKernel.freePhyPages.add(pageTable[vpn].ppn);
			}
			// if valid bit is false, page store in swap file, ignore it is fine
		}
		VMKernel.lock.release();
	}

	//#############################################################################################
	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 *
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 *
	 * ###########################################################################
	 * copy data from vaddr in user virtual memory TO data buffer in kernel, read user VM
	 * ###########################################################################
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.debug(dbgProcess, "readVirtualMemory()");

		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		//####################################################################
		// Step1: Translation : vaddr to paddr
		// calc vpn given vaddr, find ppn given a vpn in the page table, calc paddr

		Processor processor = Machine.processor();
		int vpn = processor.pageFromAddress(vaddr);				// Extract the page number component from a 32-bit address.
		int pageOffset = processor.offsetFromAddress(vaddr);	// Extract the offset component from an address.

		if( vpn<0 || vpn>= pageTable.length){
			Lib.debug(dbgProcess, " vpn is out of bound ");
			return 0;
		}
		if(pageOffset<0 || pageOffset>pageSize){
			Lib.debug(dbgProcess, " pageOffset is out of bound ");
			return 0;
		}

		//####################################################################
		// PA3 part 1
		// obtain paddr
		// check if valid page, if not, call handlePageFaultException()
		//####################################################################
		int paddr = 0;
		if(pageTable[vpn].valid){
			int ppn = pageTable[vpn].ppn;
			paddr = ppn * pageSize + pageOffset;

		}else {

			VMKernel.lock.acquire();
			handlePageFaultException(vaddr);
			VMKernel.lock.release();

			paddr = pageTable[vpn].ppn * pageSize + pageOffset;

		}
		//####################################################################

		if (paddr < 0 || (paddr+length) >= memory.length)  {
			Lib.debug(dbgProcess, "phy addr is out of range ");
			return 0;
		}

		//####################################################################
		// Step2: read data in paddr to data buffer in kernel
		// !!! while reading data, pin the page so that it will not be evicted by clockLUR algorithm.
		//####################################################################

		if((pageOffset + length)<= pageSize){	//case 1: data length does not extend off the page size

			//####################################################################
			//pin to true before arraycopy
			VMKernel.lock.acquire();
			VMKernel.invertedPageTable[pageTable[vpn].ppn].pin = true;
			VMKernel.countPin++;
			VMKernel.lock.release();

			System.arraycopy(memory, paddr, data, offset, length);	//literally reading data

			// modify used bit before unpinned
			pageTable[vpn].used = true;

			//unpin to true before arraycopy

			VMKernel.lock.acquire();
			VMKernel.invertedPageTable[pageTable[vpn].ppn].pin = false;
			VMKernel.countPin--;
			VMKernel.lock.release();

			VMKernel.pinnedCVLock.acquire();
			VMKernel.pinnedCV.wake();	//sleep in clockLRU()
			VMKernel.pinnedCVLock.release();

			//####################################################################

		}else{	//case 2: data length extend off the page size, data stored in two diff pages

			//2.1	copy data in first page into data buffer
			int amount1 = pageSize - pageOffset;

			//####################################################################
			//pin to true before arraycopy
			VMKernel.lock.acquire();
			VMKernel.invertedPageTable[pageTable[vpn].ppn].pin = true;
			VMKernel.countPin++;
			VMKernel.lock.release();

			System.arraycopy(memory, paddr, data, offset, amount1);

			// modify used bit before unpinned
			pageTable[vpn].used = true;

			//unpin to true before arraycopy
			VMKernel.lock.acquire();
			VMKernel.invertedPageTable[pageTable[vpn].ppn].pin = false;
			VMKernel.countPin--;
			VMKernel.lock.release();

			VMKernel.pinnedCVLock.acquire();
			VMKernel.pinnedCV.wake();	//sleep in clockLRU()
			VMKernel.pinnedCVLock.release();
			//####################################################################

			//2.2	copy data in second page into data buffer, continuous vpn
			int vpn2 = vpn+1;

			//check if vpn2 out of bound
			if( vpn2<0 || vpn2>= pageTable.length){
				Lib.debug(dbgProcess, " vpn is out of bound ");
				return amount1;
			}
			if (pageTable[vpn2].valid==false){
				Lib.debug(dbgProcess, " invalid page ");
				return amount1;
			}

			int ppn2 = pageTable[vpn2].ppn;	//No need to add pageOffset here, start from head of a page
			int paddr2 = ppn2 * pageSize;

			int amount2 = length - amount1;

			if (paddr2 < 0 || (paddr2+amount2) >= memory.length)  {
				Lib.debug(dbgProcess, "phy addr is out of range ");
				return 0;
			}

			//####################################################################
			//pin to true before arraycopy
			VMKernel.lock.acquire();
			VMKernel.invertedPageTable[pageTable[vpn].ppn].pin = true;
			VMKernel.countPin++;
			VMKernel.lock.release();

			System.arraycopy(memory, paddr2 , data, offset+amount1, amount2);

			// modify used bit before unpinned
			pageTable[vpn].used = true;

			//unpin to true before arraycopy
			VMKernel.lock.acquire();
			VMKernel.invertedPageTable[pageTable[vpn].ppn].pin = false;
			VMKernel.countPin--;
			VMKernel.lock.release();

			VMKernel.pinnedCVLock.acquire();
			VMKernel.pinnedCV.wake();	//sleep in clockLRU()
			VMKernel.pinnedCVLock.release();
			//####################################################################
		}

		return length;
	}


	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 *
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 *
	 * ###########################################################################
	 * copy data from data in kernel TO vaddr in user virtual memory, write user VM
	 *
	 * addr translation from vaddr to paddr is similar to readVirtualMemory()
	 * but need to take care of readonly page
	 * ###########################################################################
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.debug(dbgProcess, "writeVirtualMemory()");

		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0 )
			return 0;

		//####################################################################
		// Step1: Translation : vaddr to paddr
		Processor processor = Machine.processor();
		int vpn = processor.pageFromAddress(vaddr);			// Extract the page number component from a 32-bit address.
		int pageOffset = processor.offsetFromAddress(vaddr);	// Extract the offset component from an address.

		if( vpn<0 || vpn>= pageTable.length){
			Lib.debug(dbgProcess, " vpn is out of bound ");
			return 0;
		}
		if(pageOffset<0 || pageOffset>pageSize){
			Lib.debug(dbgProcess, " pageOffset is out of bound ");
			return 0;
		}
		if (pageTable[vpn].readOnly){
			Lib.debug(dbgProcess, " fail to write, write to read-only page ");
			return 0;
		}

		//####################################################################
		// PA3 part 1
		// chekc if valid page, if not, call handlePageFault()
		//####################################################################
		int paddr = 0;
		if(pageTable[vpn].valid){
			int ppn = pageTable[vpn].ppn;
			paddr = ppn * pageSize + pageOffset;
		}else {

			VMKernel.lock.acquire();
			handlePageFaultException(vaddr);
			VMKernel.lock.release();

			if(pageTable[vpn].valid){
				paddr = pageTable[vpn].ppn * pageSize + pageOffset;
			}
			else{
				//phy memory is "pinned" can not evict phy page
				return 0;
			}
		}
		//####################################################################

		if (paddr < 0 || (paddr+length) >= memory.length)  {
			Lib.debug(dbgProcess, "phy addr is out of range ");
			return 0;
		}

		//####################################################################
		// Step2: write data from data buffer in kernel into paddr

		if((pageOffset + length)<= pageSize){	//case 1: data length does not extend off the page size


			//####################################################################
			//pin to true before arraycopy
			VMKernel.lock.acquire();
			VMKernel.invertedPageTable[pageTable[vpn].ppn].pin = true;
			VMKernel.countPin++;
			VMKernel.lock.release();

			System.arraycopy( data, offset, memory, paddr, length);

			// modify used bit AND dirty bit before unpinned , after arraycopy
			pageTable[vpn].used = true;
			pageTable[vpn].dirty = true;

			//unpin to true before arraycopy
			VMKernel.lock.acquire();
			VMKernel.invertedPageTable[pageTable[vpn].ppn].pin = false;
			VMKernel.countPin--;
			VMKernel.lock.release();

			VMKernel.pinnedCVLock.acquire();
			VMKernel.pinnedCV.wake();	//sleep in clockLRU()
			VMKernel.pinnedCVLock.release();
			//####################################################################

		}else{	//case 2: data length extend off the page size, data stored in two diff pages

			//2.1	copy data in first page into data buffer
			int amount1 = pageSize - pageOffset;

			//####################################################################
			//pin to true before arraycopy
			VMKernel.lock.acquire();
			VMKernel.invertedPageTable[pageTable[vpn].ppn].pin = true;
			VMKernel.countPin++;
			VMKernel.lock.release();

			System.arraycopy( data, offset, memory, paddr, amount1);

			// modify used bit AND dirty bit before unpinned , after arraycopy
			pageTable[vpn].used = true;
			pageTable[vpn].dirty = true;

			//unpin to true before arraycopy
			VMKernel.lock.acquire();
			VMKernel.invertedPageTable[pageTable[vpn].ppn].pin = false;
			VMKernel.countPin--;
			VMKernel.lock.release();

			VMKernel.pinnedCVLock.acquire();
			VMKernel.pinnedCV.wake();	//sleep in clockLRU()
			VMKernel.pinnedCVLock.release();
			//####################################################################

			pageTable[vpn].used = true;
			pageTable[vpn].dirty = true;

			//2.2	copy data in second page into data buffer, continuous vpn
			int vpn2 = vpn+1;

			if( vpn2<0 || vpn2>= pageTable.length){
				Lib.debug(dbgProcess, " vpn is out of bound ");
				return amount1;
			}
			if (pageTable[vpn2].valid==false){
				Lib.debug(dbgProcess, " invalid page");
				return amount1;
			}
			if (pageTable[vpn2].readOnly){
				Lib.debug(dbgProcess, " fail to write, write to read-only page ");
				return amount1;
			}

			int ppn2 = pageTable[vpn2].ppn;
			int paddr2 = ppn2 * pageSize;	//No need to add pageOffset here, start from head of a page

			int amount2 = length - amount1;

			if (paddr2 < 0 || (paddr2+amount2) >= memory.length)  {
				Lib.debug(dbgProcess, "phy addr is out of range ");
				return 0;
			}

			//####################################################################
			//pin to true before arraycopy
			VMKernel.lock.acquire();
			VMKernel.invertedPageTable[pageTable[vpn].ppn].pin = true;
			VMKernel.countPin++;
			VMKernel.lock.release();

			System.arraycopy(data, offset+amount1, memory, paddr2 , amount2);

			// modify used bit AND dirty bit before unpinned , after arraycopy
			pageTable[vpn].used = true;
			pageTable[vpn].dirty = true;

			//unpin to true before arraycopy
			VMKernel.lock.acquire();
			VMKernel.invertedPageTable[pageTable[vpn].ppn].pin = false;
			VMKernel.countPin--;
			VMKernel.lock.release();

			VMKernel.pinnedCVLock.acquire();
			VMKernel.pinnedCV.wake();	//sleep in clockLRU()
			VMKernel.pinnedCVLock.release();
			//####################################################################
		}

		return length;
	}


	/**
	 * LRU clock page replacement algorithm to find a page can be evicted
	 * when there is a page fault and the phy memory is filled
	 * Return the ppn of a victim page
	 */
	//####################################################################
	// PA3 part 2
	//####################################################################
	private int LRUclock() {

		//iterate all the ppn and check if the ppn can be evicted
		while (true) {

			if (VMKernel.invertedPageTable[VMKernel.victim].pin == true) {
				//if all pinned pages, sleep the process
				if (VMKernel.countPin == Machine.processor().getNumPhysPages()) {

					VMKernel.pinnedCVLock.acquire();
					VMKernel.pinnedCV.sleep();
					VMKernel.pinnedCVLock.release();
				}
				VMKernel.victim = (VMKernel.victim + 1) % Machine.processor().getNumPhysPages();    //circular array
				continue;
			}
			//found a phy page can be evicted
			if (VMKernel.invertedPageTable[VMKernel.victim].entry.used == false) {
				break;
			}
			VMKernel.invertedPageTable[VMKernel.victim].entry.used = false;

			//increment the nextPPN for the next time running LRUclock alg
			VMKernel.victim = (VMKernel.victim + 1) % Machine.processor().getNumPhysPages();
		}
		//store the victimPPN to return
		int victimPPN = VMKernel.victim;

		//increment the nextPPN for the next time running LRUclock alg
		VMKernel.victim = (VMKernel.victim + 1) % Machine.processor().getNumPhysPages();

		return victimPPN;
	}

	/**
	 * handlePageFault() takes the virtual address to the non existed page,
	 * prepare the requested page on demand.
	 * load missing page from disk to page table, and the uplate TLB
	 */
	private void handlePageFaultException(int badVirtualAddr){

		//####################################################################
		// PA3 part 1
		//####################################################################
		int badVpn = Processor.pageFromAddress(badVirtualAddr);	//calc the page number for bad addr
		int vpn=0;
		int ppn=0;

		//Note that faults on different pages are handled in different ways.
		//1	 A fault on a code page should read the corresponding code/ data page from the COFF file
		//2	 A fault on a stack page or arguments page should zero-all the frame.

		//###################################################
		//step 1: loop all pages of sections of the COFF file to find the page cause page fault
		//###################################################
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			//iterate all pages(paging) in each section, section.getLength() returns the number of pages in each section
			for (int i = 0; i < section.getLength(); i++) {

				//####################################################################
				// step 2: obtain new process's vpn and the ppn to be used
				//####################################################################

				vpn = section.getFirstVPN() + i;

				if(vpn == badVpn) {    //find the fault page

					//#################################################
					//2.1: check if has free phy pages, allocate one from list, same as proj2, get ppn
					//#################################################
					if (!VMKernel.freePhyPages.isEmpty()) {
						//assign ppn
						ppn = VMKernel.freePhyPages.removeLast();

						//init the trasnlationEntry in invertedPageTable every time demanding a page
						//store a reference that map invertedPageTable entry to pageTable entry
						VMKernel.invertedPageTable[ppn].entry = pageTable[vpn];
					} else {

						//#################################################
						//2.2: if phy page is full, Select a victim for replacement using Clock algorithm
						//#################################################
						int victimPPN = LRUclock();

						//#################################################
						//2.3: check if victim/evicted page is dirty, swap out,
						// and update evicted page info by storing swapPageNum and set valid to false
						//#################################################

						//evicted page info stored in VMKernel.invertedPageTable[victimPPN]
						if (VMKernel.invertedPageTable[victimPPN].entry.dirty) {

							int swapPageNum = 0;	//record where to write to

							if (!VMKernel.freeSwapPageList.isEmpty()) {    //allocate free pages on swap files
								swapPageNum = VMKernel.freeSwapPageList.removeLast();
							} else {
								swapPageNum = VMKernel.spnGenerator;
								VMKernel.spnGenerator++;
							}

							int swapfilePos = swapPageNum * pageSize;
							int mainMemoryOffset = Processor.makeAddress(victimPPN, 0);    //VMKernel.invertedPageTable[victimPPN].entry.ppn

							//@@@ swap out/write from phy memory to swpaFile
							VMKernel.swapFile.write(swapfilePos, Machine.processor().getMemory(), mainMemoryOffset, pageSize);

							//#####################################################
							//Update old pageTable entry
							//#####################################################
							// store spn in the vpn of the old process's pagetable, so that can find the page in swapfile next time
							VMKernel.invertedPageTable[victimPPN].entry.vpn = swapPageNum;
						}

						//set valid bit of old page to false means page not in memory
						VMKernel.invertedPageTable[victimPPN].entry.valid = false;

						ppn = victimPPN;
					}

					//####################################################################
					//step3: update the invertedPageTable with new page's pageTable entry.
					// map invertedPageTable entry to new page's pageTable entry.
					//####################################################################
					// 3.1 if not dirty page, assign phy page, and update the page info
					if (!pageTable[vpn].dirty) {

						//eviction
						section.loadPage(i, ppn); 	//Load a page from this segment into physical memory.

						//update vpn, ppn, valid bit to true, readOnly bit, used bit to true
						pageTable[vpn] = new TranslationEntry(vpn, ppn, true, section.isReadOnly(), true, false);
					}
					// 3.2 If the new page is dirty/modified, swap in/read the page from the swap file into phy memory
					else{
						//if it is a dirty bit, pageTable[vpn].vpn stores swapPageNum instead of the vpn
						int swapfilePos = pageTable[vpn].vpn * pageSize;
						int mainMemoryOffset = Processor.makeAddress(ppn, 0);

						//#####################################################
						//@@@ swap in: read from swapFile to phy page in main memory
						//#####################################################
						VMKernel.swapFile.read(swapfilePos, Machine.processor().getMemory(), mainMemoryOffset, pageSize);

						//release the swap in page by adding spn into freeSwapPageList
						VMKernel.freeSwapPageList.add(pageTable[vpn].vpn);

						//update vpn, ppn, valid bit to true, readOnly bit to false, used bit to true
						pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, true, true);
					}

					//#####################################################
					// update invertedPageTable entry with new pageTable entry
					//!!! store a reference to upageTable[vpn]
					//#####################################################
					VMKernel.invertedPageTable[ppn].entry = pageTable[vpn];

					return;
				}
			}
		}

		//#######################################################################################
		//case 2: the fault page in stack and arg
		for( vpn=vpn+1; vpn < numPages; vpn++) {

			if(vpn == badVpn) {    //find the fault page

				//#################################################
				//2.1: check if has free phy pages, allocate one from list, same as proj2, get ppn
				//#################################################
				if (!VMKernel.freePhyPages.isEmpty()) {
					//assign ppn
					ppn = VMKernel.freePhyPages.removeLast();

					//init the trasnlationEntry in invertedPageTable every time demanding a page
					//store a reference that map invertedPageTable entry to pageTable entry
					VMKernel.invertedPageTable[ppn].entry = pageTable[vpn];
				} else {

					//#################################################
					//2.2: if phy page is full, Select a victim for replacement using Clock algorithm
					//#################################################
					int victimPPN = LRUclock();

					//#################################################
					//2.3: check if victim/evicted page is dirty, swap out,
					// and update evicted page info by storing swapPageNum and set valid to false
					//#################################################

					//evicted page info stored in VMKernel.invertedPageTable[victimPPN]
					if (VMKernel.invertedPageTable[victimPPN].entry.dirty) {

						int swapPageNum = 0;	//record where to write to

						if (!VMKernel.freeSwapPageList.isEmpty()) {    //allocate free pages on swap files
							swapPageNum = VMKernel.freeSwapPageList.removeLast();
						} else {
							swapPageNum = VMKernel.spnGenerator;
							VMKernel.spnGenerator++;
						}

						int swapfilePos = swapPageNum * pageSize;
						int mainMemoryOffset = Processor.makeAddress(victimPPN, 0);    //VMKernel.invertedPageTable[victimPPN].entry.ppn

						//@@@ swap out/write from phy memory to swpaFile
						VMKernel.swapFile.write(swapfilePos, Machine.processor().getMemory(), mainMemoryOffset, pageSize);

						//#####################################################
						//Update old pageTable entry
						//#####################################################
						// store spn in the vpn of the old process's pagetable, so that can find the page in swapfile next time
						VMKernel.invertedPageTable[victimPPN].entry.vpn = swapPageNum;
					}

					//set valid bit of old page to false means page not in memory
					VMKernel.invertedPageTable[victimPPN].entry.valid = false;

					ppn = victimPPN;
				}


				//####################################################################
				//step3: update the invertedPageTable with new page's pageTable entry.
				// map invertedPageTable entry to new page's pageTable entry.
				//####################################################################

				int mainMemoryOffset = Processor.makeAddress(ppn, 0);

				if (!pageTable[vpn].dirty) {

					//instead of loading page into phy mem, zero-fill the frame.
					byte[] zeroBuf = new byte[pageSize];
					for (int j = 0; j < zeroBuf.length; j++) {
						zeroBuf[j] = 0;
					}
					//copy data in first page into data buffer
					System.arraycopy(zeroBuf, 0, Machine.processor().getMemory(), mainMemoryOffset, Processor.pageSize);

					//update vpn, ppn, valid bit to true, readOnly bit, used bit to true
					pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, true, false);

				}
				// 3.2 If the new page is dirty/modified, swap in/read the page from the swap file into phy memory
				else{
					//if it is a dirty bit, pageTable[vpn].vpn stores swapPageNum instead of the vpn
					int swapfilePos = pageTable[vpn].vpn * pageSize;

					//#####################################################
					//@@@ swap in: read from swapFile to phy page in main memory
					//#####################################################
					VMKernel.swapFile.read(swapfilePos, Machine.processor().getMemory(), mainMemoryOffset, pageSize);

					//release the swap in page by adding spn into freeSwapPageList
					VMKernel.freeSwapPageList.add(pageTable[vpn].vpn);

					//update vpn, ppn, valid bit to true, readOnly bit to false, used bit thandlePageFaultExceptiono true
					pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, true, true);
				}

				//#####################################################
				// update invertedPageTable entry with new pageTable entry
				//!!! store a reference to upageTable[vpn]
				//#####################################################
				VMKernel.invertedPageTable[ppn].entry = pageTable[vpn];
			}
		}
		return;
	}
	//#############################################################################################

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
			//####################################################################
			// PA3 part 1
			//####################################################################
			case Processor.exceptionPageFault:
				handlePageFaultException(processor.readRegister(Processor.regBadVAddr));
				//do not increment the PC (as is done when handling a system call)
				// so that the machine will re-execute the faultin instruction.
				break;
			//########################################################################
		default:
			super.handleException(cause);
			break;
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}

