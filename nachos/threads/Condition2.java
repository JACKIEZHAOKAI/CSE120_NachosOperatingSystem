package nachos.threads;

import nachos.machine.*;
import java.util.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {

	private Lock conditionLock;			// user level lock associated with the CV,
										// lock is a reference passed in by user in the CV constructor
	private Queue<KThread> CVwaitQueue;	// adding a wait Queue inside CV to store wait thread if the lock is held

	/**###########################################################################
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {

		this.conditionLock = conditionLock;		// lock passed in by user
		//	Create and initialize a Queue using a LinkedList, java queue inherit from LinkedList
		this.CVwaitQueue =  new LinkedList<KThread>();
	}

	/**###########################################################################
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>.
	 * The current thread must hold the associated lock.
	 * The thread will automatically reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		//if a thread calls any of the synchronization methods without holding the lock, Nachos asserts
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		//release lock before blocking curr thread, to prevent deadlock
		conditionLock.release();

		//##########################################
		//critical section in kernel, accessing CVwaitQueue, atomic execution in kernel
		//disable interrupt to prevent context switch
		boolean status = Machine.interrupt().disable();

		System.out.println("put " + KThread.currentThread().getName() + " to sleep");

		//add curr thread to wait queue of the CV
		CVwaitQueue.add(KThread.currentThread());
		//block the thread
		KThread.sleep();

		Machine.interrupt().restore(status);
		//##########################################

		//acquire the lock again until it is unblocked
		conditionLock.acquire();
	}

	/**###########################################################################
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		//if a thread calls any of the synchronization methods without holding the lock, Nachos asserts
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

//		//disable interrupt to prevent context switch, otherwise might cause deadlock
		boolean status = Machine.interrupt().disable();

		//##########################################
		if (!CVwaitQueue.isEmpty()){

			//wake first thread is the queue
			//LinkedList.remove(): The element is removed from the beginning or head of the linked list.
			KThread firstKThread = CVwaitQueue.remove();

			// check if the thread called sleepFor() before, if so, the thread has been added into the PQ in alarm
			// otherwise, just wake the thread.
			boolean threadInPQ = ThreadedKernel.alarm.cancel(KThread.currentThread());
			if (!threadInPQ){
				//interrupt must be disabled before calling ready()
				firstKThread.ready();
			}
		}
		Machine.interrupt().restore(status);
		//##########################################
	}

	/**###########################################################################
	/**###########################################################################
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		// ###############################################################
		//use a while loop to keep calling wake
		while (!CVwaitQueue.isEmpty()){
			wake();
		}
		// ###############################################################
	}


	/**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses.  The current thread must hold the
	 * associated lock.  The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 *
	 *
	 * With sleepFor(x) , a thread is woken up and
	 * returns either because another thread has called wake as with sleep ,
	 * or the timeout x has expired.
	 */
	public void sleepFor(long timeout) {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		conditionLock.release();	// to allow other thread to hold the lock when curr thread is blocked

		// ###############################################################
		//	Put the current thread to sleep for at least timeout ticks,
		//	waking it up in the timer interrupt handler when timeout x has expired.
		ThreadedKernel.alarm.waitUntil(timeout);

		// add in the current thread to the waitQueue, so that it can be waken up by calling wake() or wakeAll()
		boolean status = Machine.interrupt().disable();
		CVwaitQueue.add(KThread.currentThread());
		Machine.interrupt().restore(status);
		// ###############################################################

		conditionLock.acquire();
	}



	// ###############################################################
	// Place Condition2 testing code in the Condition2 class.

	// Example of the "interlock" pattern where two threads strictly
	// alternate their execution with each other using a condition
	// variable.  (Also see the slide showing this pattern at the end
	// of Lecture 6.)
	// ###############################################################

	private static class InterlockTest {
		private static Lock lock;
		private static Condition2 cv;

		private static class Interlocker implements Runnable {
			public void run () {
				lock.acquire();
				for (int i = 0; i < 10; i++) {
					System.out.println(KThread.currentThread().getName());
					cv.wake();   // signal
					cv.sleep();  // wait
				}
				lock.release();
			}
		}

		public InterlockTest () {
			lock = new Lock();
			cv = new Condition2(lock);

			KThread ping = new KThread(new Interlocker());
			ping.setName("ping");
			KThread pong = new KThread(new Interlocker());
			pong.setName("pong");

			ping.fork();	// Nachos fork() === Linux exec()
			pong.fork();

			// We need to wait for ping to finish, and the proper way
			// to do so is to join on ping.
			//
			// (Note that, when ping is done, pong is sleeping on the condition variable; if we
			// were also to join on pong, we would block forever.)

			// For this to work, join must be implemented.  If you
			// have not implemented join yet, then comment out the
			// call to join and instead uncomment the loop with
			// yields; the loop has the same effect, but is a kludgy
			// way to do it.
			ping.join();
			// for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
		}
	}


	// Place Condition2 test code inside of the Condition2 class.

	// Condition and Condition2 classes.  You can first try a test with
	// Condition, which is already provided for you, and then try it
	// with Condition2, which you are implementing, and compare their
	// behavior.

	// Do not use this test program as your first Condition2 test.
	// First test it with more basic test programs to verify specific
	// functionality.

	public static void cvTest5() {
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
		final Condition2 empty = new Condition2(lock);
		final LinkedList<Integer> list = new LinkedList<>();

		KThread consumer = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				while(list.isEmpty()){
					empty.sleep();
				}
				Lib.assertTrue(list.size() == 5, "List should have 5 values.");
				while(!list.isEmpty()) {
					// context swith for the fun of it
					KThread.currentThread().yield();
					System.out.println("Removed " + list.removeFirst());
				}
				lock.release();
			}
		});

		KThread producer = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				for (int i = 0; i < 5; i++) {
					list.add(i);
					System.out.println("Added " + i);
					// context swith for the fun of it
					KThread.currentThread().yield();
				}
				empty.wake();
				lock.release();
			}
		});

		consumer.setName("Consumer");
		producer.setName("Producer");
		consumer.fork();
		producer.fork();

		// We need to wait for the consumer and producer to finish,
		// and the proper way to do so is to join on them.  For this
		// to work, join must be implemented.  If you have not
		// implemented join yet, then comment out the calls to join
		// and instead uncomment the loop with yield; the loop has the
		// same effect, but is a kludgy way to do it.
		consumer.join();
		producer.join();
		//for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
	}



	// Place sleepFor test code inside of the Condition2 class.

	private static void sleepForTest1 () {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		lock.acquire();
		long t0 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() + " sleeping for 2000 ticks");
		// no other thread will wake us up, so we should time out
		cv.sleepFor(2000);
		long t1 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() +
				" woke up, slept for " + (t1 - t0) + " ticks");
		lock.release();
	}


	// Invoke Condition2.selfTest() from ThreadedKernel.selfTest()

	public static void selfTest() {
		System.out.println("\n#########  Start testing part3 in Condition2 #########");

		new InterlockTest();

		cvTest5();

		System.out.println("\n######### Start testing part4 Sleepfor() in Condition2 #########");

		sleepForTest1();

		System.out.println("######### All Condition2 Tests Done #########");

	}

}
