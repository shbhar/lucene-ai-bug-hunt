/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * End to end, default IndexWriterConfig with the default codec (pinned, since LuceneTestCase
 * randomizes Codec.getDefault()): a sparse numeric doc-values field whose last value is on
 * doc 2,000,000,000 (below IndexWriter.MAX_DOCS). Empty documents fill the gap, so all documents
 * stay in one in-memory segment. The doc-values writer tracks the documents that have a value in a
 * DocsWithFieldSet, which grows its FixedBitSet with FixedBitSet#ensureCapacity.
 *
 * <p>Runtime: about 2 billion addDocument calls (roughly 10 minutes on one core).
 */
public class TestSparseDocValuesBeyond1_91BillionDocs extends LuceneTestCase {

  public void testValuesSurviveFlush() throws Exception {
    final int lastDoc = 2_000_000_000;
    try (Directory dir = FSDirectory.open(createTempDir())) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        Document empty = new Document();
        Document d = new Document();
        NumericDocValuesField f = new NumericDocValuesField("f", 0);
        d.add(f);
        f.setLongValue(42);
        w.addDocument(d); // doc 0
        w.addDocument(empty); // doc 1
        f.setLongValue(43);
        w.addDocument(d); // doc 2: not dense any more, DocsWithFieldSet uses a FixedBitSet
        for (int doc = 3; doc < lastDoc; doc++) {
          w.addDocument(empty);
        }
        f.setLongValue(44);
        w.addDocument(d); // doc 2,000,000,000
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(lastDoc + 1, r.maxDoc());
        assertEquals("expected a single segment", 1, r.leaves().size());
        NumericDocValues dv = r.leaves().get(0).reader().getNumericDocValues("f");
        assertNotNull(dv);
        assertTrue("doc 0 lost its value", dv.advanceExact(0));
        assertEquals(42, dv.longValue());
        assertTrue("doc 2 lost its value", dv.advanceExact(2));
        assertEquals(43, dv.longValue());
        assertTrue("doc " + lastDoc + " lost its value", dv.advanceExact(lastDoc));
        assertEquals(44, dv.longValue());
      }
    }
  }
}
