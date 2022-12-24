package nachos.threads;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.HashMap;
import java.util.LinkedList;
//import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import nachos.machine.*;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {

    // private LinkedList<Integer> tags;
    private Map<Integer, LinkedList<Integer>> exchangeValsMap;
    private Map<Integer, InfoWithTags> tagInfoMap;
    private Map<Integer, Integer> tagValMap;
    private Map<String, Integer> readyMap;
    private HashSet<Integer> tags;
    private Lock lock = new Lock();
    private Condition cv = new Condition(lock);

    public enum Status {
        WAITING, READY
    }

    /**
     * Allocate a new Rendezvous.
     */
    public Rendezvous() {
        tags = new HashSet<Integer>();
        exchangeValsMap = new HashMap<Integer, LinkedList<Integer>>();
        tagValMap = new HashMap<Integer, Integer>();
        tagInfoMap = new HashMap<Integer, InfoWithTags>();
        readyMap = new HashMap<String, Integer>();
    }

    /**
     * Synchronously exchange a value with another thread. The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y). When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other). The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag   the synchronization tag.
     * @param value the integer to exchange.
     */

    private class InfoWithTags {
        private Status status;
        private Map<String, Integer> waitingMap;

        InfoWithTags() {
            waitingMap = new HashMap<String, Integer>();
            status = Status.READY;
        }
    }

    // private class InfoWithTags {
    // private Status status;
    // private LinkedList<Integer> values;

    // InfoWithTags() {
    // values = new LinkedList<Integer>();
    // status = Status.READY;
    // }
    // }

    // public int exchange(int tag, int value) {
    // int res;
    // lock.acquire();
    // // boolean intStatus = Machine.interrupt().disable();
    // if (!tags.remove(Integer.valueOf(tag))) {
    // tags.add(tag);
    // tagValMap.put(tag, value);
    // cv.sleep();
    // res = exchangeValsMap.get(tag).removeFirst();
    // } else {
    // cv.wake();
    // res = tagValMap.get(tag);
    // if (exchangeValsMap.get(tag) == null) {
    // exchangeValsMap.put(tag, new LinkedList<Integer>());
    // }
    // exchangeValsMap.get(tag).add(value);
    // }
    // lock.release();
    // // Machine.interrupt().restore(intStatus);
    // return res;
    // }

    // public int exchange(int tag, int value) {
    // int res;
    // lock.acquire();
    // // boolean intStatus = Machine.interrupt().disable();
    // if (!tags.remove(Integer.valueOf(tag))) {
    // tags.add(tag);
    // if (!exchangeValsMap.containsKey(tag)) {
    // exchangeValsMap.put(tag, new LinkedList<Integer>());
    // }
    // exchangeValsMap.get(tag).add(value);
    // cv.sleep();
    // res = exchangeValsMap.get(tag).removeFirst();
    // } else {
    // cv.wake();
    // res = exchangeValsMap.get(tag).removeLast();
    // exchangeValsMap.get(tag).add(value);
    // }
    // lock.release();
    // // Machine.interrupt().restore(intStatus);
    // return res;
    // }

    // public int exchange(int tag, int value) {
    // int res;
    // lock.acquire();
    // if (!tagInfoMap.containsKey(tag)) {
    // tagInfoMap.put(tag, new InfoWithTags());
    // }

    // if (tagInfoMap.get(tag).status == Status.READY) {
    // tagInfoMap.get(tag).status = Status.WAITING;
    // tagInfoMap.get(tag).values.add(value);
    // cv.sleep();
    // res = tagInfoMap.get(tag).values.removeFirst();
    // } else {
    // cv.wake();
    // res = tagInfoMap.get(tag).values.removeLast();
    // tagInfoMap.get(tag).values.add(value);
    // tagInfoMap.get(tag).status = Status.READY;
    // }
    // lock.release();
    // return res;
    // }

    public int exchange(int tag, int value) {
        int res;
        lock.acquire();
        if (!tagInfoMap.containsKey(tag)) {
            tagInfoMap.put(tag, new InfoWithTags());
        }

        if (tagInfoMap.get(tag).status == Status.READY) {
            tagInfoMap.get(tag).status = Status.WAITING;
            tagInfoMap.get(tag).waitingMap.put(KThread.currentThread().toString(),
                    value);
            cv.sleep();
            res = readyMap.get(KThread.currentThread().toString());
            readyMap.remove(KThread.currentThread().toString());
        } else {
            cv.wake();
            Iterator<Map.Entry<String, Integer>> it = tagInfoMap.get(tag).waitingMap.entrySet().iterator();
            Map.Entry<String, Integer> entry = it.next();
            res = entry.getValue();
            readyMap.put(entry.getKey(), value);
            it.remove();
            tagInfoMap.get(tag).status = Status.READY;
        }
        lock.release();
        return res;
    }

    // ============================================ Test
    // =====================================

    private static int createExchange(int tag, int send, Rendezvous r) {
        System.out.println("Thread " + KThread.currentThread().getName() + " exchanging " + send);
        int recv = r.exchange(tag, send);
        System.out.println("Thread " + KThread.currentThread().getName() + " received " + recv);
        return recv;
    }

    public static void rendezTest1() {
        final Rendezvous r = new Rendezvous();

        KThread t1 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = -1;

                int recv = createExchange(tag, send, r);
                Lib.assertTrue(recv == 1, "Was expecting " + 1 + " but received " + recv);
            }
        });
        t1.setName("t1");
        KThread t2 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = 1;

                int recv = createExchange(tag, send, r);
                Lib.assertTrue(recv == -1, "Was expecting " + -1 + " but received " + recv);
            }
        });
        t2.setName("t2");

        t1.fork();
        t2.fork();
        // assumes join is implemented correctly
        t1.join();
        t2.join();
    }

    public static void rendezTest2() {
        final Rendezvous r = new Rendezvous();

        KThread t3 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = -1;

                int recv = createExchange(tag, send, r);
                Lib.assertTrue(recv == 1, "Was expecting " + 1 + " but received " + recv);
            }
        });
        t3.setName("t3");
        KThread t4 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = 1;

                int recv = createExchange(tag, send, r);
                Lib.assertTrue(recv == -1, "Was expecting " + -1 + " but received " + recv);
            }
        });
        t4.setName("t4");
        KThread t5 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = -100;

                int recv = createExchange(tag, send, r);
                Lib.assertTrue(recv == 100, "Was expecting " + 100 + " but received " + recv);
            }
        });
        t5.setName("t5");
        KThread t6 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = 100;

                int recv = createExchange(tag, send, r);
                Lib.assertTrue(recv == -100, "Was expecting " + -100 + " but received " + recv);
            }
        });
        t6.setName("t6");

        t3.fork();
        t4.fork();
        t5.fork();
        t6.fork();
        // assumes join is implemented correctly
        t3.join();
        t4.join();
        t5.join();
        t6.join();
    }

    // multiple threads with differnet tags
    public static void rendezTest3() {
        final Rendezvous r = new Rendezvous();

        KThread t3 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = -1;

                int recv = createExchange(tag, send, r);
                Lib.assertTrue(recv == -100, "Was expecting " + -100 + " but received " + recv);
            }
        });
        t3.setName("t3");

        KThread t4 = new KThread(new Runnable() {
            public void run() {
                int tag = 1;
                int send = 1;

                int recv = createExchange(tag, send, r);
                Lib.assertTrue(recv == 100, "Was expecting " + 100 + " but received " + recv);
            }
        });
        t4.setName("t4");

        KThread t5 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = -100;

                int recv = createExchange(tag, send, r);
                Lib.assertTrue(recv == -1, "Was expecting " + -1 + " but received " + recv);
            }
        });
        t5.setName("t5");

        KThread t6 = new KThread(new Runnable() {
            public void run() {
                int tag = 1;
                int send = 100;

                int recv = createExchange(tag, send, r);
                Lib.assertTrue(recv == 1, "Was expecting " + 1 + " but received " + recv);
            }
        });
        t6.setName("t6");

        t3.fork();
        t4.fork();
        t5.fork();
        t6.fork();
        // assumes join is implemented correctly
        t3.join();
        t4.join();
        t5.join();
        t6.join();
    }

    // multiple threads with differnet tags
    public static void rendezTest4() {
        final Rendezvous r = new Rendezvous();

        KThread t3 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = -1;

                int recv = createExchange(tag, send, r);
                Lib.assertTrue(recv == -100, "Was expecting " + -100 + " but received " + recv);
            }
        });
        t3.setName("t3");

        KThread t4 = new KThread(new Runnable() {
            public void run() {
                int tag = 1;
                int send = 1;

                int recv = createExchange(tag, send, r);
                Lib.assertTrue(recv == 100, "Was expecting " + 100 + " but received " + recv);
            }
        });
        t4.setName("t4");

        KThread t5 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = -100;

                int recv = createExchange(tag, send, r);
                Lib.assertTrue(recv == -1, "Was expecting " + -1 + " but received " + recv);
            }
        });
        t5.setName("t5");

        KThread t6 = new KThread(new Runnable() {
            public void run() {
                int tag = 1;
                int send = 100;

                int recv = createExchange(tag, send, r);
                Lib.assertTrue(recv == 1, "Was expecting " + 1 + " but received " + recv);
            }
        });
        t6.setName("t6");

        KThread t7 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = -1000;

                int recv = createExchange(tag, send, r);
                Lib.assertTrue(recv == 1000, "Was expecting " + 1000 + " but received " + recv);
            }
        });
        t7.setName("t7");

        KThread t8 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = 1000;

                int recv = createExchange(tag, send, r);
                Lib.assertTrue(recv == -1000, "Was expecting " + -1000 + " but received " + recv);
            }
        });
        t8.setName("t8");

        t3.fork();
        t4.fork();
        t5.fork();
        t6.fork();
        t7.fork();
        t8.fork();
        // assumes join is implemented correctly
        t3.join();
        t4.join();
        t5.join();
        t6.join();
        t7.join();
        t8.join();
    }

    // test: threads exchanging values on different instances of Rendezvous operate
    // independently of each other.
    public static void rendezTest5() {
        final Rendezvous r1 = new Rendezvous();
        final Rendezvous r2 = new Rendezvous();
        final Rendezvous r3 = new Rendezvous();

        KThread t3 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = -1;

                int recv = createExchange(tag, send, r1);
                Lib.assertTrue(recv == -1000, "Was expecting " + -1000 + " but received " + recv);
            }
        });
        t3.setName("t3");

        KThread t4 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = 1;

                int recv = createExchange(tag, send, r2);
                Lib.assertTrue(recv == 100, "Was expecting " + 100 + " but received " + recv);
            }
        });
        t4.setName("t4");

        KThread t5 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = -100;

                int recv = createExchange(tag, send, r3);
                Lib.assertTrue(recv == 1000, "Was expecting " + 1000 + " but received " + recv);
            }
        });
        t5.setName("t5");

        KThread t6 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = 100;

                int recv = createExchange(tag, send, r2);
                Lib.assertTrue(recv == 1, "Was expecting " + 1 + " but received " + recv);
            }
        });
        t6.setName("t6");

        KThread t7 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = -1000;

                int recv = createExchange(tag, send, r1);
                Lib.assertTrue(recv == -1, "Was expecting " + -1 + " but received " + recv);
            }
        });
        t7.setName("t7");

        KThread t8 = new KThread(new Runnable() {
            public void run() {
                int tag = 0;
                int send = 1000;

                int recv = createExchange(tag, send, r3);
                Lib.assertTrue(recv == -100, "Was expecting " + -100 + " but received " + recv);
            }
        });
        t8.setName("t8");

        t3.fork();
        t4.fork();
        t5.fork();
        t6.fork();
        t7.fork();
        t8.fork();
        // assumes join is implemented correctly
        t3.join();
        t4.join();
        t6.join();
        t5.join();
        t7.join();
        t8.join();
    }

    // test n threads with same tags
    public static void rendezTest6(int n) {
        if (n % 2 != 0) {
            ++n;
            System.out.println("Odd n will hang the system, so I make it to even by +1.");
        }
        KThread[] threads = new KThread[n];
        final Rendezvous r = new Rendezvous();
        Map<Integer, Integer> sendRecvMap = new HashMap<>();

        // creating n threads
        for (int i = 0; i < n; ++i) {
            final int I = i;
            KThread t = new KThread(new Runnable() {
                public void run() {
                    int tag = 0;
                    int send = I;

                    int recv = createExchange(tag, send, r);
                    sendRecvMap.put(send, recv);
                }
            });
            t.setName("thread #" + i);
            threads[i] = t;
        }

        for (int i = 0; i < n; ++i) {
            threads[i].fork();
        }
        for (int i = 0; i < n; ++i) {
            threads[i].join();
        }

        int[] check = new int[sendRecvMap.size()];
        Arrays.fill(check, -1);
        boolean res = true;
        for (int key : sendRecvMap.keySet()) {
            if (check[key] == -1) {
                check[sendRecvMap.get(key)] = key;
            } else {
                if (check[key] != sendRecvMap.get(key)) {
                    res = false;
                    break;
                }
            }
        }
        Lib.assertTrue(res, "Did not exchange properly");
        System.out.println(sendRecvMap);
        System.out.println("exchange properly");
    }

    // test n threads with different tags
    public static void rendezTest7(int n) {
        if (n % 2 != 0) {
            ++n;
            System.out.println("Odd n will hang the system, so I make it to even by +1.");
        }
        KThread[] threads = new KThread[n];
        final Rendezvous r = new Rendezvous();
        Map<Integer, Integer> sendRecvMap = new HashMap<>();
        int tags[] = new int[n];
        for (int i = 0; i < n; ++i) {
            tags[i] = i % (n / 2);
        }
        // creating n threads
        for (int i = 0; i < n; ++i) {
            final int I = i;
            KThread t = new KThread(new Runnable() {
                public void run() {
                    int tag = tags[I];
                    int send = I;

                    int recv = createExchange(tag, send, r);
                    sendRecvMap.put(send, recv);
                }
            });
            t.setName("thread #" + i);
            threads[i] = t;
        }

        for (int i = 0; i < n; ++i) {
            threads[i].fork();
        }
        for (int i = 0; i < n; ++i) {
            threads[i].join();
        }

        int[] check = new int[sendRecvMap.size()];
        Arrays.fill(check, -1);
        boolean res = true;
        for (int key : sendRecvMap.keySet()) {
            if (check[key] == -1) {
                check[sendRecvMap.get(key)] = key;
            } else {
                if (check[key] != sendRecvMap.get(key)) {
                    res = false;
                    break;
                }
            }
        }
        Lib.assertTrue(res, "Did not exchange properly");
        System.out.println(sendRecvMap);
        System.out.println("exchange properly");
    }

    public static void selfTest() {
        Lib.debug('t', "Enter Rendezvous selfTest");
        System.out.println("\n Enter Rendezvous SelfTest \n");
        // System.out.println("==============================");
        // rendezTest1();
        // System.out.println("==============================");
        // rendezTest2();
        // System.out.println("==============================");
        // rendezTest3();
        // System.out.println("==============================");
        // rendezTest4();
        // System.out.println("==============================");
        // rendezTest5();
        // System.out.println("==============================");
        rendezTest6(200);
        // System.out.println("==============================");
        // rendezTest6(25);
        // System.out.println("==============================");
        // rendezTest7(230);

    }

}
