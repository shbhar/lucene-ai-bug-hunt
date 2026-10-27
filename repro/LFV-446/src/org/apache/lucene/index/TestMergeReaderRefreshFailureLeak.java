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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * A merge needs a reader of each merging segment with its latest deletes. When the segment has
 * pending deletes that its pooled reader does not reflect, ReadersAndUpdates#getReaderForMerge
 * builds a new SegmentReader from the pooled one. If building that reader fails (here: an
 * IOException opening the segment's doc-values-update field infos), the merge fails and IndexWriter
 * closes itself, as it should, but the reference that getReaderForMerge took on the pooled reader
 * is never released, so the pooled reader and its files stay open after the writer is closed.
 */
public class TestMergeReaderRefreshFailureLeak extends LuceneTestCase {

  private static Document doc(String id, long num) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new NumericDocValuesField("num", num));
    return d;
  }

  public void testFailedMergeReaderRefreshReleasesPooledReader() throws IOException {
    MockDirectoryWrapper dir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(new LogDocMergePolicy()); // mergeFactor 10: no automatic merge here
    iwc.setMergeScheduler(new SerialMergeScheduler());
    IndexWriter w = new IndexWriter(dir, iwc);

    w.addDocument(doc("a", 1));
    w.addDocument(doc("b", 2));
    w.commit(); // segment _0
    w.addDocument(doc("c", 3));
    w.commit(); // segment _1
    // a doc-values update gives segment _0 a field-infos generation
    w.updateNumericDocValue(new Term("id", "a"), "num", 10);
    w.commit();
    // a delete that the pooled reader of _0 does not reflect yet
    w.deleteDocuments(new Term("id", "b"));

    final boolean[] fired = {false};
    MockDirectoryWrapper.Failure failure =
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (doFail
                && callStackContains(ReadersAndUpdates.class, "getReaderForMerge")
                && callStackContains(SegmentReader.class, "initFieldInfos")) {
              doFail = false;
              fired[0] = true;
              throw new IOException("simulated failure opening the merge reader's field infos");
            }
          }
        };
    dir.failOn(failure);
    failure.setDoFail();

    IOException e = expectThrows(IOException.class, () -> w.forceMerge(1));
    assertEquals("simulated failure opening the merge reader's field infos", e.getMessage());
    assertTrue("the injected failure fired", fired[0]);
    // a failed merge is tragic: the writer closed itself and must have released its files
    assertNotNull(w.getTragicException());
    assertFalse(w.isOpen());
    w.close();

    long open = dir.getFileHandleCount();
    if (open != 0) {
      RuntimeException leak = expectThrows(RuntimeException.class, dir::close);
      fail(
          "IndexWriter closed itself after the failed merge but left "
              + open
              + " file handle(s) open: "
              + leak.getMessage());
    }
    dir.close();
  }
}
