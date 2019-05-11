package nachos.threads;

import nachos.machine.*;

/**########################################################
 * A <i>SquadMatch</i> groups together player threads of the 
 * three different abilities to play matches with each other.
 * Implement the class <i>SquadMatch</i> using <i>Lock</i> and
 * <i>Condition</i> to synchronize player threads into such groups.
 */

public class SquadMatch {

    private Condition2 condition_warrier;
    private Condition2 condition_wizard;
    private Condition2 condition_thief;

    // since we can not acccess the condiiton var, assume Condition2 is implemented by others
    // condition var provides methods wake() and sleep()
    // so we need to keep track of number of each type of character in CV queue
    private int num_warrier;
    private int num_wizard;
    private int num_thief;
    private int num_square;

    private Lock lock;      //lock the shared var num_warrier, num_wizard, num_thief
//    private Lock lock_wizard;
//    private Lock lock_theif;

    /**
     * Allocate a new SquadMatch for matching players of different
     * abilities into a squad to play a match.
     */
    public SquadMatch () {

        lock = new Lock();
//        lock_wizard = new Lock();
//        lock_theif = new Lock();

        condition_warrier = new Condition2(lock);
        condition_wizard = new Condition2(lock);
        condition_thief = new Condition2(lock);

        num_warrier = 0;
        num_wizard = 0;
        num_thief = 0;
        num_square = 0;
    }

    // sync diff thread using shared var
    /**
     * Wait to form a squad with Wizard and Thief threads, only
     * returning once all three kinds of player threads have called
     * into this SquadMatch.  A squad always has three threads, and
     * can only be formed by three different kinds of threads.  Many
     * matches may be formed over time, but any one player thread can
     * be assigned to only one match.
     */
    public void warrior () {

        lock.acquire();

        //check if have at least one wizard and thief exist in their CV queue
        //if so,  wake thief and wizard from their CV queue
        if (num_wizard != 0 && num_thief != 0){     //condition check

            condition_thief.wake();
            condition_wizard.wake();

            num_wizard -=1;
            num_thief -=1;
            num_square += 1;
            System.out.println("team "+ num_square +" is matched\n");
        }
        else{
            //if not , block current warrier thread
            num_warrier +=1;        // must increment num before blocking the thread
//           System.out.println("put "+ KThread.currentThread().getName() +" to condition_warrier queue")
            condition_warrier.sleep();
        }

        lock.release();
        return;
    }

    /**
     * Wait to form a squad with Warrior and Thief threads, only
     * returning once all three kinds of player threads have called
     * into this SquadMatch.  A squad always has three threads, and
     * can only be formed by three different kinds of threads.  Many
     * matches may be formed over time, but any one player thread can
     * be assigned to only one match.
     */
    public void wizard () {

        lock.acquire();

        if (num_warrier != 0 && num_thief != 0){

            condition_warrier.wake();
            condition_thief.wake();

            num_warrier -=1;
            num_thief -=1;

            num_square += 1;
            System.out.println("team "+ num_square +" is matched\n");
        }
        else{
            num_wizard +=1;
            condition_wizard.sleep();

        }

        lock.release();

        return;
    }

    /**
     * Wait to form a squad with Warrior and Wizard threads, only
     * returning once all three kinds of player threads have called
     * into this SquadMatch.  A squad always has three threads, and
     * can only be formed by three different kinds of threads.  Many
     * matches may be formed over time, but any one player thread can
     * be assigned to only one match.
     */
    public void thief () {

        lock.acquire();

        if (num_warrier != 0 && num_wizard != 0){

            condition_warrier.wake();
            condition_wizard.wake();

            num_warrier -=1;
            num_wizard -=1;

            num_square += 1;
            System.out.println("team "+ num_square +" is matched\n");
        }
        else{
            num_thief +=1;
            condition_thief.sleep();
        }
        lock.release();
        return;
    }


    //##############################################################
    // Place SquadMatch test code inside of the SquadMatch class.
    // only threads that can form a square should be able to run
    // left thread should not run


    public static void squadTest1 () {

        System.out.println("\n#########  Start testing part5 (SquareMatch) #########");
        final SquadMatch match = new SquadMatch();

        // Instantiate the threads
        KThread w1 = new KThread( new Runnable () {
            public void run() {
                match.warrior();
                System.out.println ("w1 matched");
            }
        });
        w1.setName("w1");

        KThread z1 = new KThread( new Runnable () {
            public void run() {
                match.wizard();
                System.out.println ("z1 matched");
            }
        });
        z1.setName("z1");

        KThread t1 = new KThread( new Runnable () {
            public void run() {
                match.thief();
                System.out.println ("t1 matched");
            }
        });
        t1.setName("t1");

        //#######################################
//        // w1, z1 abd t1 should be printed, BUT t2,z2 should not be printed
//        KThread t2 = new KThread( new Runnable () {
//            public void run() {
//                match.thief();
//                System.out.println ("t2 matched");
//            }
//        });
//        t2.setName("t2");
//
//        KThread z2 = new KThread( new Runnable () {
//            public void run() {
//                match.wizard();
//                System.out.println ("z2 matched");
//            }
//        });
//        z2.setName("z2");

        //#######################################

        // Run the threads
        z1.fork();
        t1.fork();
//        t2.fork();      // should not be added to a sqaure
//        z2.fork();      // should not be added to a sqaure
        w1.fork();

        // if you have join implemented, use the following:
        z1.join();
        t1.join();
//        t2.join();      // if comment out then can program will not halt since these two thread never finish
//        z2.join();
        w1.join();

        // if you do not have join implemented, use yield to allow
        // time to pass...10 yields should be enough
//        for (int i = 0; i < 10; i++) {
//            KThread.currentThread().yield();
//        }

        System.out.println("#########  part5 (SquareMatch) Done #########");
    }

    public static void selfTest() {
        squadTest1();
    }

}
