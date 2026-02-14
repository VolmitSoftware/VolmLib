package art.arcane.volmlib.util.scheduling;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.function.NastyRunnable;
import art.arcane.volmlib.util.math.M;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;

public class TaskExecutor {
    private final ExecutorService service;
    private int xc;

    public TaskExecutor(int threadLimit, int priority, String name) {
        xc = 1;

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

    public TaskGroup startWork() {
        return new TaskGroup();
    }

    public void close() {
        Thread closer = new Thread(() -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {
            }

            service.shutdown();
        }, "TaskExecutor-Close");

        closer.setDaemon(true);
        closer.start();
    }

    public void closeNow() {
        service.shutdown();
    }

    protected void onTaskFailure(Throwable ex) {
        SchedulerBridge.onError(ex);
    }

    public enum TaskState {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public class TaskGroup {
        private final KList<AssignedTask> tasks;

        public TaskGroup() {
            tasks = new KList<>();
        }

        public TaskGroup queue(NastyRunnable... r) {
            for (NastyRunnable i : r) {
                tasks.add(new AssignedTask(i));
            }

            return this;
        }

        public TaskGroup queue(KList<NastyRunnable> r) {
            for (NastyRunnable i : r) {
                tasks.add(new AssignedTask(i));
            }

            return this;
        }

        public TaskGroup queue(List<NastyRunnable> r) {
            for (NastyRunnable i : r) {
                tasks.add(new AssignedTask(i));
            }

            return this;
        }

        public TaskResult execute() {
            double timeElapsed;
            int tasksExecuted = 0;
            int tasksFailed = 0;
            int tasksCompleted = 0;
            tasks.forEach((t) -> t.go());
            long msv = M.ns();

            waiting:
            while (true) {
                try {
                    Thread.sleep(0);
                } catch (InterruptedException ignored) {
                }

                for (AssignedTask i : tasks) {
                    if (i.getState().equals(TaskState.QUEUED) || i.getState().equals(TaskState.RUNNING)) {
                        continue waiting;
                    }
                }

                timeElapsed = (double) (M.ns() - msv) / 1000000D;

                for (AssignedTask i : tasks) {
                    if (i.getState().equals(TaskState.COMPLETED)) {
                        tasksCompleted++;
                    } else {
                        tasksFailed++;
                    }

                    tasksExecuted++;
                }

                break;
            }

            return new TaskResult(timeElapsed, tasksExecuted, tasksFailed, tasksCompleted);
        }
    }

    public static class TaskResult {
        public final double timeElapsed;
        public final int tasksExecuted;
        public final int tasksFailed;
        public final int tasksCompleted;

        public TaskResult(double timeElapsed, int tasksExecuted, int tasksFailed, int tasksCompleted) {
            this.timeElapsed = timeElapsed;
            this.tasksExecuted = tasksExecuted;
            this.tasksFailed = tasksFailed;
            this.tasksCompleted = tasksCompleted;
        }

        @Override
        public String toString() {
            return "TaskResult{" +
                    "timeElapsed=" + timeElapsed +
                    ", tasksExecuted=" + tasksExecuted +
                    ", tasksFailed=" + tasksFailed +
                    ", tasksCompleted=" + tasksCompleted +
                    '}';
        }
    }

    public class AssignedTask {
        private final NastyRunnable task;
        private volatile TaskState state;

        public AssignedTask(NastyRunnable task) {
            this.task = task;
            state = TaskState.QUEUED;
        }

        public NastyRunnable getTask() {
            return task;
        }

        public TaskState getState() {
            return state;
        }

        public void setState(TaskState state) {
            this.state = state;
        }

        public void go() {
            service.execute(() -> {
                state = TaskState.RUNNING;
                try {
                    task.run();
                    state = TaskState.COMPLETED;
                } catch (Throwable ex) {
                    onTaskFailure(ex);
                    state = TaskState.FAILED;
                }
            });
        }
    }
}
