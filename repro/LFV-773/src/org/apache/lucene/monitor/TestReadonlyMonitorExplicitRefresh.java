/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.monitor;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.Lock;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Monitor#purgeCache: "When Monitor is in read-only mode, cache is NEVER purged automatically you
 * MUST call it when you want new changes." A query that the writable Monitor committed before
 * purgeCache() was called must therefore be matched by the read-only Monitor once purgeCache()
 * has returned, also when another refresh of the read-only Monitor runs at the same time (its own
 * background refresh at the purge frequency, or another thread's purgeCache()).
 *
 * <p>Only public API is used. The read-only Monitor's Directory (a plain delegating {@link
 * Directory} supplied through MonitorConfiguration#setDirectoryProvider) makes thread A's refresh
 * slow: A waits on a latch when it opens a segment file, i.e. after it has picked the latest
 * commit, which at that moment does not contain q2. Then q2 is committed and thread B calls
 * purgeCache(). No timing is involved: the test waits until B has either returned or is waiting,
 * and only then lets A continue.
 */
public class TestReadonlyMonitorExplicitRefresh extends LuceneTestCase {

  /**
   * Delegates to another Directory; parks one chosen thread when it opens a file of a segment it is
   * about to read.
   */
  static final class SlowDirectory extends Directory {
    final Directory in;
    volatile Thread target;
    final CountDownLatch parked = new CountDownLatch(1);
    final CountDownLatch resume = new CountDownLatch(1);

    SlowDirectory(Directory in) {
      this.in = in;
    }

    @Override
    public IndexInput openInput(String name, IOContext context) throws IOException {
      // segments_N and .si files are read both by the up-to-date check and when the commit is
      // opened; any other segment file is read only while the new reader is built, i.e. after
      // the refresh has picked its commit
      if (Thread.currentThread() == target
          && name.startsWith("segments") == false
          && name.endsWith(".si") == false) {
        target = null;
        parked.countDown();
        try {
          resume.await();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      return in.openInput(name, context);
    }

    @Override
    public String[] listAll() throws IOException {
      return in.listAll();
    }

    @Override
    public void deleteFile(String name) throws IOException {
      in.deleteFile(name);
    }

    @Override
    public long fileLength(String name) throws IOException {
      return in.fileLength(name);
    }

    @Override
    public IndexOutput createOutput(String name, IOContext context) throws IOException {
      return in.createOutput(name, context);
    }

    @Override
    public IndexOutput createTempOutput(String prefix, String suffix, IOContext context)
        throws IOException {
      return in.createTempOutput(prefix, suffix, context);
    }

    @Override
    public void sync(Collection<String> names) throws IOException {
      in.sync(names);
    }

    @Override
    public void syncMetaData() throws IOException {
      in.syncMetaData();
    }

    @Override
    public void rename(String source, String dest) throws IOException {
      in.rename(source, dest);
    }

    @Override
    public Lock obtainLock(String name) throws IOException {
      return in.obtainLock(name);
    }

    @Override
    public void copyFrom(Directory from, String src, String dest, IOContext context)
        throws IOException {
      in.copyFrom(from, src, dest, context);
    }

    @Override
    public Set<String> getPendingDeletions() throws IOException {
      return in.getPendingDeletions();
    }

    @Override
    public void close() {
      // the writable Monitor owns and closes the shared directory
    }
  }

  private static MonitorQuery query(String id, String term) {
    return new MonitorQuery(id, new TermQuery(new Term("f", term)), term, Collections.emptyMap());
  }

  private static boolean matches(Monitor monitor, String text, String id) throws IOException {
    Document d = new Document();
    d.add(new TextField("f", text, Field.Store.NO));
    return monitor.match(d, QueryMatch.SIMPLE_MATCHER).matches(id) != null;
  }

  private static Thread purgeInThread(Monitor monitor, AtomicReference<Throwable> failure) {
    Thread t =
        new Thread(
            () -> {
              try {
                monitor.purgeCache();
              } catch (Throwable e) {
                failure.compareAndSet(null, e);
              }
            });
    t.start();
    return t;
  }

  public void testPurgeCacheWhileAnotherRefreshRuns() throws Exception {
    MonitorQuerySerializer serializer =
        MonitorQuerySerializer.fromParser(s -> new TermQuery(new Term("f", s)));
    Directory shared = new ByteBuffersDirectory();
    SlowDirectory readDir = new SlowDirectory(shared);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    try (Monitor writer =
        new Monitor(
            new StandardAnalyzer(),
            new MonitorConfiguration().setDirectoryProvider(() -> shared, serializer))) {
      writer.register(query("q1", "apple"));
      MonitorConfiguration readConfig =
          new MonitorConfiguration()
              .setPurgeFrequency(1, TimeUnit.DAYS) // background refresh out of the way
              .setDirectoryProvider(() -> readDir, serializer, true);
      try (Monitor reader = new Monitor(new StandardAnalyzer(), readConfig)) {
        assertTrue(matches(reader, "apple", "q1"));
        writer.register(query("qx", "banana"));

        // thread A asks for the new changes; its refresh picks the latest commit (q1, qx) and is slow
        Thread a =
            new Thread(
                () -> {
                  try {
                    reader.purgeCache();
                  } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                  }
                });
        readDir.target = a;
        a.start();
        assertTrue("A's refresh started", readDir.parked.await(30, TimeUnit.SECONDS));

        // q2 is committed; thread B asks for the new changes while A's refresh runs
        writer.register(query("q2", "cherry"));
        Thread b = purgeInThread(reader, failure);
        while (b.isAlive()
            && b.getState() != Thread.State.WAITING
            && b.getState() != Thread.State.TIMED_WAITING
            && b.getState() != Thread.State.BLOCKED) {
          Thread.onSpinWait();
        }
        boolean bReturnedBeforeA = b.isAlive() == false;
        boolean q2VisibleBeforeAFinished = bReturnedBeforeA && matches(reader, "cherry", "q2");
        readDir.resume.countDown();
        a.join();
        b.join();
        assertNull("purgeCache failed: " + failure.get(), failure.get());
        // both purgeCache() calls have returned; B was called after q2 was committed
        boolean q2Visible = matches(reader, "cherry", "q2");
        assertTrue("qx (committed before A's call) is visible", matches(reader, "banana", "qx"));
        assertTrue(
            "read-only Monitor does not match q2 after purgeCache() returned, although q2 was"
                + " committed before purgeCache() was called (B returned before A's refresh"
                + " finished: "
                + bReturnedBeforeA
                + ", q2 visible right after B returned: "
                + q2VisibleBeforeAFinished
                + "; q2 still not visible after both calls returned)",
            q2Visible);
      }
    }
  }
}
