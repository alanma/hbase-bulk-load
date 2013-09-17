package com.blogspot.anikulin.bulkload.loaders;


import com.blogspot.anikulin.bulkload.clients.HBaseClient;
import com.blogspot.anikulin.bulkload.clients.HBaseClientImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.blogspot.anikulin.bulkload.commons.Constants.*;

/**
 * @author Anatoliy Nikulin
 * @email 2anikulin@gmail.com
 */
public class HBaseLoader {

    private static final Logger LOG = LoggerFactory.getLogger(HBaseLoader.class);
    private static final int defaultThreadsCount = 8;

    public static void main(String[] args) {

        if (args.length < 2) {
            System.out.println("Wrong input parameters. Use [start key] [end key] optional: [zookeeper] [table name] [threads count]");
            return;
        }

        long startKey = Long.parseLong(args[0]);
        long endKey = Long.parseLong(args[1]);

        String zookeeper = args.length > 2 ? args[2] : ZOOKEEPER_HOST;
        String tableName = args.length > 3 ? args[3] : TABLE_NAME;

        int threadsCount = args.length > 4 ? Integer.parseInt(args[3]) : defaultThreadsCount;

        LOG.info(
                String.format(
                        "Process started with startKey %d, endKey %d, zookeeper %s, table name %s, threads count %d",
                        startKey,
                        endKey,
                        zookeeper,
                        tableName,
                        threadsCount
                )
        );

        HBaseLoader loader = new HBaseLoader();
        loader.load(startKey, endKey, zookeeper, tableName, threadsCount);

    }

    public void load(final long startKey, final long endKey, final String zookeeper, final String tableName, int threadsCount) {
        final CyclicBarrier barrier = new CyclicBarrier(threadsCount);
        final CountDownLatch latch = new CountDownLatch(threadsCount);

        final AtomicLong commonTimeCounter = new AtomicLong(0);
        final AtomicLong keysRangeCounter = new AtomicLong(startKey);
        final long step = (endKey - startKey) / threadsCount;

        Executor executor = Executors.newFixedThreadPool(threadsCount);

        for (int i = 0; i < threadsCount; i++) {
            executor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            HBaseClient client = null;
                            try {
                                client = createClient(zookeeper, tableName);
                                long fromKey = keysRangeCounter.getAndAdd(step);
                                long toKey = fromKey + step;

                                barrier.await();

                                LOG.info(
                                        String.format(
                                                "Thread id: %d started. Key range [%d, %d]",
                                                Thread.currentThread().getId(),
                                                fromKey,
                                                toKey
                                        )
                                );

                                long startTime = System.nanoTime();
                                client.send(fromKey, toKey);
                                commonTimeCounter.getAndAdd(System.nanoTime() - startTime);
                            } catch (Throwable e) {
                                LOG.error("");
                            } finally {
                                close(client);
                                latch.countDown();
                            }
                        }
                    }
            );
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            LOG.error("Process was interrupted", e);
        }

        LOG.info(
                "Process finished. Threads count: {} average time (ms): {}",
                threadsCount,
                commonTimeCounter.get() / threadsCount / 1000000
        );
    }

    protected HBaseClient createClient(String zookeeper, String tableName) throws IOException {
        return new HBaseClientImpl(zookeeper, tableName);
    }

    private void close(HBaseClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Throwable e) {
                LOG.error("Error while closing HBaseClientImpl", e);
            }
        }
    }
}