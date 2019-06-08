package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.*;

import java.io.EOFException;

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

//	########################################################################################
//  pageTable size is decided by the numPages, numPages differs by each process
//	So, pageTable need to be constructed in loadSection()
// 	Original Code:
//	########################################################################################
//		int numPhysPages = Machine.processor().getNumPhysPages();
//		pageTable = new TranslationEntry[numPhysPages];
//		for (int i = 0; i < numPhysPages; i++)
//			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
//	########################################################################################

		//###########################################################
		//PA2 part1 implement filesystem call
		//###########################################################
		fileTable = new OpenFile[maxFileNum];	// init an array of size 16 to store OpenFile

		//descriptors 0 and 1 must refer to standard input and standard output.
		fileTable[0] = UserKernel.console.openForReading();
		fileTable[1] = UserKernel.console.openForWriting();

		//init the rest with null
		for(int i=2; i<16; i++){
			fileTable[i] = null;
		}

		//###########################################################
		//PA2 part3	 section1 exec()
		//processIDGenerator in UserKernel generate pid for each process
		//###########################################################

		UserKernel.lock.acquire();
		this.processID = UserKernel.nextProcessID;
		UserKernel.nextProcessID++;
		UserKernel.numOfProcesses++;
		UserKernel.lock.release();

		// used in handleExec()
		childProcessMap = new HashMap<Integer, UserProcess>();
		parentProcess = null;

		//used in handleJoin() and handleExit()
		childStatusMap =  new HashMap<Integer, Integer>();

		// used in handleJoin()
		cvLock = new Lock();
		childCompletedCV = new Condition2(cvLock);

		//used in handleException() and handleExit()
		isNormal = true;

		//used in handleExit() and  handleJoin()
		abnormalChildSet = new HashSet<Integer>();

		//###########################################################
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 *
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
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
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
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

	//###########################################################################
	// PA2 part2	section 3
	// Modify UserProcess.readVirtualMemory and UserProcess.writeVirtualMemory,
	// which copy data between the kernel and the user's virtual address space,
	// so that they work with multiple user processes.
	//###########################################################################
	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
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
		if (pageTable[vpn].valid==false){
			Lib.debug(dbgProcess, " invalid page");
			return 0;
		}

		int ppn = pageTable[vpn].ppn;
		int paddr = ppn * pageSize + pageOffset;


		//####################################################################
		// Step2: read data in paddr to data buffer in kernel
		//	EX	pageSize = 128 		page0 0x80	page1 0x100
		//	if vaddr is 0x7E and data.length is 4, out of page bound

		if((pageOffset + length)<= pageSize){	//case 1: data length does not extend off the page size

			System.arraycopy(memory, paddr, data, offset, length);

			pageTable[vpn].used = true;		//mark this page used

		}else{	//case 2: data length extend off the page size, data stored in two diff pages

			//2.1	copy data in first page into data buffer
			int amount1 = pageSize - pageOffset;
			System.arraycopy(memory, paddr, data, offset, amount1);

			pageTable[vpn].used = true;		//mark this page used

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

			System.arraycopy(memory, paddr2 , data, offset+amount1, amount2);

			pageTable[vpn2].used = true;
		}

		//####################################################################

//	original code:(assume vpn==ppn)
//		int amount = Math.min(length, memory.length - vaddr);
//		System.arraycopy( memory, vaddr, data, offset, amount);
//		return amount

		return length;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
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
		if (pageTable[vpn].valid==false){
			Lib.debug(dbgProcess, " invalid page ");
			return 0;
		}
		if (pageTable[vpn].readOnly){
			Lib.debug(dbgProcess, " fail to write, write to read-only page ");
			return 0;
		}

		int ppn = pageTable[vpn].ppn;
		int paddr = ppn * pageSize + pageOffset;

		if (paddr < 0 || (paddr+length) >= memory.length)  {
			Lib.debug(dbgProcess, "phy addr is out of range ");
			return 0;
		}

		//####################################################################
		// Step2: write data from data buffer in kernel into paddr

		if((pageOffset + length)<= pageSize){	//case 1: data length does not extend off the page size

			System.arraycopy( data, offset, memory, paddr, length);

			pageTable[vpn].used = true;
			pageTable[vpn].dirty = true;

		}else{	//case 2: data length extend off the page size, data stored in two diff pages

			//2.1	copy data in first page into data buffer
			int amount1 = pageSize - pageOffset;
			System.arraycopy( data, offset, memory, paddr, amount1);

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

			System.arraycopy(data, offset+amount1, memory, paddr2 , amount2);

			pageTable[vpn2].used = true;
			pageTable[vpn2].dirty = true;
		}
		//####################################################################
//	original code:(assume vpn==ppn)
//		int amount = Math.min(length, memory.length - vaddr);
//		System.arraycopy(data, offset, memory, vaddr, amount);
//		return amount

		return length;
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
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;	//decide the virtual page size for each process
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

		//we know the numPages for the process before calling loadSections()
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



	//#######################################################################
	//# PA2 part2	section 2
	//Modify UserProcess.loadSections() so that it allocates the
	//number of physical pages that it needs
	//#######################################################################
	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 *
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {

		UserKernel.lock.acquire();
		int numFreePhyPages = UserKernel.freePhyPages.size();	// critical section to access freePhyPages
		UserKernel.lock.release();

		//if free phy page not enough for memory allocation of a new process, return an error
		if (numPages > numFreePhyPages) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory" );
			return false;
		}

		//####################################
		//	1.	init the new pageTable, fill in with vpn and ppn
		pageTable = new TranslationEntry[numPages];

		UserKernel.lock.acquire();	//critical section to access freePhyPages
		for(int vpn = 0; vpn < numPages; vpn++) {
			int ppn = UserKernel.freePhyPages.removeLast();	// use removeLast() to allocate phy page in reverse order
															// EX: for vpn==0, assign ppn=numPages-1
			pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, false, false);
		}
		UserKernel.lock.release();

		//###################################
		// 2.	load COFF sections
		// it allocates the number of physical pages that it needs (that is, based on the
		// size of the address space required to load and run the user program).

		// iterate all section(segmentation) of a process's addr space
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			//iterate all pages(paging) in each section, section.getLength() returns the number of pages in each section

			for (int spn = 0; spn < section.getLength(); spn++) {	// spn: page number of each section

				int vpn = section.getFirstVPN() + spn;	//	int ppn = UserKernel.freePhyPages.removeLast();
				int ppn = pageTable[vpn].ppn;	//ppn already init in the pageTable entry

				// loadPage(spn, ppn): Load a page of this segment into physical memory.
				section.loadPage(spn, ppn);

				//The field TranslationEntry.readOnly should be set to true if the page
				// is coming from a COFF section which is marked as read-only.
				pageTable[vpn].readOnly = section.isReadOnly();
			}
		}

		return true;
	}


	//#######################################################################
	//# PA2 part2	section 3	called by handleExit()
	// exit a process by unloading the section
	//#######################################################################
	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 *
	 * called by handleExit()
	 */
	protected void unloadSections() {

		// iterate pageTbale of this process to add all used pages back to freePhyPages
		UserKernel.lock.acquire();
		for(int i=0; i< pageTable.length; i++){
			if(pageTable[i].used){
				UserKernel.freePhyPages.add(pageTable[i].ppn);
			}
		}
		UserKernel.lock.release();
	}

	//#######################################################################
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



	//###########################################################################
	//PA2 part1
	// Implement the file system calls creat, open, read, write, close, and unlink
	//###########################################################################
	/**
	 * handleFileDescriptor() is shared by handleOpen and handleCreate
	 * store an current file in fileTable and return a new file descriptor if there is empty space
	 * return -1 if no available fileDescriptor
	 */
	private int handleFileDescriptor(OpenFile openfile){

		int fileDescriptor = -1;

		// index 0 and 1 are reserved for stdin and stdout, but can be closed
		for(int i = 0; i < maxFileNum; i++){
			//find an empty place in fileTable to store curFile and assign the fileDescriptor
			if(fileTable[i] == null){
				fileDescriptor = i;
				fileTable[i] = openfile;
				break;
			}
		}
		return fileDescriptor;
	}

	/**
	 * Handle the create() system call.
	 *
	 * Attempt to open the named disk file, creating it if it does not exist,
	 * and return a file descriptor that can be used to access the file.
	 *
	 * Note that creat() can only be used to create files on disk;
	 * creat() will never return a file descriptor referring to a stream.
	 *
	 * Returns the new file descriptor, or -1 if an error occurred.
	 */
	private int handleCreate(int name){

		Lib.debug(dbgProcess, "handleCreate()");

		int fileDescriptor=-1;

		// Check for valid virtual addr
		if (name < 0){
			Lib.debug(dbgProcess, "file virtualAddr is invalid");
			return -1;
		}

		//Transfer data from this process's virtual memory to the specified array.
		//return a String of filename OR null if no terminator is found
		String filename = readVirtualMemoryString(name, maxFileLen);
		if (filename == null){
			Lib.debug(dbgProcess, "can not find the filename given virtualAddr");
			return -1;
		}

		//obtain the OpenFile object given the filename by invoking the stubFileSystem API
		//assume the file is existed, so passed in create para to be false
		//return a OpenFile if found, OR return null if OpenFile not found
		OpenFile openfile = ThreadedKernel.fileSystem.open(filename, false);

		if (openfile == null){
			//file not exist, need to create one by passing in create para to be true
			openfile = ThreadedKernel.fileSystem.open(filename, true);

			if(openfile == null){
				Lib.debug(dbgProcess, "can not create a file");
				return -1;
			}

			//assign new fileDescriptor
			fileDescriptor = handleFileDescriptor(openfile);
		}
		else{
			//file exist, assign fileDescriptor
			fileDescriptor = handleFileDescriptor(openfile);
		}

		return fileDescriptor;
	}

	/**
	 * Handle the open() system call.

	 * Attempt to open the named file and return a file descriptor.
	 *
	 * Note that open() can only be used to open files on disk; open() will never
	 * return a file descriptor referring to a stream.
	 *
	 * Returns the new file descriptor, or -1 if an error occurred.
	 */
	private int handleOpen(int name){

		Lib.debug(dbgProcess, "handleOpen()");

		// Check for valid virtual addr
		if (name < 0){
			Lib.debug(dbgProcess, "file virtualAddr is invalid");
			return -1;
		}

		//Transfer data from this process's virtual memory to the specified array.
		//return a String of filename OR null if no terminator is found
		String filename = readVirtualMemoryString(name, maxFileLen);
		if (filename == null){
			Lib.debug(dbgProcess, "can not find the filename given virtualAddr");
			return -1;
		}

		//obtain the OpenFile object given the filename by invoking the stubFileSystem API
		OpenFile openfile = ThreadedKernel.fileSystem.open(filename, false);
		if(openfile == null){
			Lib.debug(dbgProcess, "can not open the file");
			return -1;
		}

		int fileDescriptor = handleFileDescriptor(openfile);

		//No free fileDescriptor
		if(fileDescriptor ==-1){
			Lib.debug(dbgProcess, "No fileDescriptor is available");
			return -1;
		}
		return fileDescriptor;
	}



	/**
	 * Handle the read() system call.
	 *
	 * Attempt to read up to count bytes into buffer from the file or stream
	 * referred to by fileDescriptor.
	 *
	 * On success, the number of bytes read is returned. If the file descriptor
	 * refers to a file on disk, the file position is advanced by this number.
	 *
	 * It is not necessarily an error if this number is smaller than the number of
	 * bytes requested. If the file descriptor refers to a file on disk,
	 * this indicates that the end of the file has been reached.
	 *
	 * If the file descriptor refers to a stream, this indicates that the fewer bytes
	 * are actually available right now than were requested, but more bytes may become
	 * available in the future.
	 * ###Note that read() never waits for a stream to have more data;
	 * it always returns as much as possible immediately.
	 *
	 * On error, -1 is returned, and the new file position is undefined. This can
	 * happen if fileDescriptor is invalid, if part of the buffer is read-only or
	 * invalid, or if a network stream has been terminated by the remote host and
	 * no more data is available.
	 *
	 * ###########################################################
	 * read data from file(given fileDescriptor) to bufferAddr in virtual memory
	 * ###########################################################
	 */
	int handleRead(int fileDescriptor, int bufferAddr, int totalBytes){

		Lib.debug(dbgProcess, "handleRead()");

		//handle edge case for fileDescriptor, bufferAddr, and count
		if ((fileDescriptor < 0) || (fileDescriptor >= maxFileNum) ) {
			Lib.debug(dbgProcess, "fileDescriptor out of range (0-15)");
			return -1;
		}
		OpenFile openfile = fileTable[fileDescriptor];
		if(openfile == null){
			Lib.debug(dbgProcess, "no file under this fileDescriptor");
			return -1;
		}
		if(totalBytes<0){
			Lib.debug(dbgProcess, "countBytes is negative");
			return -1;
		}
		if(bufferAddr<0){
			Lib.debug(dbgProcess, "bufferAddr is invalid");
			return -1;
		}

		byte[] localBuffer = new byte[pageSize];	//create a local buf with page size 1024, reading 1024 at each time
		int currentBytes = 0;	//offset, how many bytes successfully copied from openfile to bufferAddr
		int readByte = 0;		//bytes read from openfile
		int writeByte = 0;		//bytes written into virtual memory
		int readByteleft;		//bytes left to be read

		// keep track of current reading bytes, read 1024 in max in each round
		while(true){

			readByteleft = totalBytes - currentBytes;
			if(readByteleft <= 0){
				break;
			}

			/* the requestReadByte has 2 cases:
			1	requestReadByte >= 1024(pageSize), read pageSize at each time
			2	requestReadByte < 1024(pageSize), read and immediately return
		 	*/
			if(readByteleft >= pageSize){

				// copy data from openfile to localBuffer
				//Read this file starting at the current file pointer and return the number of bytes successfully read.
				readByte = openfile.read(localBuffer, 0, pageSize);

				if(readByte==-1){
					Lib.debug(dbgProcess, "read file out of bound");
					return -1;
				}

				if(readByte == pageSize){
					// EX totalBytes=2000, readByte = 1024, read the rest in the next round

					//copy read data from kernel localBuffer to  user virtual memory
					writeByte = writeVirtualMemory(bufferAddr + currentBytes, localBuffer,0, pageSize);

					if(writeByte < pageSize){
						Lib.debug(dbgProcess, "write out of memory");
						return -1;
					}
					currentBytes += writeByte;
				}
				else{
					// handle the case where readByte<1024 bytes, read and return immediately
					// EX totalBytes=2000, readByte = 500, read 500 into bufferAddr  and return

					writeByte = writeVirtualMemory(bufferAddr + currentBytes, localBuffer,0,readByte);

					if(writeByte < readByte){
						Lib.debug(dbgProcess, "write out of memory");
						return -1;
					}
					currentBytes += writeByte;
					return currentBytes;
				}
			}
			else{
				// handle the case where ReadByteleft<1024 bytes, read and return immediately

				// copy data from openfile to localBuffer
				readByte = openfile.read(localBuffer, 0, readByteleft);

				if(readByte==-1){
					Lib.debug(dbgProcess, "read file out of bound");
					return -1;
				}

				writeByte = writeVirtualMemory(bufferAddr + currentBytes, localBuffer,0,readByte);

				if(writeByte < readByte){
					Lib.debug(dbgProcess, "write out of memory");
					return -1;
				}
				currentBytes += writeByte;
				return currentBytes;
			}
		}
		return currentBytes;
	}


	/**
	 *
	 * Handle the write() system call.
	 *
	 * Attempt to write up to count bytes from buffer to the file or stream
	 * referred to by fileDescriptor. write() can return before the bytes are
	 * actually flushed to the file or stream.
	 * A write to a stream can block, however, if kernel queues are temporarily full.
	 *
	 * On success, the number of bytes written is returned (zero indicates nothing
	 * was written), and the file position is advanced by this number.
	 *
	 * It IS an error if this number is smaller than the number of bytes requested.
	 * For disk files, this indicates that the disk is full. For streams, this
	 * indicates the stream was terminated by the remote host before all the data
	 * was transferred.
	 *
	 * On error, -1 is returned, and the new file position is undefined. This can
	 * happen if fileDescriptor is invalid, if part of the buffer is invalid, or
	 * if a network stream has already been terminated by the remote host.
	 *
	 * ###########################################################
	 * similary to handleRead
	 * write data from bufferAddr in virtual memory to file(given fileDescriptor)
	 * ###########################################################
	 */
	int handleWrite(int fileDescriptor, int bufferAddr, int  totalBytes){

		Lib.debug(dbgProcess, "handleWrite()");

		//handle edge case for fileDescriptor, bufferAddr, and count
		if ((fileDescriptor < 0) || (fileDescriptor > 15) ) {
			Lib.debug(dbgProcess, "fileDescriptor out of range (0-15)");
			return -1;
		}
		OpenFile openfile = fileTable[fileDescriptor];
		if(openfile == null){
			Lib.debug(dbgProcess, "no file under this fileDescriptor");
			return -1;
		}
		if(totalBytes<0){
			Lib.debug(dbgProcess, "count is negative");
			return -1;
		}
		if(bufferAddr<0){
			Lib.debug(dbgProcess, "bufferAddr is invalid");
			return -1;
		}

		byte[] localBuffer = new byte[pageSize];	//create a local buf with page size 1024, writing 1024 at each time
		int currentBytes = 0;	//offset of bufferAddr, how many bytes successfully copied from bufferAddr to openfile
		int readByte = 0;		//bytes read from bufferAddr in virtual memory
		int writeByte = 0;		//bytes written into file
		int writenByteLeft;		//bytes left to be write

		// keep track of current writing bytes, write 1024 in max in each round
		while(true){

			writenByteLeft = totalBytes - currentBytes;
			if(writenByteLeft <= 0){
				break;
			}

			if( writenByteLeft >= pageSize){

				//read bytes from user virtual memory to kernel localBuffer, read 1024 bytes in max
				readByte = readVirtualMemory(bufferAddr + currentBytes, localBuffer,0, pageSize);

				// EX writenByteLeft=2000, readByte = 500, write 500 into file

				if(readByte < pageSize){
					Lib.debug(dbgProcess, "read out of memory");
					return -1;
				}

				// copy data from localBuffer to openfile
				writeByte = openfile.write(localBuffer,0, readByte);

				if(writeByte == -1){
					// disk is full OR stream terminate
					Lib.debug(dbgProcess, "write file unsuccessfully");
					return - 1;
				}
				currentBytes += writeByte;
			}
			else{	// writenByteLeft < 1024

				readByte = readVirtualMemory(bufferAddr + currentBytes, localBuffer, 0, writenByteLeft);

				if(readByte < writenByteLeft){
					Lib.debug(dbgProcess, "read out of memory");
					return -1;
				}

				// copy data from openfile to localBuffer
				writeByte = openfile.write(localBuffer,0, readByte);

				if(writeByte == -1){
					// disk is full OR stream terminate
					Lib.debug(dbgProcess, "write file unsuccessfully");
					return - 1;
				}
				currentBytes += writeByte;
			}
		}
		return currentBytes;
	}

	/**
	 * Handle the close() system call.
	 *
	 * Close a file descriptor, so that it no longer refers to any file or
	 * stream and may be reused. The resources associated with the file
	 * descriptor are released.
	 *
	 * Returns 0 on success, or -1 if an error occurred.
	 */
	private int handleClose(int fileDescriptor){

		Lib.debug(dbgProcess, "handleClose()");

		if ((fileDescriptor < 0) || (fileDescriptor > 15) ) {
			Lib.debug(dbgProcess, "fileDescriptor out of range (0-15)");
			return -1;
		}

		if(fileTable[fileDescriptor] == null){
			Lib.debug(dbgProcess, "fileDescriptor is null");
			return -1;
		}
		fileTable[fileDescriptor].close();
		fileTable[fileDescriptor] = null;
		return 0;
	}

	/**
	 * Handle the unlink() system call.
	 *
	 * Delete a file from the file system.
	 *
	 * If another process has the file open, the underlying file system
	 * implementation in StubFileSystem will cleanly handle this situation
	 * (this process will ask the file system to remove the file, but the
	 * file will not actually be deleted by the file system until all
	 * other processes are done with the file).
	 *
	 * Returns 0 on success, or -1 if an error occurred.
	 */
	private int handleUnlink(int virtualAddr){

		Lib.debug(dbgProcess, "handleUnlink()");

		// Check for valid virtual addr
		if (virtualAddr < 0){
			Lib.debug(dbgProcess, "virtualAddr is invalid");
			return -1;
		}

		//get file name
		String filename = readVirtualMemoryString(virtualAddr,  maxFileLen);
		if(filename == null){
			Lib.debug(dbgProcess, "can not find the filename given virtualAddr");
			return -1;
		}

		// delete the file by calling fileSystem API
		boolean succeed = ThreadedKernel.fileSystem.remove(filename);
		if(!succeed){
			return -1;
		}
		return 0; //successfully deleted
	}



	//###########################################################################
	//PA2 part3
	// Implement the PROCESS MANAGEMENT SYSCALLS: exec(), join(), exit(), halt()
	//###########################################################################
	/**
	 * handle the exec() system call
	 *
	 * Execute the program stored in the specified file, with the specified
	 * arguments, in a new child process. The child process has a new unique
	 * process ID, and starts with stdin opened as file descriptor 0, and stdout
	 * opened as file descriptor 1.
	 *
	 * file is a null-terminated string that specifies the name of the file
	 * containing the executable. Note that this string must include the ".coff"
	 * extension.
	 *
	 * argc specifies the number of arguments to pass to the child process. This
	 * number must be non-negative.
	 *
	 * argv is an array of pointers to null-terminated strings that represent the
	 * arguments to pass to the child process. argv[0] points to the first
	 * argument, and argv[argc-1] points to the last argument.
	 *
	 * exec() returns the child process's process ID, which can be passed to
	 * join(). On error, returns -1.
	 *
	 */
	int handleExec(int coffName, int argc, int argv){

		Lib.debug(dbgProcess, "UserProcess.handleExec (" + coffName +","+ argc +","+ argv + ")");

		if (coffName < 0){
			Lib.debug(dbgProcess, "file virtualAddr is invalid");
			return -1;
		}

		//#######################################
		//1	Read coffName from virtual address
		String filename = readVirtualMemoryString(coffName, 256);
		if (filename == null){
			Lib.debug(dbgProcess, "can not find the filename given virtualAddr");
			return -1;
		}

		if(argc < 0){
			Lib.debug(dbgProcess, "missing argc");
			return -1;
		}

		//#######################################
		//2. Read argvs from virtual address
		String[] argvList = new String[argc];	//allocate a list of args

		//read virtual memo string
		for(int index=0; index< argc; index++){

			byte[] argAddrInBytes = new byte[byteSize];	//one pointer is 4 bytes == 32 bits

			//read 4 bytes argument addr pointer from (argv+index*byteSize)
			// argv is an array of pointers to null-terminated strings that represent
			// the arguments to pass to the child process
			int readByte = readVirtualMemory((argv+index*byteSize), argAddrInBytes);

			if(readByte < (byteSize) ){
				Lib.debug(dbgProcess, "fail to read arg virtual addr");
				return -1;
			}
			int argAddr = Lib.bytesToInt(argAddrInBytes, 0);	// convert byte to int with nachos kernel support

			//read argument from argAddr
			String argument = readVirtualMemoryString(argAddr, maxFileLen);

			if(argument==null){
				Lib.debug(dbgProcess, "fail to read argument");
				return -1;
			}

			argvList[index] = argument;
		}


		//#######################################
		//3. Execute user program in a new child process - newUserProcess()

		// newUserProcess(): Allocate and return a new process of the correct class.
		UserProcess childProcess = newUserProcess();	//if success, assign new PID to child process
												// also, increment numOfProcesses and processIDGenerator by 1

		// execute(): Execute the specified program with the specified arguments.
		//	Attempts to load the program, and then forks a thread to run it.
		// 	boolean execute(String name, String[] args)
		boolean success = childProcess.execute(filename, argvList);
		if (!success){
			Lib.debug(dbgProcess, "fail to exec child processs");

			UserKernel.lock.acquire();
			UserKernel.numOfProcesses--;	// fail to execute child process
											// but newUserProcess() already increment numOfProcesses by 1
			//processIDGenerator dont decrease, skip this pid if failed
			UserKernel.lock.release();
			return -1;
		}

		//#######################################
		//4. Save details of child in parent and details of parent in child
		// create a two-way mapping
		childProcess.parentProcess = this;
		childProcessMap.put(childProcess.processID, childProcess);
		// so far childStatusMap is empty

		//#######################################
		//5. Return child PID
		return childProcess.processID;
	}

	/**
	 * handle the join() system call
	 * join works whether or not child has finished by the time the parent calls join;
	 * can only join to a direct child of the parent, otherwise return an error;
	 * join returns 0 if the child terminates due to an exception.
	 *#################################################################################
	 *
	 * Suspend execution of the current process until the child process specified
	 * (int file, int argc, int argv) by the processID argument has exited.
	 *
	 * If the child has already exited by the stime of the call, returns immediately.
	 *
	 * When the current process resumes, it disowns the child process,
	 * so that join() cannot be used on that process again.
	 *
	 * processID is the process ID of the child process, returned by exec().
	 *
	 * status points to an integer where the exit status of the child process will
	 * be stored. This is the value the child passed to exit(). If the child exited
	 * because of an unhandled exception, the value stored is not defined.
	 *
	 * If the child exited normally, returns 1.
	 * If the child exited as a result of an unhandled exception, returns 0.
	 * If processID does not refer to a child process of the current process, returns -1.
	 */
	int handleJoin(int childPID, int status){

		Lib.debug(dbgProcess, "UserProcess.handleJoin (" + childPID +","+ status + ")");

		if (!childProcessMap.containsKey(childPID)){
			Lib.debug(dbgProcess, "processID does not refer to a child process of the current process");
			return -1;
		}

		//case1: child already exit
		//1.1 exit abnormally
		if(abnormalChildSet.contains(childPID)){
			Lib.debug(dbgProcess, "the child exited as a result of an unhandled exception");
			return 0;
		}

		//1.2 exit normally
		if(childStatusMap.containsKey(childPID)){
			//Get and set child status
			int childStatus = childStatusMap.get(processID);	//get child status
			byte[] childStatusInInt = Lib.bytesFromInt(childStatus);
			writeVirtualMemory(status, childStatusInInt);		//set child status

			return 1;
		}

		//case2: child not exit yet, wait until child exit

		//curr process sleep on child, Suspend execution until child completes
		UserProcess child = childProcessMap.get(childPID);
		child.cvLock.acquire();
		child.childCompletedCV.sleep();	//put curr process to sleep, wait until child completed
		child.cvLock.release();

		//2.1child exit abnormally
		if(abnormalChildSet.contains(childPID)){
			Lib.debug(dbgProcess, "the child exited as a result of an unhandled exception");
			return 0;
		}

		//2.2. child process exit Normally,  set status
		// Get child status of this process AND write it to argument status(virtual addr)
		int childStatus = childStatusMap.get(processID);	//get child status
		byte[] childStatusInInt = Lib.bytesFromInt(childStatus);
		writeVirtualMemory(status, childStatusInInt);		//set to status in virtual memory

		return 1;	//the child exited normally
	}

	/**
	 * Handle the exit() system call.
	 *
	 * Terminate the current process immediately. Any open file descriptors
	 * belonging to the process are closed. Any children of the process no longer
	 * have a parent process.
	 *
	 * status is returned to the parent process as this process's exit status and
	 * can be collected using the join syscall.
	 * #######################################
	 * (it does not return literally, but let the parent process know it exit,
	 * normally OR abnormally)
	 *  #######################################
	 * A process exiting normally should (but is not required to) set status to 0.
	 *
	 * exit() never returns.
	 */

	// FAIL : +1.67 : 56.67 : exit2: Test if process is freeing pages correctly on exit

	//FAIL : +1.67 : 60.83 : exit5: Test if exit status of child is returned to parent via join - multiple children

	//FAIL : +1.67 : 63.33 : join3: Call join on child's child which counts as joining on a process that is not a child of the current process

	private int handleExit(int status) {

		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		System.out.println("Proccess["+ processID +"]Exit status(handleExit) " + status);
		// Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		//#######################################################
		//1. Close all files in file table
		for(int i = 0; i < fileTable.length; i++){
			if(fileTable[i] != null){
				fileTable[i].close();	// Close this file and release any associated system resources
				fileTable[i] = null;
			}
		}

		//2. Delete all memory by calling UnloadSections()
		unloadSections();

		//3. Close the coff by calling coff.close()
		coff.close();

		//4. If it has a parent process, save the status for parent
		if(parentProcess!=null){

			if(isNormal){
				// 	When the child process exit, create a new entry of the its pid and its status
				// add to the childStatusMap in its parent
				//	If a child process terminates abnormally (e.g., due to an unhandled
				//exception), it will not have an exit status. In this case, join will return 0
				parentProcess.childStatusMap.put(processID,status);
			}
			else{
				parentProcess.abnormalChildSet.add(processID);
			}

			//5. Wake up parent if sleeping
			cvLock.acquire();
			childCompletedCV.wake();
			cvLock.release();
		}

		//6. In case of last process, call kernel.kernel.terminate()
		//		critical section to access numOfProcesses in UserKernel
		UserKernel.lock.acquire();
		if(UserKernel.numOfProcesses == 1){	//last process
			UserKernel.numOfProcesses--;
			Kernel.kernel.terminate();
		}
		else{
			UserKernel.numOfProcesses--;
		}
		UserKernel.lock.release();

		//7. Close KThread by calling KThread.finish()
		KThread.finish();

		//#######################################################
		//original code:
		// for now, unconditionally terminate with just one process
		//	Kernel.kernel.terminate();

		return 0;//handle exit never return , so it doest matter what value we return
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {

		//########################################
		// Part3: Extend the implementation of the halt system call so that it can
		// only be invoked by the "root" process that is, the initial process in the system.
		if(processID != 0){
			Lib.debug(dbgProcess, "not the root process, can not call halt()");
			return 0;
		}
		//########################################

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}



	//###########################################################################

	private static final int
	syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
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
	 * @param a0 the first syscall argument.	virtual address refer to fileName OR a filedescriptor
	 * @param a1 the second syscall argument.	buffer address
	 * @param a2 the third syscall argument.	count of bytes
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();

		//######################################################
		//part 1: Implement the file system calls creat, open, read, write, close, and unlink
		//######################################################
		case syscallCreate:
			return handleCreate(a0);		// pass in va of filename
		case syscallOpen:
			return handleOpen(a0);			// pass in va of filename
		case syscallRead:
			return handleRead(a0, a1, a2);	// pass in a file descriptor, buffer addr, bytes to read
		case syscallWrite:
			return handleWrite(a0, a1, a2); // pass in a file descriptor, buffer addr, bytes to write
		case syscallClose:
			return handleClose(a0);			// pass in a file descriptor
		case syscallUnlink:
			return handleUnlink(a0);		// pass in va of filename


		//######################################################
		//part 3: Implement the process system calls exit(), join(), exec()
		//######################################################
		case syscallExec:
			return handleExec(a0, a1, a2);	//pass in va of filename, argc and argv
		case syscallJoin:
			return handleJoin(a0, a1);		//pass in pid, and status
		case syscallExit:
			return handleExit(a0);			//pass in status
		//######################################################

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
			//####################################################
			//part3
			isNormal = false;
			handleExit(-1);
			//####################################################

			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
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

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	//#############################################################
	//part 1

	private OpenFile[] fileTable;

	private static final int maxFileNum = 16;

	private static final int maxFileLen = 256;
	//#############################################################
	//part 3
	private static final int byteSize = 4;

	private static int processID;	//globally unique positive integer

	private static HashMap<Integer,UserProcess> childProcessMap;	//key=child processID; value=child process

	private static UserProcess parentProcess;

	private HashMap<Integer, Integer> childStatusMap;	//mapping child PID to child status

	//for join() to check if a child process is completed
	//lock and cv are shared by parent and child processes, thus set to public
	public Condition2 childCompletedCV;
	public Lock cvLock;

	private boolean isNormal;	//to handle if child process terminate abnormally

	private HashSet<Integer> abnormalChildSet;	//store the child PID

	//#############################################################
}
