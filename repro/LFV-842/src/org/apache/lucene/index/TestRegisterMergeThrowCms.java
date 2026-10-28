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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;

/**
 * IndexWriter.registerMerge adds the merge to pendingMerges and only then logs and marks its
 * segments as merging. If the InfoStream (public extension point) throws in between, the merge
 * stays queued but half registered: its segments are not marked as merging, so the next merge
 * selection can pick the same segments again.
 *
 * <p>Here the throw is an exception from forceMergeDeletes(), which does not treat it as tragic,
 * so the writer stays open; the next forceMerge(1) then runs two merges over the same segments.
 * Public API only; SerialMergeScheduler keeps the order deterministic.
 */
public class TestRegisterMergeThrowCms extends LuceneTestCase {

  private static final class ThrowOnceAtRegister extends InfoStream {
    final AtomicBoolean armed = new AtomicBoolean();
    final Throwable toThrow;

    ThrowOnceAtRegister(Throwable toThrow) {
      this.toThrow = toThrow;
    }

    @Override
    public void message(String component, String message) {
      if ("IW".equals(component)
          && message.startsWith("add merge to pendingMerges")
          && armed.compareAndSet(true, false)) {
        if (toThrow instanceof Error e) throw e;
        throw (RuntimeException) toThrow;
      }
    }

    @Override
    public boolean isEnabled(String component) {
      return "IW".equals(component);
    }

    @Override
    public void close() {}
  }

  private void run(Throwable toThrow) throws Exception {
    ThrowOnceAtRegister infoStream = new ThrowOnceAtRegister(toThrow);
    try (Directory dir = new ByteBuffersDirectory()) {
      LogDocMergePolicy mp = new LogDocMergePolicy();
      mp.setMergeFactor(10); // no natural merges for these few segments
      IndexWriter w =
          new IndexWriter(
              dir,
              new IndexWriterConfig(new MockAnalyzer(random()))
                  .setMergePolicy(mp)

                  .setInfoStream(infoStream));
      int id = 0;
      for (int seg = 0; seg < 3; seg++) {
        for (int i = 0; i < 4; i++) {
          Document d = new Document();
          d.add(new StringField("id", Integer.toString(id++), Field.Store.YES));
          w.addDocument(d);
        }
        w.flush();
      }
      w.deleteDocuments(new Term("id", "0"));
      w.deleteDocuments(new Term("id", "4"));
      w.commit(); // 3 segments, two of them with a deletion, 10 live docs
      infoStream.armed.set(true);
      Throwable first = expectThrows(Throwable.class, w::forceMergeDeletes);
      assertSame(toThrow, first);
      List<String> problems = new ArrayList<>();
      if (toThrow instanceof Error && w.isOpen()) {
        // IndexWriter class javadoc: "if you hit an Error ... IndexWriter will close itself"
        problems.add("IndexWriter did not close itself after an Error (getTragicException()=" + w.getTragicException() + ")");
      }
      if (w.isOpen()) {
        // the writer is still open, so the application may keep using it: it must keep working
        try {
          w.forceMerge(1);
          w.commit();
          w.close();
        } catch (Throwable t) {
          problems.add(
              "forceMerge(1)/commit on a healthy index failed: "
                  + t
                  + (t.getStackTrace().length > 0 ? " at " + t.getStackTrace()[0] : ""));
          w.rollback();
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        // the last commit had 10 live documents; nothing was added or deleted since
        if (r.numDocs() != 10 || r.maxDoc() - r.numDeletedDocs() != 10) {
          int copiesOfId1 = new IndexSearcher(r).count(new TermQuery(new Term("id", "1")));
          problems.add(
              "committed index has numDocs="
                  + r.numDocs()
                  + " (expected 10) in "
                  + r.leaves().size()
                  + " segment(s); copies of document id:1 = "
                  + copiesOfId1);
        }
      }
      assertTrue(String.join("; ", problems), problems.isEmpty());
    }
  }

  /** An Error from the InfoStream while forceMergeDeletes registers its merge. */
  public void testErrorInRegisterMergeFromForceMergeDeletes() throws Exception {
    run(new OutOfMemoryError("simulated allocation failure while logging"));
  }

  /** An unchecked exception at the same point: not tragic, the writer stays open and must keep working. */
  public void testExceptionInRegisterMergeThenForceMerge() throws Exception {
    run(new IllegalStateException("logging backend failed"));
  }
}
