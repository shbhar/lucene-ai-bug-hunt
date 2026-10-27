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
package org.apache.lucene.facet.taxonomy.directory;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

/**
 * DirectoryTaxonomyWriter.replaceTaxonomy changes the taxonomy index first and increments the
 * taxonomy epoch last. A near-real-time DirectoryTaxonomyReader reopened in between sees the new
 * categories but the old epoch, so it treats the taxonomy as not recreated and reuses the previous
 * reader's ordinal/label caches and parent arrays, which belong to the replaced taxonomy.
 *
 * <p>Deterministic: the taxonomy IndexWriter's InfoStream (configured through the documented
 * createIndexWriterConfig extension hook) pauses replaceTaxonomy when it refreshes its internal
 * reader, which is after addIndexes and before the epoch increment, and another thread reopens the
 * reader at that moment. No lock is held by the paused thread except the taxonomy writer's own
 * monitor, which openIfChanged does not take. Public and protected API only.
 */
public class TestNrtReopenDuringReplaceTaxonomy extends LuceneTestCase {

  /**
   * Pauses the armed thread once, at the start of IndexWriter.getReader, until the reopening
   * thread has finished, or until it is blocked on the taxonomy writer's monitor (which is what a
   * fixed reader that serialises with replaceTaxonomy does; then the pause must end).
   */
  private static final class PauseAtGetReader extends InfoStream {
    final AtomicReference<Thread> armed = new AtomicReference<>();
    final CountDownLatch paused = new CountDownLatch(1);
    final CountDownLatch resume = new CountDownLatch(1);
    volatile Thread reopener;
    volatile Object writerMonitor;

    @Override
    public void message(String component, String message) {
      if ("IW".equals(component)
          && "flush at getReader".equals(message)
          && armed.compareAndSet(Thread.currentThread(), null)) {
        paused.countDown();
        try {
          long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
          while (resume.await(10, TimeUnit.MILLISECONDS) == false) {
            if (blockedOnWriterMonitor()) {
              return;
            }
            assertTrue("reopener neither finished nor blocked", System.nanoTime() < deadline);
          }
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }

    private boolean blockedOnWriterMonitor() {
      Thread t = reopener;
      if (t == null || t.getState() != Thread.State.BLOCKED) {
        return false;
      }
      ThreadInfo ti = ManagementFactory.getThreadMXBean().getThreadInfo(t.threadId());
      LockInfo lock = ti == null ? null : ti.getLockInfo();
      return lock != null && lock.getIdentityHashCode() == System.identityHashCode(writerMonitor);
    }

    @Override
    public boolean isEnabled(String component) {
      return "IW".equals(component);
    }

    @Override
    public void close() {}
  }

  private static DirectoryTaxonomyWriter hookedWriter(Directory dir, PauseAtGetReader pause)
      throws Exception {
    return new DirectoryTaxonomyWriter(dir) {
      @Override
      protected IndexWriterConfig createIndexWriterConfig(OpenMode openMode) {
        return super.createIndexWriterConfig(openMode).setInfoStream(pause);
      }
    };
  }

  /** Runs replaceTaxonomy(src) on this thread and reopens r1 on another thread inside it. */
  private static DirectoryTaxonomyReader reopenDuringReplace(
      DirectoryTaxonomyWriter writer, PauseAtGetReader pause, DirectoryTaxonomyReader r1, Directory src)
      throws Exception {
    AtomicReference<DirectoryTaxonomyReader> r2 = new AtomicReference<>();
    AtomicReference<Throwable> reopenFailure = new AtomicReference<>();
    Thread reopener =
        new Thread(
            () -> {
              try {
                assertTrue(pause.paused.await(30, TimeUnit.SECONDS));
                r2.set(TaxonomyReader.openIfChanged(r1));
              } catch (Throwable t) {
                reopenFailure.set(t);
              } finally {
                pause.resume.countDown();
              }
            });
    pause.reopener = reopener;
    pause.writerMonitor = writer;
    reopener.start();
    pause.armed.set(Thread.currentThread());
    writer.replaceTaxonomy(src);
    reopener.join();
    if (reopenFailure.get() != null) {
      throw new AssertionError("openIfChanged during replaceTaxonomy threw", reopenFailure.get());
    }
    return r2.get();
  }

  private static Directory sourceTaxonomy(Directory dir, String... labels) throws Exception {
    try (DirectoryTaxonomyWriter w = new DirectoryTaxonomyWriter(dir)) {
      for (String l : labels) {
        w.addCategory(new FacetLabel(l));
      }
    }
    return dir;
  }

  /**
   * Same labels, other ordinals. Mirrors upstream testOpenIfChangedReplaceTaxonomy (which reopens
   * after replaceTaxonomy returns): the reopened reader must answer from the new taxonomy only.
   */
  public void testReopenedReaderUsesReplacedTaxonomyCaches() throws Exception {
    try (Directory dir = newDirectory();
        Directory src = sourceTaxonomy(newDirectory(), "b", "a")) {
      PauseAtGetReader pause = new PauseAtGetReader();
      DirectoryTaxonomyWriter writer = hookedWriter(dir, pause);
      FacetLabel a = new FacetLabel("a");
      FacetLabel b = new FacetLabel("b");
      writer.addCategory(a); // ordinal 1
      writer.addCategory(b); // ordinal 2
      DirectoryTaxonomyReader r1 = new DirectoryTaxonomyReader(writer);
      DirectoryTaxonomyReader r2 = null;
      try {
        // fill r1's caches, as any facet search would
        assertEquals(1, r1.getOrdinal(a));
        assertEquals(2, r1.getOrdinal(b));
        assertEquals(a, r1.getPath(1));
        assertEquals(b, r1.getPath(2));

        r2 = reopenDuringReplace(writer, pause, r1, src);
        assertNotNull("the taxonomy changed, so openIfChanged must return a new reader", r2);
        // the replaced taxonomy is root=0, b=1, a=2
        assertEquals(3, r2.getInternalIndexReader().maxDoc());
        DirectoryTaxonomyReader r3 = TaxonomyReader.openIfChanged(r2);
        if (r3 != null) {
          r3.close();
        }
        String msg =
            "reader reopened during replaceTaxonomy: getOrdinal(b)="
                + r2.getOrdinal(b)
                + " getOrdinal(a)="
                + r2.getOrdinal(a)
                + " getPath(1)="
                + r2.getPath(1)
                + " getPath(2)="
                + r2.getPath(2)
                + "; a later openIfChanged returned "
                + (r3 == null ? "null (the stale reader stays current)" : "a new reader");
        assertEquals(msg, 1, r2.getOrdinal(b));
        assertEquals(msg, 2, r2.getOrdinal(a));
        assertEquals(msg, b, r2.getPath(1));
        assertEquals(msg, a, r2.getPath(2));
      } finally {
        if (r2 != null) {
          r2.close();
        }
        r1.close();
        writer.close();
      }
    }
  }

  /**
   * A smaller replacement: the reopen copies the previous reader's parent array into a smaller
   * one and throws instead of returning a reader for the new taxonomy.
   */
  public void testReopenDuringReplaceWithSmallerTaxonomy() throws Exception {
    try (Directory dir = newDirectory();
        Directory src = sourceTaxonomy(newDirectory(), "x")) {
      PauseAtGetReader pause = new PauseAtGetReader();
      DirectoryTaxonomyWriter writer = hookedWriter(dir, pause);
      writer.addCategory(new FacetLabel("a"));
      writer.addCategory(new FacetLabel("b"));
      DirectoryTaxonomyReader r1 = new DirectoryTaxonomyReader(writer);
      DirectoryTaxonomyReader r2 = null;
      try {
        r1.getParallelTaxonomyArrays(); // as every taxonomy facet count does
        r2 = reopenDuringReplace(writer, pause, r1, src);
        assertNotNull(r2);
        assertEquals(new FacetLabel("x"), r2.getPath(1));
        assertEquals(2, r2.getSize());
      } finally {
        if (r2 != null) {
          r2.close();
        }
        r1.close();
        writer.close();
      }
    }
  }
}
