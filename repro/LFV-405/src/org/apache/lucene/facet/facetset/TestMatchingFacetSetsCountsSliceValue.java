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
package org.apache.lucene.facet.facetset;

import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * MatchingFacetSetsCounts reads the facet-set binary doc value from position 0 of
 * BytesRef.bytes instead of BytesRef.offset, so a value that a reader returns as a slice of a
 * larger array is decoded from the wrong bytes.
 */
public class TestMatchingFacetSetsCountsSliceValue extends LuceneTestCase {

  /** counts of the matchers (3,4) and (1,2) for one document whose facet-set value is given */
  private static String counts(BytesRef value) throws Exception {
    // a FacetSetsField whose value is (re)set with the expert field-reuse API Field#setBytesValue,
    // which takes the BytesRef as is ("the provided BytesRef is not copied")
    FacetSetsField field = FacetSetsField.create("fs", new LongFacetSet(0, 0));
    field.setBytesValue(value);
    MemoryIndex mi = new MemoryIndex();
    mi.addField(field, null);
    IndexSearcher searcher = mi.createSearcher();
    LeafReader leaf = searcher.getIndexReader().leaves().get(0).reader();
    BinaryDocValues dv = leaf.getBinaryDocValues("fs");
    assertTrue(dv.advanceExact(0));
    assertEquals("the reader returns the value as given", value, dv.binaryValue());
    FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
    MatchingFacetSetsCounts counts =
        new MatchingFacetSetsCounts(
            "fs",
            fc,
            FacetSetDecoder::decodeLongs,
            new ExactFacetSetMatcher("(3,4)", new LongFacetSet(3, 4)),
            new ExactFacetSetMatcher("(1,2)", new LongFacetSet(1, 2)));
    StringBuilder sb = new StringBuilder();
    for (var lv : counts.getAllChildren("fs").labelValues) {
      sb.append(lv.label).append('=').append(lv.value).append(' ');
    }
    return sb.toString().trim();
  }

  private static BytesRef encode(long... values) {
    return BytesRef.deepCopyOf(FacetSetsField.create("fs", new LongFacetSet(values)).binaryValue());
  }

  /** two values packed back to back in one buffer; the document's value is the second one */
  public void testValueSlicedFromSharedBufferIsCountedAsItself() throws Exception {
    BytesRef first = encode(1, 2);
    BytesRef second = encode(3, 4);
    assertEquals("(3,4)=1 (1,2)=0", counts(second)); // control: compact value

    byte[] buffer = new byte[first.length + second.length];
    System.arraycopy(first.bytes, 0, buffer, 0, first.length);
    System.arraycopy(second.bytes, 0, buffer, first.length, second.length);
    BytesRef slice = new BytesRef(buffer, first.length, second.length);
    assertEquals(second, slice);

    assertEquals(
        "the document's facet set is (3,4); it was given as a BytesRef slice of a shared buffer",
        "(3,4)=1 (1,2)=0",
        counts(slice));
  }

  /** control: the same field value indexed with IndexWriter (which copies it) is counted right */
  public void testSameSliceThroughIndexWriterIsCountedAsItself() throws Exception {
    BytesRef first = encode(1, 2);
    BytesRef second = encode(3, 4);
    byte[] buffer = new byte[first.length + second.length];
    System.arraycopy(first.bytes, 0, buffer, 0, first.length);
    System.arraycopy(second.bytes, 0, buffer, first.length, second.length);
    BytesRef slice = new BytesRef(buffer, first.length, second.length);
    FacetSetsField field = FacetSetsField.create("fs", new LongFacetSet(0, 0));
    field.setBytesValue(slice);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        doc.add(field);
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(r);
        FacetsCollector fc =
            searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        MatchingFacetSetsCounts counts =
            new MatchingFacetSetsCounts(
                "fs",
                fc,
                FacetSetDecoder::decodeLongs,
                new ExactFacetSetMatcher("(3,4)", new LongFacetSet(3, 4)),
                new ExactFacetSetMatcher("(1,2)", new LongFacetSet(1, 2)));
        StringBuilder sb = new StringBuilder();
        for (var lv : counts.getAllChildren("fs").labelValues) {
          sb.append(lv.label).append('=').append(lv.value).append(' ');
        }
        assertEquals("(3,4)=1 (1,2)=0", sb.toString().trim());
      }
    }
  }

  /** the same value at offset 5 of a zero-filled array */
  public void testValueSlicedFromZeroFilledArray() throws Exception {
    BytesRef value = encode(3, 4);
    byte[] backing = new byte[5 + value.length + 3];
    System.arraycopy(value.bytes, 0, backing, 5, value.length);
    BytesRef slice = new BytesRef(backing, 5, value.length);
    assertEquals(value, slice);
    assertEquals("(3,4)=1 (1,2)=0", counts(slice));
  }
}
