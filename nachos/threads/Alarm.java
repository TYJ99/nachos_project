package nachos.threads;

import nachos.machine.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.List;
import java.util.LinkedList;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	private Map<KThread, Long> waitUntilMap = new HashMap<>();
	private Queue<KThread> wakeQueue;

	public Alarm() {

		wakeQueue = new PriorityQueue<>(new Comparator<KThread>() {

			@Override
			public int compare(KThread t1, KThread t2) {
				if (t1.wakeTime > t2.wakeTime) {
					return 1;
				} else if (t1.wakeTime < t2.wakeTime) {
					return -1;
				}
				return 0;
			}

		});

		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */

	public void timerInterrupt() {
		Lib.debug('t', "\nenter timer Interrupt\n");
		boolean intStatus = Machine.interrupt().disable();
		long currentTime = Machine.timer().getTime();
		// System.out.println("currTime: " + currentTime);
		Lib.debug('t', "curr" + currentTime);
		// System.out.println("wakeQueue: " + wakeQueue);
		while (!wakeQueue.isEmpty() && currentTime >= wakeQueue.peek().wakeTime) {
			wakeQueue.poll().ready();
		}

		Lib.debug('t', "ready to yield");

		Machine.interrupt().restore(intStatus);
		KThread.yield();
	}

	// public void timerInterrupt() {
	// Lib.debug('t', "\nenter timer Interrupt\n");
	// boolean intStatus = Machine.interrupt().disable();
	// long currentTime = Machine.timer().getTime();
	// // System.out.println("currTime: " + currentTime);
	// Lib.debug('t', "curr" + currentTime);
	// for (Iterator<Map.Entry<KThread, Long>> it =
	// waitUntilMap.entrySet().iterator(); it.hasNext();) {
	// Map.Entry<KThread, Long> set = it.next();
	// if (set.getValue() <= currentTime) {
	// set.getKey().ready();
	// // System.out.println(set.getValue());
	// Lib.debug('t', "before: " + waitUntilMap.size());
	// it.remove();
	// Lib.debug('t', "after: " + waitUntilMap.size());
	// }
	// }
	// Lib.debug('t', "ready to yield");
	// KThread.yield();

	// Machine.interrupt().restore(intStatus);
	// }

	/**
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
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		boolean intStatus = Machine.interrupt().disable();

		if (x <= 0) {
			Machine.interrupt().restore(intStatus);
			return;
		} else {
			KThread.currentThread().wakeTime = Machine.timer().getTime() + x;
			wakeQueue.offer(KThread.currentThread());
			KThread.sleep();
		}

		Machine.interrupt().restore(intStatus);

		/*
		 * while (wakeTime > Machine.timer().getTime())
		 * KThread.yield();
		 */
	}

	// public void waitUntil(long x) {
	// // for now, cheat just to get something working (busy waiting is bad)
	// boolean intStatus = Machine.interrupt().disable();

	// if (x <= 0) {
	// Machine.interrupt().restore(intStatus);
	// return;
	// } else {
	// long wakeTime = Machine.timer().getTime() + x;
	// // System.out.println("wakeTime: " + wakeTime);
	// waitUntilMap.put(KThread.currentThread(), wakeTime);
	// KThread.sleep();
	// }

	// Machine.interrupt().restore(intStatus);

	// /*
	// * while (wakeTime > Machine.timer().getTime())
	// * KThread.yield();
	// */
	// }

	/**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true. If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * 
	 * @param thread the thread whose timer should be cancelled.
	 */

	public boolean cancel(KThread thread) {
		boolean intStatus = Machine.interrupt().disable();

		boolean res = false;
		if (wakeQueue.remove(thread)) {
			res = true;
			thread.ready();
		}

		Machine.interrupt().restore(intStatus);
		return res;
	}

	// public boolean cancel(KThread thread) {
	// boolean intStatus = Machine.interrupt().disable();

	// boolean res = false;
	// if (waitUntilMap.remove(thread) != null) {
	// res = true;
	// thread.ready();
	// }

	// Machine.interrupt().restore(intStatus);
	// return res;
	// }

	public static void alarmTest1() {
		int durations[] = { 1000, 10 * 1000, 100 * 1000 };
		long t0, t1;

		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(d);
			t1 = Machine.timer().getTime();
			System.out.println("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}

	public static void alarmTest2() {
		int durations[] = { 100 * 1000, 0, 10 * 1000, 1000, 500 };
		long t0, t1;

		for (int d : durations) {
			System.out.println("duration: " + d);
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(d);
			t1 = Machine.timer().getTime();
			System.out.println("alarmTest2: waited for " + (t1 - t0) + " ticks");
		}
	}

	private static class AlarmTest implements Runnable {
		AlarmTest(int which, int duration) {
			this.which = which;
			this.duration = duration;
		}

		private int which;
		private int duration;

		public void run() {
			long t0, t1;
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(duration);
			t1 = Machine.timer().getTime();
			System.out.println("alarmTest" + which + ": waited for " + (t1 - t0) +
					" ticks. Duration: " + duration);
		}
	}

	public static void alarmTest3() {
		int durations[] = { 100 * 1000, 0, 10 * 1000, 1000, 500 };

		// boolean intStatus = Machine.interrupt().disable();
		for (int d : durations) {
			new KThread(new AlarmTest(d, d)).setName("#forked" + d).fork();
		}
		// Machine.interrupt().restore(intStatus);
		// new KThread().run();
		new AlarmTest(0, 50000).run();

	}

	public static void alarmTest4() {
		int durations[] = { 100 * 1000, 0, 10 * 1000, 1000, 500 };

		// boolean intStatus = Machine.interrupt().disable();
		for (int d : durations) {
			new KThread(new AlarmTest(d, d)).setName("#forked" + d).fork();
		}
		// Machine.interrupt().restore(intStatus);
		// new KThread().run();
		new AlarmTest(500000, 500000).run();

	}

	public static void selfTest() {
		Lib.debug('t', "Enter Alarm selfTest");
		System.out.println("\n Enter Alarm SelfTest \n");
		// System.out.println("==============================");
		// alarmTest1();
		// System.out.println("==============================");
		// alarmTest2();
		// System.out.println("==============================");
		// alarmTest3();
		// System.out.println("==============================");
		alarmTest4();
	}

}
