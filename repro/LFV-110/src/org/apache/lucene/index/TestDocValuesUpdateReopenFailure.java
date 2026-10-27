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

import java.io.IOException;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Doc-values update files that ReadersAndUpdates.writeFieldUpdates has already published into a
 * SegmentCommitInfo are only checkpointed (referenced by IndexFileDeleter) when the whole write
 * returns normally. Two ways to break that: (1) an IOException while IndexWriter re-opens its pooled
 * segment reader right after writing a
 * doc-values update (ReadersAndUpdates.writeFieldUpdates -> swapNewReaderWithLatestLiveDocs) makes
 * getReader throw, but the writer stays open. The update was acknowledged; it must survive, and the
 * writer must keep working. The fault is injected through MockDirectoryWrapper, as Lucene's own
 * exception tests do (same-package test; the only non-public reference is
 * ReadersAndUpdates.class for the call-stack check).
 */
public class TestDocValuesUpdateReopenFailure extends LuceneTestCase {

  static String values(DirectoryReader reader) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (LeafReaderContext ctx : reader.leaves()) {
      NumericDocValues dv = ctx.reader().getNumericDocValues("n");
      for (int doc = 0; doc < ctx.reader().maxDoc(); doc++) {
        sb.append(dv != null && dv.advanceExact(doc) ? Long.toString(dv.longValue()) : "-");
        sb.append(' ');
      }
    }
    return sb.toString().trim();
  }

  /** Runs the scenario with the given config and checks the documented outcome. */
  static void runScenario(IndexWriterConfig iwc) throws IOException {
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    final boolean[] armed = {false};
    dir.failOn(
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (armed[0]
                && callStackContains(ReadersAndUpdates.class, "swapNewReaderWithLatestLiveDocs")) {
              armed[0] = false; // fail once
              throw new IOException("simulated I/O error while re-opening the segment reader");
            }
          }
        });
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    IndexWriter w = new IndexWriter(dir, iwc);
    for (int i = 0; i < 4; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
      doc.add(new NumericDocValuesField("n", i));
      w.addDocument(doc);
    }
    w.commit();
    DirectoryReader.open(w).close(); // the segment's reader is now pooled by the writer

    w.updateNumericDocValue(new Term("id", "0"), "n", 100);
    armed[0] = true;
    try {
      DirectoryReader.open(w).close();
    } catch (IOException e) {
      // on main and in releases this getReader fails with the injected exception; either outcome is
      // fine as long as the writer stays consistent
    }
    assertFalse("the injected fault must have fired", armed[0]);
    assertTrue("an IOException in getReader is not tragic", w.isOpen());

    // the writer is still open: later work must succeed and must keep the acknowledged update
    w.updateNumericDocValue(new Term("id", "1"), "n", 200);
    try (DirectoryReader r = DirectoryReader.open(w)) {
      assertEquals("NRT reader after the failed getReader", "100 200 2 3", values(r));
    }
    w.commit();
    w.close();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals("last commit", "100 200 2 3", values(r));
    }
    dir.close(); // CheckIndex + unreferenced-files check
  }

  /** Default IndexWriterConfig (on main: doc-values overlays enabled). */
  public void testDefaultConfig() throws IOException {
    runScenario(new IndexWriterConfig(null));
  }

  /**
   * (2) Two segments with pending updates; segment A's update is written, then the write of segment
   * B's update throws, so getReader fails. Segment A's new files were published but never
   * checkpointed. The order in which the writer visits segments depends on identity hash codes, so
   * the scenario is repeated until segment A is written first (about half the attempts).
   */
  public void testFailureInAnotherSegmentsWrite() throws IOException {
    int reached = 0;
    for (int attempt = 0; attempt < 30 && reached < 3; attempt++) {
      MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
      final boolean[] armed = {false};
      dir.failOn(
          new MockDirectoryWrapper.Failure() {
            @Override
            public void eval(MockDirectoryWrapper d) throws IOException {
              if (armed[0]
                  && callStackContains(ReadersAndUpdates.class, "writeFieldUpdates")
                  && callStackContainsAnyOf("addBinaryField")) {
                armed[0] = false; // fail once, while writing segment B's binary update
                throw new IOException("simulated I/O error while writing a doc-values update");
              }
            }
          });
      IndexWriterConfig iwc = new IndexWriterConfig(null);
      iwc.setCodec(TestUtil.getDefaultCodec());
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      IndexWriter w = new IndexWriter(dir, iwc);
      for (int seg = 0; seg < 2; seg++) {
        for (int i = 0; i < 3; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", seg + "-" + i, Field.Store.NO));
          doc.add(new NumericDocValuesField("n", i));
          doc.add(new BinaryDocValuesField("b", new BytesRef("x")));
          w.addDocument(doc);
        }
        w.commit(); // segment A = _0, segment B = _1
      }
      DirectoryReader.open(w).close();
      w.updateNumericDocValue(new Term("id", "0-0"), "n", 100); // segment A
      w.updateBinaryDocValue(new Term("id", "1-0"), "b", new BytesRef("y")); // segment B
      long genA = w.cloneSegmentInfos().info(0).getDocValuesGen();
      armed[0] = true;
      try {
        DirectoryReader.open(w).close();
      } catch (IOException e) {
        // expected: the injected fault
      }
      assertFalse("the injected fault must have fired", armed[0]);
      assertTrue("an IOException in getReader is not tragic", w.isOpen());
      boolean aWrittenFirst = w.cloneSegmentInfos().info(0).getDocValuesGen() != genA;
      if (aWrittenFirst == false) {
        w.rollback();
        dir.setCheckIndexOnClose(false);
        dir.close();
        continue;
      }
      reached++;
      // the writer is open and consistent: later work must succeed
      w.updateNumericDocValue(new Term("id", "1-1"), "n", 7);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals("NRT reader after the failed getReader", "100 1 2 0 7 2", values(r));
      }
      w.commit();
      w.close();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals("last commit", "100 1 2 0 7 2", values(r));
      }
      dir.close();
    }
    assumeTrue("segment A was never written first", reached > 0);
  }
}
