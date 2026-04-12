package com.treloc.xtreloc.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates thread pools for batch-style workloads with bounded queues and caller-runs back-pressure.
 */
public final class BatchExecutorFactory {

    private static final AtomicInteger POOL_SEQ = new AtomicInteger();

    private BatchExecutorFactory() {
    }

    /**
     * Suggested queue capacity from an expected task count (capped for memory).
     */
    public static int suggestedQueueCapacity(int taskCount) {
        if (taskCount <= 0) {
            return 64;
        }
        return Math.min(8192, Math.max(32, taskCount * 2));
    }

    /**
     * Fixed-size pool, bounded queue, {@link ThreadPoolExecutor.CallerRunsPolicy} when the queue is full.
     */
    public static ExecutorService newFixedThreadPoolBounded(int nThreads, int maxQueueSize) {
        int cap = Math.max(1, maxQueueSize);
        int n = Math.max(1, nThreads);
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "xtreloc-batch-" + POOL_SEQ.incrementAndGet());
            t.setDaemon(false);
            return t;
        };
        return new ThreadPoolExecutor(
                n, n,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(cap),
                tf,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
