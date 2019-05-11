package nachos.threads;

import nachos.machine.*;
import java.util.PriorityQueue;
import java.util.Comparator;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 *	 * ########################################################################
 * 	 * PA 1 part 1
 * 	 * ########################################################################
 *
 * 	 Complete the implementation of the Alarm class (except for cancel ,
 * which you will implement later) . A thread calls waitUntil(long x) to
 * suspend its execution until wall-clock time has advanced to at least now + x.
 *
 * There is no requirement that threads start running
 * immediately after waking up; just put them on the ready queue in the timer
 * interrupt handler after they have waited for at least the right amount of time.
 *
 * you need only modify waitUntil and the timer interrupt handler methods.
 */

//  Alarm is accessible to all the threads
public class Alarm {

	public PriorityQueue<AlarmThread>  PQ;
	// create a PQ to hold thread and its time to wake, this structure is accessible to all threads

	/**########################################################################
	 *	create a class AlarmThread to hold the Kthread and the time to wake the thread
	 */
	class AlarmThread{
		KThread thread;
		long wakeTime;

		AlarmThread(KThread k, long t){
			thread = k;
			wakeTime = t;
		}
	}

	/**#########################################################################
	 *	redefine the compare method in readyQueue
	 */
	class alarmComparator implements Comparator<AlarmThread>{

		public int compare(AlarmThread at1, AlarmThread at2){
			if(at1.wakeTime > at2.wakeTime){
				return 1;
			}
			else if(at1.wakeTime < at2.wakeTime){
				return -1;
			}
			else{
				return 0;		// if two time equals
			}
		}
	}

	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 *
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {

		//create an readyQueue instance
		PQ = new PriorityQueue<AlarmThread>( new alarmComparator()); // default capacity =11

		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/** #######################################################
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 *
	 * ########################################################
	 * 	 * key idea: use the information that was stored from waitUnitl()
	 * 	 to check if curr time is past that wake up time,
	 * 	 if so, unblock that thread by calling ready(),
	 * 	 move it from block/wait fstate to ready Queue.
	 *
	 */
	public void timerInterrupt() {
		//####################################################
		boolean status = Machine.interrupt().disabled();
		// disable interrupt and enable interrupt to guard the critical section
		// otherwise other threads may change the structure

		//check the PQ here and pop and evoke the thread in PQ if time is up
		while(!PQ.isEmpty()){

			AlarmThread threadToCheck =  PQ.peek();

			if(threadToCheck.wakeTime <= Machine.timer().getTime()){

				//evoke the thread by adding to readyQueue
				threadToCheck.thread.ready();

				//pop the AlarmThread from PQ
				PQ.poll();
			}
			else{
				// if the wakeTime of peek thread in PQ is more than curr time
				// then rest the wakeTime of other threads in PQ must be more than curr time
				break;
			}

		}
		Machine.interrupt().restore(status);

		KThread.currentThread().yield();  // curr thread call yield() to voluntarily give up CPU, prevent
	}

	/** ########################################################
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 *
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 *
	 * @param x the minimum number of clock ticks to wait.
	 *
	 * @see nachos.machine.Timer#getTime()
	 *
	 * ########################################################
	 * key idea: calculate wake up time and store the information
	 * avoid  busy waiting (the waiting thread to occupy CPU resources)
	 * 1	compute waketime of current  thread
	 * 2	push curr thread to wait quuee(PQ)
	 * 3	call sleep() to move to blocked/wait state , so that the thread will not be allocated
	 * 		in readyQueue until the time is up and other thread call ready() on it
	 */
	public void waitUntil(long x) {

		// If the wait parameter x is 0 or negative, return without waiting (do not assert).
		if(x <= 0){
			return;
		}

		long wakeTime = Machine.timer().getTime() + x;

		if(wakeTime > Machine.timer().getTime()){

			AlarmThread threadToWait = new AlarmThread(KThread.currentThread(), wakeTime);

			// ### an important idea is to disable interrupt and enable interrupt to guard the critical section
			// which is accessing the shared data structure Priority Queue, similar to lock and unlock
			//##################################################
			boolean status = Machine.interrupt().disable();

			PQ.add(threadToWait);
			KThread.sleep();

			Machine.interrupt().restore(status);

			//######## original code ###############
			//for now, cheat just to get something working (busy waiting is bad)
			/** busy waiting: put curr thread to ready queue evey time calling gettime() to check if
			 * it the time to run the thread, otherwise calling yield to put the thread to readyqueue and
			 * let the other thread from readyQueue to run
			 * yield() voluntarily give up on CPU, added to readyQueue and run next thread in readyQueue
			 */
			//while(wakeTime > Machine.timer().getTime())
			//		KThread.yield(); 	// yield() voluntarily give up on CPU, added to readyQueue and run next thread in readyQueue
			// but CPU scheduler may run the thread later
		}

	}

	/**##################################################
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 *
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 *
	 *  Curr problem: cancel can not be tested individually,
	 *
	 */

	public boolean cancel(KThread thread){
		boolean hasTimeSet = false;
//		boolean status = Machine.interrupt().disabled();
		if (PQ.contains(thread)){
			PQ.remove(thread);
			thread.ready();
			hasTimeSet = true;
		}
//		Machine.interrupt().restore(status);
		return hasTimeSet;
	}

	// Add Alarm testing code to the Alarm class
	public static void alarmTest1() {
		System.out.println("\n#########  Start Alarm tests  #########");
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;

		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (d);
			t1 = Machine.timer().getTime();
			System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}

	public static class alarmTest2 implements Runnable {
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;
		int name;
		alarmTest2(int input){
			name = input;
		}
		public void run() {
			System.out.println ("want actual waiting time > time passed to waitUnitl() ");

			for (int d : durations) {
				t0 = Machine.timer().getTime();
				ThreadedKernel.alarm.waitUntil (d);
				t1 = Machine.timer().getTime();
				System.out.println("d (time passed to waitUntil() )" + d);
				System.out.println("t0 (time before calling waitUntil() ): " + t0);
				System.out.println("t1 (time after calling waitUntil() ):: " + t1);
				System.out.println (name + "alarmTest1: waited for " + (t1 - t0) + " ticks\n");
			}
			System.out.println("\n#########  Alarm tests Done #########");
		}
	}

	// Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
	public static void selfTest() {

		// Invoke your other test methods here ...
		alarmTest1();

		alarmTest2 test2 = new alarmTest2(999);
		new KThread(test2).fork();	//move from new state to ready state
		// fork
		new alarmTest2(11).run();
	}

}