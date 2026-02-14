package art.arcane.volmlib.util.scheduling;

import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.function.NastyRunnable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;

public class GroupedExecutor {
    private final ExecutorService service;
    private final KMap<String, Integer> mirror;
    private int xc;

    public GroupedExecutor(int threadLimit, int priority, String name) {
        xc = 1;
        mirror = new KMap<>();

        if (threadLimit == 1) {
            service = Executors.newSingleThreadExecutor((r) -> {
                Thread t = new Thread(r);
                t.setName(name);
                t.setPriority(priority);
                return t;
            });
        } else if (threadLimit > 1) {
            ForkJoinWorkerThreadFactory factory = (pool) -> {
                ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                worker.setName(name + " " + xc++);
                worker.setPriority(priority);
                return worker;
            };

            service = new ForkJoinPool(threadLimit, factory, null, false);
        } else {
            service = Executors.newCachedThreadPool((r) -> {
                Thread t = new Thread(r);
                t.setName(name + " " + xc++);
                t.setPriority(priority);
                return t;
            });
        }
    }

    public void waitFor(String group) {
        if (group == null) {
            return;
        }

        if (!mirror.containsKey(group)) {
            return;
        }

        while (true) {
            if (mirror.get(group) == 0) {
                break;
            }
        }
    }

    public void queue(String group, NastyRunnable runnable) {
        mirror.compute(group, (k, v) -> k == null || v == null ? 1 : v + 1);
        service.execute(() -> {
            try {
                runnable.run();
            } catch (Throwable e) {
                e.printStackTrace();
            }

            mirror.computeIfPresent(group, (k, v) -> v - 1);
        });
    }

    public void close() {
        Thread closeThread = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            service.shutdown();
        }, "GroupedExecutor-Close");

        closeThread.setDaemon(true);
        closeThread.start();
    }

    public void closeNow() {
        service.shutdown();
    }
}
