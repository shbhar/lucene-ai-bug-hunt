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
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;

/**
 * {@link SortedDocValues#ordinalRangeIntoBitSet} must fill the bit set with "the doc IDs in
 * [fromDoc, toDoc) whose ordinals are in [minOrd, maxOrd]"; its javadoc says the default
 * implementation "falls back to per-doc evaluation via advanceExact and ordValue". The default
 * starts at docID() when docID() >= fromDoc and calls ordValue() there, so after
 * advanceExact(fromDoc) returned false it reads the ordinal of a document that has none. Default
 * codec, a SORTED field that some documents lack (sparse).
 */
public class TestSortedOrdinalRangeAfterAdvanceExact extends LuceneTestCase {

  private interface Layout {
    String value(int doc); // null = no value
  }

  private static DirectoryReader index(Directory dir, int n, Layout layout) throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig(null).setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int d = 0; d < n; d++) {
        Document doc = new Document();
        String v = layout.value(d);
        if (v != null) doc.add(new SortedDocValuesField("f", new BytesRef(v)));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return DirectoryReader.open(dir);
  }

  /** The per-doc loop described by the javadoc, used as the reference. */
  private static FixedBitSet reference(SortedDocValues v, int from, int to, long min, long max)
      throws IOException {
    FixedBitSet bits = new FixedBitSet(to);
    for (int doc = from; doc < to; doc++) {
      if (v.advanceExact(doc)) {
        int ord = v.ordValue();
        if (ord >= min && ord <= max) bits.set(doc);
      }
    }
    return bits;
  }

  /** doc 0: "a", doc 1: no value, doc 2: "b". Range [ord(a), ord(a)] over [1, 3) matches nothing. */
  public void testDocWithoutValueIsNotMarked() throws IOException {
    try (Directory dir = newDirectory();
        DirectoryReader r = index(dir, 3, d -> d == 1 ? null : (d == 0 ? "a" : "b"))) {
      LeafReader leaf = r.leaves().get(0).reader();
      SortedDocValues v = leaf.getSortedDocValues("f");
      int ordA = v.lookupTerm(new BytesRef("a"));
      assertFalse(v.advanceExact(1)); // docID() == 1, no value
      FixedBitSet bits = new FixedBitSet(3);
      v.ordinalRangeIntoBitSet(1, 3, ordA, ordA, bits, 0);
      FixedBitSet expected = reference(leaf.getSortedDocValues("f"), 1, 3, ordA, ordA);
      assertEquals(0, expected.cardinality());
      assertEquals("doc 1 has no value but was marked", str(expected), str(bits));
    }
  }

  /** doc 0: no value, doc 1: "a", doc 2: "b". The call fails instead of marking doc 1. */
  public void testNoPreviousDocumentWithValue() throws IOException {
    try (Directory dir = newDirectory();
        DirectoryReader r = index(dir, 3, d -> d == 0 ? null : (d == 1 ? "a" : "b"))) {
      SortedDocValues v = r.leaves().get(0).reader().getSortedDocValues("f");
      int ordA = v.lookupTerm(new BytesRef("a"));
      assertFalse(v.advanceExact(0));
      FixedBitSet bits = new FixedBitSet(3);
      v.ordinalRangeIntoBitSet(0, 3, ordA, ordA, bits, 0); // expected: only doc 1
      assertFalse(bits.get(0));
      assertTrue(bits.get(1));
      assertFalse(bits.get(2));
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
