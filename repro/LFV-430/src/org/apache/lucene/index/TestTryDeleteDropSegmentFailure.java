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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * tryDeleteDocument deletes the last live document of a segment: IndexWriter removes the
 * segment from its SegmentInfos and then closes the segment's pooled reader. If closing that
 * reader throws (an IOException from IndexInput#close), the exception escapes before
 * tryDeleteDocument calls checkpoint() and changed(). The writer stays open, the segment is gone
 * from what IndexWriter serves to NRT readers, but the writer does not count the change, so
 * commit() and close() find nothing to commit and the deleted document is back after a reopen.
 */
public class TestTryDeleteDropSegmentFailure extends LuceneTestCase {

  private static Document doc(String id, long num) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new NumericDocValuesField("num", num));
    return d;
  }

  private static int count(IndexReader r, String id) throws IOException {
    return new IndexSearcher(r).count(new TermQuery(new Term("id", id)));
  }

  public void testFailedSegmentDropIsCommittedOrRolledBack() throws IOException {
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    IndexWriter w = new IndexWriter(dir, iwc);
    w.addDocument(doc("a", 1));
    w.commit(); // segment _0 = [a]
    w.addDocument(doc("b", 2));
    w.commit(); // segment _1 = [b]

    DirectoryReader oldReader = DirectoryReader.open(w);
    // give _0 a doc-values update generation that only the writer's pooled reader holds open
    w.updateNumericDocValue(new Term("id", "a"), "num", 10);
    w.commit();

    final boolean[] fired = {false};
    MockDirectoryWrapper.Failure failure =
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (doFail
                && callStackContains(ReaderPool.class, "drop")
                && callStackContains(IndexWriter.class, "tryDeleteDocument")) {
              doFail = false;
              fired[0] = true;
              throw new IOException("simulated failure closing the dropped segment's reader");
            }
          }
        };
    dir.failOn(failure);
    failure.setDoFail();

    // doc 0 of oldReader is "a", the only document of segment _0
    assertEquals("a", oldReader.storedFields().document(0).get("id"));
    IOException e = expectThrows(IOException.class, () -> w.tryDeleteDocument(oldReader, 0));
    assertEquals("simulated failure closing the dropped segment's reader", e.getMessage());
    assertTrue("the injected failure fired", fired[0]);
    oldReader.close();

    // the failure was not tragic: the writer is open
    assertNull(w.getTragicException());
    assertTrue(w.isOpen());

    // what IndexWriter now serves to a new NRT reader ...
    int nrtCount;
    try (DirectoryReader nrt = DirectoryReader.open(w)) {
      nrtCount = count(nrt, "a");
    }
    // ... must be what it commits
    w.commit();
    w.close();
    int committedCount;
    try (DirectoryReader committed = DirectoryReader.open(dir)) {
      committedCount = count(committed, "a");
    }
    assertEquals(
        "document 'a': visible in an NRT reader of the open writer (count="
            + nrtCount
            + ") vs. in the index committed by that writer (count="
            + committedCount
            + ")",
        nrtCount,
        committedCount);
    dir.close();
  }
}
