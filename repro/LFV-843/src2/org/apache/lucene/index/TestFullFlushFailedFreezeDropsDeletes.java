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
package org.apache.lucene.index;

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

/**
 * An NRT reopen (or commit) whose full flush has no buffered documents freezes the writer's
 * buffered deletes and doc-values updates on their own ("naked" global deletes). If that freeze
 * fails, the finally block of DocumentsWriter.flushAllThreads closes the old delete queue, which
 * throws IllegalStateException("Can't close queue unless all changes are applied") and replaces
 * the original failure; the old queue, which still holds the acknowledged deletes and updates, is
 * then dropped.
 *
 * <p>The failure is an OutOfMemoryError raised from the writer's InfoStream (public extension
 * point) while the frozen packet is being built (component "BD", the message logged by the frozen
 * packet's constructor), which stands in for an allocation failure at that point. Public API only.
 */
public class TestFullFlushFailedFreezeDropsDeletes extends LuceneTestCase {

  private static final class ThrowOnceWhileFreezing extends InfoStream {
    final AtomicBoolean armed = new AtomicBoolean();
    final OutOfMemoryError error = new OutOfMemoryError("simulated allocation failure");
    final String component;
    final String text;

    /** "BD"/"compressed": inside the frozen packet's constructor; "DW"/"naked": just before it. */
    ThrowOnceWhileFreezing(String component, String text) {
      this.component = component;
      this.text = text;
    }

    ThrowOnceWhileFreezing() {
      this("BD", "compressed");
    }

    @Override
    public void message(String component, String message) {
      if (this.component.equals(component)
          && message.contains(text)
          && armed.compareAndSet(true, false)) {
        throw error;
      }
    }

    @Override
    public boolean isEnabled(String component) {
      return this.component.equals(component);
    }

    @Override
    public void close() {}
  }

  private static Document doc(String id, long dv) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new NumericDocValuesField("dv", dv));
    return d;
  }

  /** Commits docs 1 and 2, then buffers delete(1) and dv(2)=22 and makes the NRT reopen fail. */
  private static Throwable setUpAndFail(IndexWriter w, ThrowOnceWhileFreezing infoStream)
      throws Exception {
    return setUpAndFail(w, infoStream, false);
  }

  private static Throwable setUpAndFail(
      IndexWriter w, ThrowOnceWhileFreezing infoStream, boolean viaCommit) throws Exception {
    w.addDocument(doc("1", 1));
    w.addDocument(doc("2", 2));
    w.commit();
    long delSeqNo = w.deleteDocuments(new Term("id", "1"));
    long dvSeqNo = w.updateNumericDocValue(new Term("id", "2"), "dv", 22);
    assertTrue(delSeqNo > 0 && dvSeqNo > delSeqNo); // both acknowledged
    infoStream.armed.set(true);
    Throwable thrown = null;
    try {
      if (viaCommit) {
        w.commit();
      } else {
        DirectoryReader.open(w).close();
      }
    } catch (Throwable t) {
      thrown = t;
    }
    assertNotNull("the injected failure did not fire", thrown);
    return thrown;
  }

  private static boolean chainContains(Throwable t, Throwable target) {
    if (t == null) return false;
    if (t == target) return true;
    for (Throwable s : t.getSuppressed()) {
      if (chainContains(s, target)) return true;
    }
    return chainContains(t.getCause(), target);
  }

  /**
   * IndexWriter class javadoc: "if you hit an Error ... then IndexWriter will close itself". The
   * caller must see the Error, and the writer must close itself with it.
   */
  public void testErrorWhileFreezingNakedDeletesClosesWriter() throws Exception {
    ThrowOnceWhileFreezing infoStream = new ThrowOnceWhileFreezing();
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(
                dir, new IndexWriterConfig(new MockAnalyzer(random())).setInfoStream(infoStream))) {
      Throwable thrown = setUpAndFail(w, infoStream);
      assertTrue(
          "the Error is lost; caller got: " + thrown, chainContains(thrown, infoStream.error));
      assertFalse("IndexWriter must close itself after an Error", w.isOpen());
    }
  }

  /**
   * If the writer stays open after the failed reopen, the deletes and updates it acknowledged with
   * sequence numbers must still be in the next commit.
   */
  public void testAcknowledgedDeleteAndUpdateSurviveFailedReopen() throws Exception {
    checkAcknowledgedChangesSurvive(new ThrowOnceWhileFreezing(), false);
  }

  /** Same, when the failing full flush is a commit() instead of an NRT reopen. */
  public void testAcknowledgedDeleteAndUpdateSurviveFailedCommit() throws Exception {
    checkAcknowledgedChangesSurvive(new ThrowOnceWhileFreezing(), true);
  }

  /** Same, failing at the log line just before the freeze ("flush naked frozen global deletes"). */
  public void testAcknowledgedDeleteAndUpdateSurviveFailureBeforeFreeze() throws Exception {
    checkAcknowledgedChangesSurvive(new ThrowOnceWhileFreezing("DW", "naked frozen"), false);
  }

  private void checkAcknowledgedChangesSurvive(ThrowOnceWhileFreezing infoStream, boolean viaCommit)
      throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w =
          new IndexWriter(
              dir, new IndexWriterConfig(new MockAnalyzer(random())).setInfoStream(infoStream));
      Throwable thrown = setUpAndFail(w, infoStream, viaCommit);
      boolean stillOpen = w.isOpen();
      if (stillOpen) {
        w.commit(); // the writer treated the failure as non-tragic: commit what it acknowledged
        w.close();
      } else {
        w.close(); // tragic: the last commit (docs 1 and 2, dv 2) is the expected state
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        int doc1 = s.count(new TermQuery(new Term("id", "1")));
        long dv2 = -1;
        for (LeafReaderContext ctx : r.leaves()) {
          NumericDocValues dv = ctx.reader().getNumericDocValues("dv");
          LeafReader sfr = ctx.reader();
          for (int d = 0; d < ctx.reader().maxDoc(); d++) {
            if (ctx.reader().getLiveDocs() != null && ctx.reader().getLiveDocs().get(d) == false) {
              continue;
            }
            if ("2".equals(sfr.document(d).get("id")) && dv.advanceExact(d)) {
              dv2 = dv.longValue();
            }
          }
        }
        String state =
            "writer open after failure="
                + stillOpen
                + ", caller got "
                + thrown
                + "; committed: id:1 live="
                + (doc1 == 1)
                + ", dv(id:2)="
                + dv2;
        if (stillOpen) {
          assertTrue(
              "acknowledged deleteDocuments(id:1) / updateNumericDocValue(id:2, 22) lost: " + state,
              doc1 == 0 && dv2 == 22);
        } else {
          assertTrue("tragic close must keep the last commit: " + state, doc1 == 1 && dv2 == 2);
        }
      }
    }
  }
}
