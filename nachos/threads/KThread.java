package nachos.threads;

import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import nachos.machine.*;

/**
 * A KThread is a thread that can be used to execute Nachos kernel code. Nachos
 * allows multiple threads to run concurrently.
 * 
 * To create a new thread of execution, first declare a class that implements
 * the <tt>Runnable</tt> interface. That class then implements the <tt>run</tt>
 * method. An instance of the class can then be allocated, passed as an argument
 * when creating <tt>KThread</tt>, and forked. For example, a thread that
 * computes pi could be written as follows:
 * 
 * <p>
 * <blockquote>
 * 
 * <pre>
 * class PiRun implements Runnable {
 * 	public void run() {
 *         // compute pi
 *         ...
 *     }
 * }
 * </pre>
 * 
 * </blockquote>
 * <p>
 * The following code would then create a thread and start it running:
 * 
 * <p>
 * <blockquote>
 * 
 * <pre>
 * PiRun p = new PiRun();
 * new KThread(p).fork();
 * </pre>
 * 
 * </blockquote>
 */
public class KThread {
	/**
	 * Get the current thread.
	 * 
	 * @return the current thread.
	 */
	public static KThread currentThread() {
		Lib.assertTrue(currentThread != null);
		return currentThread;
	}

	/**
	 * Allocate a new <tt>KThread</tt>. If this is the first <tt>KThread</tt>,
	 * create an idle thread as well.
	 */
	public KThread() {
		joinedThreads = new HashSet<>();
		threadCallJoin = null;
		wakeTime = 0;
		if (currentThread != null) {
			Lib.debug(dbgThread, "KThread() and currentThread != null");
			tcb = new TCB();
		} else {
			Lib.debug(dbgThread, "KThread() and currentThread == null");
			readyQueue = ThreadedKernel.scheduler.newThreadQueue(false);
			readyQueue.acquire(this);

			currentThread = this;
			tcb = TCB.currentTCB();
			name = "main";
			restoreState();

			createIdleThread();
		}
	}

	/**
	 * Allocate a new KThread.
	 * 
	 * @param target the object whose <tt>run</tt> method is called.
	 */
	public KThread(Runnable target) {
		this();
		this.target = target;
		Lib.debug(dbgThread, "KThread(target)");
	}

	/**
	 * Set the target of this thread.
	 * 
	 * @param target the object whose <tt>run</tt> method is called.
	 * @return this thread.
	 */
	public KThread setTarget(Runnable target) {
		Lib.assertTrue(status == statusNew);

		this.target = target;
		return this;
	}

	/**
	 * Set the name of this thread. This name is used for debugging purposes
	 * only.
	 * 
	 * @param name the name to give to this thread.
	 * @return this thread.
	 */
	public KThread setName(String name) {
		this.name = name;
		return this;
	}

	public int getStatus() {
		return status;
	}

	/**
	 * Get the name of this thread. This name is used for debugging purposes
	 * only.
	 * 
	 * @return the name given to this thread.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the full name of this thread. This includes its name along with its
	 * numerical ID. This name is used for debugging purposes only.
	 * 
	 * @return the full name given to this thread.
	 */
	public String toString() {
		return (name + " (#" + id + ")");
	}

	/**
	 * Deterministically and consistently compare this thread to another thread.
	 */
	public int compareTo(Object o) {
		KThread thread = (KThread) o;

		if (id < thread.id)
			return -1;
		else if (id > thread.id)
			return 1;
		else
			return 0;
	}

	/**
	 * Causes this thread to begin execution. The result is that two threads are
	 * running concurrently: the current thread (which returns from the call to
	 * the <tt>fork</tt> method) and the other thread (which executes its
	 * target's <tt>run</tt> method).
	 */
	public void fork() {
		Lib.assertTrue(status == statusNew);
		Lib.assertTrue(target != null);

		Lib.debug(dbgThread, "Forking thread: " + toString() + " Runnable: "
				+ target);

		boolean intStatus = Machine.interrupt().disable();

		tcb.start(new Runnable() {
			public void run() {
				runThread();
			}
		});

		ready();

		Machine.interrupt().restore(intStatus);
	}

	private void runThread() {
		begin();
		target.run();
		finish();
	}

	private void begin() {
		Lib.debug(dbgThread, "Beginning thread: " + toString());

		Lib.assertTrue(this == currentThread);

		restoreState();

		Machine.interrupt().enable();
	}

	/**
	 * Finish the current thread and schedule it to be destroyed when it is safe
	 * to do so. This method is automatically called when a thread's
	 * <tt>run</tt> method returns, but it may also be called directly.
	 * 
	 * The current thread cannot be immediately destroyed because its stack and
	 * other execution state are still in use. Instead, this thread will be
	 * destroyed automatically by the next thread to run, when it is safe to
	 * delete this thread.
	 */
	public static void finish() {
		Lib.debug(dbgThread, "Finishing thread: " + currentThread.toString());
		Lib.debug('a', "Finishing thread: " + currentThread.toString());

		Machine.interrupt().disable();

		if (currentThread.threadCallJoin != null) {
			currentThread.threadCallJoin.ready();
			currentThread.threadCallJoin = null;
		}

		Machine.autoGrader().finishingCurrentThread();

		Lib.assertTrue(toBeDestroyed == null);
		toBeDestroyed = currentThread;

		currentThread.status = statusFinished;
		Lib.debug('a', "finish Finishing thread: " + currentThread.toString());
		sleep();
	}

	/**
	 * Relinquish the CPU if any other thread is ready to run. If so, put the
	 * current thread on the ready queue, so that it will eventually be
	 * rescheuled.
	 * 
	 * <p>
	 * Returns immediately if no other thread is ready to run. Otherwise returns
	 * when the current thread is chosen to run again by
	 * <tt>readyQueue.nextThread()</tt>.
	 * 
	 * <p>
	 * Interrupts are disabled, so that the current thread can atomically add
	 * itself to the ready queue and switch to the next thread. On return,
	 * restores interrupts to the previous state, in case <tt>yield()</tt> was
	 * called with interrupts disabled.
	 */
	public static void yield() {
		Lib.debug(dbgThread, "Yielding thread: " + currentThread.toString());

		Lib.assertTrue(currentThread.status == statusRunning);

		boolean intStatus = Machine.interrupt().disable();

		currentThread.ready();

		runNextThread();

		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Relinquish the CPU, because the current thread has either finished or it
	 * is blocked. This thread must be the current thread.
	 * 
	 * <p>
	 * If the current thread is blocked (on a synchronization primitive, i.e. a
	 * <tt>Semaphore</tt>, <tt>Lock</tt>, or <tt>Condition</tt>), eventually
	 * some thread will wake this thread up, putting it back on the ready queue
	 * so that it can be rescheduled. Otherwise, <tt>finish()</tt> should have
	 * scheduled this thread to be destroyed by the next thread to run.
	 */
	public static void sleep() {
		Lib.debug(dbgThread, "Sleeping thread: " + currentThread.toString());

		Lib.assertTrue(Machine.interrupt().disabled());

		if (currentThread.status != statusFinished)
			currentThread.status = statusBlocked;

		runNextThread();
	}

	/**
	 * Moves this thread to the ready state and adds this to the scheduler's
	 * ready queue.
	 */
	public void ready() {
		Lib.debug(dbgThread, "Ready thread: " + toString());
		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(status != statusReady);

		status = statusReady;
		if (this != idleThread)
			readyQueue.waitForAccess(this);

		Machine.autoGrader().readyThread(this);
	}

	/**
	 * Waits for this thread to finish. If this thread is already finished,
	 * return immediately. This method must only be called once; the second call
	 * is not guaranteed to return. This thread must not be the current thread.
	 */
	public void join() {
		Lib.debug(dbgThread, "Joining to thread: " + toString());
		Lib.assertTrue(this != currentThread);
		Lib.assertTrue(threadCallJoin == null);
		Lib.assertTrue(currentThread.joinedThreads.add(this));

		boolean intStatus = Machine.interrupt().disable();

		if (this.status != statusFinished) {
			threadCallJoin = currentThread;
			KThread.sleep(); // let currentThread sleep.
		}

		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Create the idle thread. Whenever there are no threads ready to be run,
	 * and <tt>runNextThread()</tt> is called, it will run the idle thread. The
	 * idle thread must never block, and it will only be allowed to run when all
	 * other threads are blocked.
	 * 
	 * <p>
	 * Note that <tt>ready()</tt> never adds the idle thread to the ready set.
	 */
	private static void createIdleThread() {
		Lib.assertTrue(idleThread == null);

		idleThread = new KThread(new Runnable() {
			public void run() {
				while (true)
					KThread.yield();
			}
		});
		idleThread.setName("idle");

		Machine.autoGrader().setIdleThread(idleThread);

		idleThread.fork();
	}

	/**
	 * Determine the next thread to run, then dispatch the CPU to the thread
	 * using <tt>run()</tt>.
	 */
	private static void runNextThread() {
		KThread nextThread = readyQueue.nextThread();
		if (nextThread == null)
			nextThread = idleThread;

		nextThread.run();
	}

	/**
	 * Dispatch the CPU to this thread. Save the state of the current thread,
	 * switch to the new thread by calling <tt>TCB.contextSwitch()</tt>, and
	 * load the state of the new thread. The new thread becomes the current
	 * thread.
	 * 
	 * <p>
	 * If the new thread and the old thread are the same, this method must still
	 * call <tt>saveState()</tt>, <tt>contextSwitch()</tt>, and
	 * <tt>restoreState()</tt>.
	 * 
	 * <p>
	 * The state of the previously running thread must already have been changed
	 * from running to blocked or ready (depending on whether the thread is
	 * sleeping or yielding).
	 * 
	 * @param finishing <tt>true</tt> if the current thread is finished, and
	 *                  should be destroyed by the new thread.
	 */
	private void run() {
		Lib.assertTrue(Machine.interrupt().disabled());

		Machine.yield();

		currentThread.saveState();

		Lib.debug(dbgThread, "Switching from: " + currentThread.toString()
				+ " to: " + toString());

		currentThread = this;

		tcb.contextSwitch();

		currentThread.restoreState();
	}

	/**
	 * Prepare this thread to be run. Set <tt>status</tt> to
	 * <tt>statusRunning</tt> and check <tt>toBeDestroyed</tt>.
	 */
	protected void restoreState() {
		Lib.debug(dbgThread, "Running thread: " + currentThread.toString());

		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
		Lib.assertTrue(tcb == TCB.currentTCB());

		Machine.autoGrader().runningThread(this);

		status = statusRunning;

		if (toBeDestroyed != null) {
			toBeDestroyed.tcb.destroy();
			toBeDestroyed.tcb = null;
			toBeDestroyed = null;
		}
	}

	/**
	 * Prepare this thread to give up the processor. Kernel threads do not need
	 * to do anything here.
	 */
	protected void saveState() {
		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
	}

	private static class PingTest implements Runnable {
		PingTest(int which) {
			this.which = which;
		}

		public void run() {
			for (int i = 0; i < 5; i++) {
				System.out.println("*** thread " + which + " looped " + i
						+ " times");
				currentThread.yield();
			}
		}

		private int which;
	}

	private static class JoinRunnable implements Runnable {
		private int count = 50000;

		JoinRunnable() {
			count = 50000;
		}

		JoinRunnable(int count) {
			this.count = count;
		}

		public void run() {
			for (int i = 0; i <= count; i++) {
				if (i % 25000 == 0) {
					System.out.println("in thread " + currentThread.getName() + ". i = " + i);
					KThread.yield();
				}
			}
			System.out.println(currentThread.getName() + " finished!!!!");
		}
	}

	private static class JoinRunnable2 implements Runnable {

		public void run() {
			KThread child = new KThread(new JoinRunnable()).setName("child's child");
			child.fork();
			child.join();
			checkJoin(child);
			System.out.println(currentThread.getName() + " finished!!!!");
		}
	}

	// test: if a thread calls join on itself, Nachos asserts.
	private static class JoinRunnable3 implements Runnable {

		public void run() {
			KThread child = new KThread(new JoinRunnable()).setName("child's child");
			child.fork();
			currentThread.join();
			checkJoin(child);
			System.out.println(currentThread.getName() + " finished!!!!");
		}
	}

	// test: if join is called more than once on a thread, Nachos asserts.
	private static class JoinRunnable4 implements Runnable {
		private KThread thread = null;

		JoinRunnable4(KThread thread) {
			this.thread = thread;
		}

		public void run() {
			thread.join();
			checkJoin(thread);
			System.out.println(currentThread.getName() + " finished!!!!");
		}
	}

	private static void checkJoin(KThread joinThread) {
		System.out.println("After joining, " + joinThread.getName() + " should be finished.");
		System.out.println("is it? " + (joinThread.status == statusFinished));
		Lib.assertTrue((joinThread.status == statusFinished), " Expected child1 to be finished.");
	}

	private static void joinTest1() {
		KThread child1 = new KThread(new JoinRunnable()).setName("child1");
		child1.fork();
		KThread child2 = new KThread(new JoinRunnable(250000)).setName("child2");
		child2.fork();

		// We want the child to finish before we call join. Although
		// our solutions to the problems cannot busy wait, our test
		// programs can!

		KThread.yield();

		child1.join();
		System.out.println("After joining, child1 should be finished.");
		System.out.println("is it? " + (child1.status == statusFinished));
		Lib.assertTrue((child1.status == statusFinished), " Expected child1 to be finished.");
	}

	private static void joinTest2() {
		KThread child3 = new KThread(new JoinRunnable()).setName("child3");
		child3.fork();
		KThread child4 = new KThread(new JoinRunnable(250000)).setName("child4");
		child4.fork();
		KThread child5 = new KThread(new JoinRunnable(10000)).setName("child5");
		child5.fork();
		KThread child6 = new KThread(new JoinRunnable(80000)).setName("child6");
		child6.fork();

		// We want the child to finish before we call join. Although
		// our solutions to the problems cannot busy wait, our test
		// programs can!

		// KThread.yield();

		child3.join();
		child4.join();
		child5.join();
		child6.join();
		checkJoin(child3);
		checkJoin(child4);
		checkJoin(child5);
		checkJoin(child6);
	}

	private static void joinTest3() {
		KThread child7 = new KThread(new JoinRunnable()).setName("child7");
		child7.fork();
		KThread child8 = new KThread(new JoinRunnable(250000)).setName("child8");
		child8.fork();
		KThread child9 = new KThread(new JoinRunnable(10000)).setName("child9");
		child9.fork();
		KThread child10 = new KThread(new JoinRunnable(80000)).setName("child10");
		child10.fork();

		// We want the child to finish before we call join. Although
		// our solutions to the problems cannot busy wait, our test
		// programs can!

		KThread.yield();

		child7.join();
		child10.join();
		checkJoin(child7);
		child9.join();
		checkJoin(child9);
		checkJoin(child10);
	}

	private static void joinTest4() {

		KThread child11 = new KThread(new JoinRunnable2()).setName("child11");
		child11.fork();
		KThread child12 = new KThread(new JoinRunnable()).setName("child12");
		child12.fork();

		// We want the child to finish before we call join. Although
		// our solutions to the problems cannot busy wait, our test
		// programs can!
		System.out.println("before yield.");
		KThread.yield();
		System.out.println("after yield.");

		child11.join();
		checkJoin(child11);
	}

	// test: if a thread calls join on itself, Nachos asserts.
	private static void joinTest5() {

		KThread child13 = new KThread(new JoinRunnable3()).setName("child13");
		child13.fork();
		KThread child14 = new KThread(new JoinRunnable()).setName("child14");
		child14.fork();

		// We want the child to finish before we call join. Although
		// our solutions to the problems cannot busy wait, our test
		// programs can!
		System.out.println("before yield.");
		KThread.yield();
		System.out.println("after yield.");

		child13.join();
		checkJoin(child13);
	}

	// test: if join is called more than once on a thread, Nachos asserts.
	private static void joinTest6() {

		KThread child15 = new KThread(new JoinRunnable2()).setName("child15");
		child15.fork();
		KThread child16 = new KThread(new JoinRunnable4(child15)).setName("child16");
		child16.fork();

		// We want the child to finish before we call join. Although
		// our solutions to the problems cannot busy wait, our test
		// programs can!
		System.out.println("before yield.");
		KThread.yield();
		System.out.println("after yield.");

		child15.join();
		child16.join();
		checkJoin(child15);
		checkJoin(child16);
	}

	/**
	 * Tests whether this module is working.
	 */
	public static void selfTest() {
		Lib.debug(dbgThread, "Enter KThread.selfTest");
		System.out.println("\n Enter KThread SelfTest \n");

		new KThread(new PingTest(1)).setName("forked thread").fork();
		new PingTest(0).run();
		// System.out.println("==============================");
		// joinTest1();
		// System.out.println("==============================");
		joinTest2();
		// System.out.println("==============================");
		// joinTest3();
		// System.out.println("==============================");
		// joinTest4();
		// System.out.println("==============================");
		// joinTest5();
		// System.out.println("==============================");
		// joinTest6();

	}

	private static final char dbgThread = 't';

	/**
	 * Additional state used by schedulers.
	 * 
	 * @see nachos.threads.PriorityScheduler.ThreadState
	 */
	public Object schedulingState = null;

	private static final int statusNew = 0;

	private static final int statusReady = 1;

	private static final int statusRunning = 2;

	private static final int statusBlocked = 3;

	private static final int statusFinished = 4;

	/**
	 * The status of this thread. A thread can either be new (not yet forked),
	 * ready (on the ready queue but not running), running, or blocked (not on
	 * the ready queue and not running).
	 */
	private int status = statusNew;

	private String name = "(unnamed thread)";

	private Runnable target;

	private TCB tcb;

	/**
	 * Unique identifer for this thread. Used to deterministically compare
	 * threads.
	 */
	private int id = numCreated++;

	/*
	 * <thread1, thread2>
	 * if B join on A, which is calling A.join() in thread B.
	 * thread1(this): A
	 * thread2(currentThread): B
	 */
	private KThread threadCallJoin = null;
	public long wakeTime;
	private Set<KThread> joinedThreads;
	/** Number of times the KThread constructor was called. */
	private static int numCreated = 0;

	private static ThreadQueue readyQueue = null;

	private static KThread currentThread = null;

	private static KThread toBeDestroyed = null;

	private static KThread idleThread = null;

}
