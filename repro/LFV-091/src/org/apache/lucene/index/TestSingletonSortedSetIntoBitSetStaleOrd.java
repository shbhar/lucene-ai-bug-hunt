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
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.search.DocValuesRangeIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;

/**
 * The single-valued view of a SortedSetDocValues field (DocValues.singleton, returned by
 * DocValues.getSortedSet for a segment where every document has at most one value) caches the
 * ordinal of the current document. intoBitSet moves the iterator to the first document at or after
 * upTo but does not refresh that cache, so nextOrd() then returns the ordinal of an earlier document.
 */
public class TestSingletonSortedSetIntoBitSetStaleOrd extends LuceneTestCase {

  /** Root cause: nextOrd() after intoBitSet() returns the previous document's ordinal. */
  public void testNextOrdAfterIntoBitSet() throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String v : new String[] {"a", "b"}) {
          Document doc = new Document();
          doc.add(new SortedSetDocValuesField("f", new BytesRef(v)));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader leaf = getOnlyLeafReader(r);
        SortedSetDocValues values = DocValues.getSortedSet(leaf, "f");
        long ordB = values.lookupTerm(new BytesRef("b"));
        assertEquals(0, values.nextDoc());
        values.intoBitSet(1, new FixedBitSet(2), 0); // loads doc 0, leaves the iterator on doc 1
        assertEquals(1, values.docID());
        assertEquals(1, values.docValueCount());
        assertEquals("ordinal of doc 1 after intoBitSet", ordB, values.nextOrd());
      }
    }
  }

  /**
   * Consequence: DocValuesRangeIterator.forOrdinalRange (public API) given the SortedSetDocValues
   * of a single-valued segment marks a non-matching document. A sparse skip-index block whose
   * values are all in range is loaded with intoBitSet; the first document of the next block is
   * then tested with the stale ordinal.
   */
  public void testForOrdinalRangeIntoBitSet() throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      final int half = 8192; // docs [0, half): "b" on even docs only (4096 docs with a value)
      final int maxDoc = 2 * half; // docs [half, maxDoc): "a" on even docs, "b" on odd docs
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < maxDoc; i++) {
          Document doc = new Document();
          if (i < half) {
            if (i % 2 == 0) {
              doc.add(SortedSetDocValuesField.indexedField("f", new BytesRef("b")));
            }
          } else {
            doc.add(SortedSetDocValuesField.indexedField("f", new BytesRef(i % 2 == 0 ? "a" : "b")));
          }
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader leaf = getOnlyLeafReader(r);
        SortedSetDocValues values = DocValues.getSortedSet(leaf, "f");
        long ordB = values.lookupTerm(new BytesRef("b"));
        DocValuesRangeIterator it =
            DocValuesRangeIterator.forOrdinalRange(
                values, leaf.getDocValuesSkipper("f"), ordB, ordB);
        FixedBitSet actual = new FixedBitSet(maxDoc);
        it.approximation().nextDoc();
        it.intoBitSet(maxDoc, actual, 0);
        FixedBitSet expected = new FixedBitSet(maxDoc);
        for (int i = 0; i < maxDoc; i++) {
          if (i < half ? i % 2 == 0 : i % 2 == 1) {
            expected.set(i);
          }
        }
        FixedBitSet diff = expected.clone();
        diff.xor(actual);
        int first = diff.nextSetBit(0);
        assertEquals(
            "docs that differ (first: "
                + (first == org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS ? "none" : first)
                + ", value \"a\" is not in [b, b])",
            0,
            diff.cardinality());
      }
    }
  }
}
