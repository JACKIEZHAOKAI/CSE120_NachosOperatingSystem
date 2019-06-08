package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.*;

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

		//####################################################################
		// PA3 part 2
		//####################################################################
		//@@@ create a file to store swapped pages on VMKernel init, assume unlimited file size!!!
		swapFile = ThreadedKernel.fileSystem.open("swapFile", true);

		invertedPageTable = new Info[Machine.processor().getNumPhysPages()];
		for(int i = 0; i < Machine.processor().getNumPhysPages(); i++){
			invertedPageTable[i] = new Info(null, false);	//init pin to be false
		}
		victim = 0;
		countPin = 0;

		pinnedCVLock = new Lock();
		pinnedCV = new Condition2(pinnedCVLock);

		spnGenerator = 0;
		freeSwapPageList = new LinkedList<>();
		//####################################################################
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

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	//####################################################################
	// PA3 part 2
	//####################################################################
	public	static OpenFile swapFile; 	// to store dirty pages, manage swap file using @@@ to commnet

	public class Info {
		public boolean pin;				//which page is pinned
		public TranslationEntry entry;

		public Info(TranslationEntry entry, boolean pin) {
			this.entry = entry;
			this.pin = pin;
		}
	}

	public static Info invertedPageTable[];	//map ppn to an Info object, same length of phy memory!!!
											//index from 0 to (phy size -1)

	public static int victim;				//victim last time

	//################################
	public static int countPin;				//count the number of pinned pages in phy memory

	public static Lock pinnedCVLock;

	public static Condition2 pinnedCV;		//if all phy pages are pinned, call pinnedCV.sleep()

	//################################

	public static LinkedList<Integer> freeSwapPageList; //record available pages in swapFile, store spn (swap page number)

	public static int spnGenerator;
}

