package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;

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
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 *                      The current thread must hold this lock whenever it uses
	 *                      <tt>sleep()</tt>,
	 *                      <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */

	private Lock conditionLock;
	private LinkedList<KThread> waitQueue;

	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
		waitQueue = new LinkedList<KThread>();
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();

		waitQueue.add(KThread.currentThread());
		conditionLock.release();
		KThread.sleep();
		conditionLock.acquire();

		Machine.interrupt().restore(intStatus);

	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		System.out.println("enter wake...");
		boolean intStatus = Machine.interrupt().disable();

		if (!waitQueue.isEmpty()) {
			KThread thread = waitQueue.removeFirst();
			if (!ThreadedKernel.alarm.cancel(thread)) {
				thread.ready();
			}
		}

		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();

		while (!waitQueue.isEmpty()) {
			KThread thread = waitQueue.removeFirst();
			if (!ThreadedKernel.alarm.cancel(thread)) {
				thread.ready();
			}
		}

		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses. The current thread must hold the
	 * associated lock. The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 */
	public void sleepFor(long timeout) {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();

		if (timeout > 0) {
			waitQueue.add(KThread.currentThread());
		}
		conditionLock.release();
		ThreadedKernel.alarm.waitUntil(timeout);
		System.out.println("after waitUntil in sleepFor");
		conditionLock.acquire();
		waitQueue.remove(KThread.currentThread());

		Machine.interrupt().restore(intStatus);
	}

	public static class InterlockTest {
		private static Lock lock;
		private static Condition2 cv;

		private static class Interlocker implements Runnable {
			public void run() {
				lock.acquire();
				for (int i = 0; i < 10; i++) {
					System.out.println(KThread.currentThread().getName() + ", i = " + i);
					cv.wake(); // signal
					cv.sleep(); // wait
				}
				lock.release();
			}
		}

		public InterlockTest() {
			lock = new Lock();
			cv = new Condition2(lock);

			KThread ping = new KThread(new Interlocker());
			ping.setName("ping");
			KThread pong = new KThread(new Interlocker());
			pong.setName("pong");

			ping.fork();
			pong.fork();

			// We need to wait for ping to finish, and the proper way
			// to do so is to join on ping. (Note that, when ping is
			// done, pong is sleeping on the condition variable; if we
			// were also to join on pong, we would block forever.)
			// For this to work, join must be implemented. If you
			// have not implemented join yet, then comment out the
			// call to join and instead uncomment the loop with
			// yields; the loop has the same effect, but is a kludgy
			// way to do it.
			ping.join();
			// for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
		}
	}

	// Test programs should have exactly the same behavior with the
	// Condition and Condition2 classes. You can first try a test with
	// Condition, which is already provided for you, and then try it
	// with Condition2, which you are implementing, and compare their
	// behavior.
	public static void cvTest2() {
		final Lock lock = new Lock();
		// final Condition empty = new Condition(lock);
		final Condition2 empty = new Condition2(lock);
		final LinkedList<Integer> list = new LinkedList<>();

		KThread consumer = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				while (list.isEmpty()) {
					empty.sleep();
				}
				Lib.assertTrue(list.size() == 5, "List should have 5 values.");
				while (!list.isEmpty()) {
					// context swith for the fun of it
					KThread.yield();
					System.out.println("Removed " + list.removeFirst());
				}
				lock.release();
			}
		});

		KThread producer = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				for (int i = 0; i < 5; i++) {
					list.add(i);
					System.out.println("Added " + i);
					// context swith for the fun of it
					KThread.yield();
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
		// and the proper way to do so is to join on them. For this
		// to work, join must be implemented. If you have not
		// implemented join yet, then comment out the calls to join
		// and instead uncomment the loop with yield; the loop has the
		// same effect, but is a kludgy way to do it.
		consumer.join();
		producer.join();
		// for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
	}

	public static void sleepForTest1() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		lock.acquire();
		long t0 = Machine.timer().getTime();
		System.out.println(KThread.currentThread().getName() + " sleeping");
		// no other thread will wake us up, so we should time out
		cv.sleepFor(2000);
		long t1 = Machine.timer().getTime();
		System.out.println(KThread.currentThread().getName() +
				" woke up, slept for " + (t1 - t0) + " ticks");
		lock.release();
	}

	public static void sleepForTest2() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		KThread child1 = new KThread(new Runnable() {
			public void run() {
				for (int i = 0; i < 15; i++) {
					System.out.println("i = " + i + ", busy...");
					if (i == 3) {
						lock.acquire();
						cv.wake();
						lock.release();
					}
					KThread.yield();
				}
			}
		});
		child1.setName("child1").fork();

		lock.acquire();
		long t0 = Machine.timer().getTime();
		System.out.println(KThread.currentThread().getName() + " sleeping");
		cv.sleepFor(2000);
		long t1 = Machine.timer().getTime();
		System.out.println(KThread.currentThread().getName() +
				" woke up, slept for " + (t1 - t0) + " ticks");
		lock.release();
		child1.join();
	}

	public static void sleepForTest3() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		KThread child2 = new KThread(new Runnable() {
			public void run() {
				for (int i = 0; i < 5; i++) {
					System.out.println("i = " + i + ", busy...");
					if (i == 3) {
						lock.acquire();
						long t0 = Machine.timer().getTime();
						System.out.println(KThread.currentThread().getName() + " sleeping");
						cv.sleepFor(5000);
						long t1 = Machine.timer().getTime();
						System.out.println(KThread.currentThread().getName() +
								" woke up, slept for " + (t1 - t0) + " ticks");
						lock.release();
					}
					KThread.yield();
				}
			}
		});
		child2.setName("child2").fork();

		lock.acquire();
		long t0 = Machine.timer().getTime();
		System.out.println(KThread.currentThread().getName() + " sleeping");
		cv.sleepFor(2000);
		long t1 = Machine.timer().getTime();
		System.out.println(KThread.currentThread().getName() +
				" woke up, slept for " + (t1 - t0) + " ticks");
		lock.release();
		child2.join();
	}

	public static void sleepForTest4() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		KThread child3 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() + " sleeping");
				cv.sleepFor(5000);
				long t1 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
				lock.release();
			}
		});
		child3.setName("child3").fork();
		KThread child4 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() + " sleeping");
				cv.sleepFor(8000);
				long t1 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
				lock.release();
			}
		});
		child4.setName("child4").fork();
		KThread child5 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() + " sleeping");
				cv.sleepFor(15000);
				long t1 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
				lock.release();
			}
		});
		child5.setName("child5").fork();
		KThread child6 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				long t0 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() + " sleeping");
				cv.sleepFor(1000);
				long t1 = Machine.timer().getTime();
				System.out.println(KThread.currentThread().getName() +
						" woke up, slept for " + (t1 - t0) + " ticks");
				lock.release();
			}
		});
		child6.setName("child6").fork();

		lock.acquire();
		long t0 = Machine.timer().getTime();
		System.out.println(KThread.currentThread().getName() + " sleeping");
		cv.sleepFor(2000);
		long t1 = Machine.timer().getTime();
		System.out.println(KThread.currentThread().getName() +
				" woke up, slept for " + (t1 - t0) + " ticks");
		cv.wakeAll();
		lock.release();
		child3.join();
		child4.join();
		child5.join();
		child6.join();
	}

	public static void selfTest() {
		Lib.debug('t', "Enter Condition2 selfTest");
		System.out.println("\n Enter Condition2 SelfTest \n");
		System.out.println("==============================");
		new InterlockTest();
		System.out.println("==============================");
		Condition.cvTest();
		System.out.println("==============================");
		cvTest2();
		System.out.println("==============================");
		sleepForTest1();
		System.out.println("==============================");
		sleepForTest2();
		System.out.println("==============================");
		sleepForTest3();
		System.out.println("==============================");
		sleepForTest4();
	}

}
