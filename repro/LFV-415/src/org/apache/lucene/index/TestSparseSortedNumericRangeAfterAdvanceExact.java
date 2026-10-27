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
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.FixedBitSet;

/**
 * {@link SortedNumericDocValues#rangeIntoBitSet} must fill the bit set with "the doc IDs in
 * [fromDoc, toDoc) whose sorted numeric values contain at least one value in [minValue, maxValue]".
 * Its default implementation evaluates every doc with {@code advanceExact(doc)}, so it may be
 * called while the iterator sits on {@code fromDoc} after {@code advanceExact(fromDoc)} returned
 * false ({@code fromDoc >= docID()}; {@link DocValuesIterator#advanceExact} forbids only intoBitSet
 * and docIDRunEnd in that state). On the default codec, a multi-valued field that some documents
 * lack (sparse), the override treats the current document as one that has values.
 */
public class TestSparseSortedNumericRangeAfterAdvanceExact extends LuceneTestCase {

  private static DirectoryReader index(Directory dir) throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig(null).setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      // doc 0: {1, 3}   doc 1: no value   doc 2: {5, 7}
      for (int d = 0; d < 3; d++) {
        Document doc = new Document();
        if (d != 1) {
          doc.add(new SortedNumericDocValuesField("f", d == 0 ? 1 : 5));
          doc.add(new SortedNumericDocValuesField("f", d == 0 ? 3 : 7));
        }
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return DirectoryReader.open(dir);
  }

  /** The documented per-doc loop, used as the reference. */
  private static FixedBitSet reference(SortedNumericDocValues v, int from, int to, long min, long max)
      throws IOException {
    FixedBitSet bits = new FixedBitSet(to);
    for (int doc = from; doc < to; doc++) {
      if (v.advanceExact(doc)) {
        for (int i = 0, c = v.docValueCount(); i < c; i++) {
          long x = v.nextValue();
          if (x >= min) {
            if (x <= max) bits.set(doc);
            break;
          }
        }
      }
    }
    return bits;
  }

  /** Range [1, 3] matches only doc 0; doc 1 (no value) is marked with doc 0's values. */
  public void testDocWithoutValueIsNotMarked() throws IOException {
    try (Directory dir = newDirectory();
        DirectoryReader r = index(dir)) {
      LeafReader leaf = r.leaves().get(0).reader();
      SortedNumericDocValues v = leaf.getSortedNumericDocValues("f");
      assertFalse(v.advanceExact(1)); // doc 1 has no value; docID() == 1
      FixedBitSet bits = new FixedBitSet(3);
      v.rangeIntoBitSet(1, 3, 1, 3, bits, 0);
      FixedBitSet expected =
          reference(leaf.getSortedNumericDocValues("f"), 1, 3, 1, 3); // fresh iterator
      assertEquals("no doc in [1, 3) has a value in [1, 3]", 0, expected.cardinality());
      assertEquals("doc 1 has no value but was marked", str(expected), str(bits));
    }
  }

  /** advanceExact(0) == false on a field whose first document lacks values: the call throws. */
  public void testNoPreviousDocumentWithValues() throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(null).setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        // doc 0: no value   doc 1: {1, 3}   doc 2: {5, 7}
        for (int d = 0; d < 3; d++) {
          Document doc = new Document();
          if (d != 0) {
            doc.add(new SortedNumericDocValuesField("f", d == 1 ? 1 : 5));
            doc.add(new SortedNumericDocValuesField("f", d == 1 ? 3 : 7));
          }
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        SortedNumericDocValues v = r.leaves().get(0).reader().getSortedNumericDocValues("f");
        assertFalse(v.advanceExact(0));
        FixedBitSet bits = new FixedBitSet(3);
        v.rangeIntoBitSet(0, 3, 1, 3, bits, 0); // expected: only doc 1
        assertFalse(bits.get(0));
        assertTrue(bits.get(1));
        assertFalse(bits.get(2));
      }
    }
  }

  private static String str(FixedBitSet bits) {
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i < bits.length(); i++) {
      if (bits.get(i)) sb.append(sb.length() > 1 ? "," : "").append(i);
    }
    return sb.append('}').toString();
  }
}
