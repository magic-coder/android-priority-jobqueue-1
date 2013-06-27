package com.path.android.jobqueue.test.jobmanager;

import android.util.Log;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.test.jobs.DummyJob;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Collection;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.equalTo;

@RunWith(RobolectricTestRunner.class)
public class JobManagerMultiThreadTest extends JobManagerTestBase {
    private static AtomicInteger multiThreadedJobCounter;
    @Test
    public void testMultiThreaded() throws Exception {
        multiThreadedJobCounter = new AtomicInteger(0);
        final JobManager jobManager = createJobManager();
        int limit = 200;
        ExecutorService executor = new ThreadPoolExecutor(20, 20, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(limit));
        Collection<Future<?>> futures = new LinkedList<Future<?>>();
        for(int i = 0; i < limit; i++) {
            final int id = i;
            futures.add(executor.submit(new Runnable() {
                @Override
                public void run() {
                    final boolean persistent = Math.round(Math.random()) % 2 == 0;
                    boolean requiresNetwork = Math.round(Math.random()) % 2 == 0;
                    int priority = (int) (Math.round(Math.random()) % 10);
                    multiThreadedJobCounter.incrementAndGet();
                    jobManager.addJob(priority, new DummyJobForMultiThread(id, requiresNetwork, persistent));
                }
            }));
        }
        for (Future<?> future:futures) {
            future.get();
        }
        Log.d("TAG", "added all jobs");
        //wait until all jobs are added
        long start = System.nanoTime();
        long timeLimit = JobManager.NS_PER_MS * 20000;//20 seconds
        while(System.nanoTime() - start < timeLimit && multiThreadedJobCounter.get() != 0) {
            Thread.sleep(1000);
        }
        Log.d("TAG", "did we reach timeout? " + (System.nanoTime() - start >= timeLimit));

        MatcherAssert.assertThat("jobmanager count should be 0",
                jobManager.count(), equalTo(0));

        MatcherAssert.assertThat("multiThreadedJobCounter should be 0",
                multiThreadedJobCounter.get(), equalTo(0));

    }
    public static class DummyJobForMultiThread extends DummyJob {
        private int id;
        private boolean persist;
        private DummyJobForMultiThread(int id, boolean requiresNetwork, boolean persist) {
            super(requiresNetwork);
            this.persist = persist;
            this.id = id;
        }

        @Override
        public boolean shouldPersist() {
            return persist;
        }

        @Override
        public void onRun() throws Throwable {
            super.onRun();
            int remaining = multiThreadedJobCounter.decrementAndGet();
            Log.d("DummyJobForMultiThread", "persistent:" + persist + ", requires network:" + requiresNetwork() + ", running " + id + ", remaining: " + remaining);
        }

        @Override
        protected boolean shouldReRunOnThrowable(Throwable throwable) {
            multiThreadedJobCounter.incrementAndGet();
            return true;
        }
    };
}
