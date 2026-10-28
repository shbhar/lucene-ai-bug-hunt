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

import org.apache.lucene.backward_codecs.lucene80.Lucene80DocValuesFormat;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/** Variant triggers for MatchingFacetSetsCounts reading binary doc values from bytes[0]. */
public class TestFacetSetsSliceVariantsV3 extends LuceneTestCase {

  private static String render(MatchingFacetSetsCounts counts) throws Exception {
    StringBuilder sb = new StringBuilder();
    for (var lv : counts.getAllChildren("fs").labelValues) {
      sb.append(lv.label).append('=').append(lv.value).append(' ');
    }
    return sb.toString().trim();
  }

  /** V1: plain BinaryDocValuesField (no field reuse API) holding the documented encoding. */
  public void testPlainBinaryDocValuesFieldSliceInMemoryIndex() throws Exception {
    BytesRef first = FacetSetsField.create("fs", new LongFacetSet(1, 2)).binaryValue();
    BytesRef second = FacetSetsField.create("fs", new LongFacetSet(3, 4)).binaryValue();
    byte[] buffer = new byte[first.length + second.length];
    System.arraycopy(first.bytes, first.offset, buffer, 0, first.length);
    System.arraycopy(second.bytes, second.offset, buffer, first.length, second.length);
    BytesRef slice = new BytesRef(buffer, first.length, second.length);
    MemoryIndex mi = new MemoryIndex();
    mi.addField(new BinaryDocValuesField("fs", slice), null);
    IndexSearcher searcher = mi.createSearcher();
    FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
    MatchingFacetSetsCounts counts =
        new MatchingFacetSetsCounts(
            "fs",
            fc,
            FacetSetDecoder::decodeLongs,
            new ExactFacetSetMatcher("(3,4)", new LongFacetSet(3, 4)),
            new ExactFacetSetMatcher("(1,2)", new LongFacetSet(1, 2)));
    assertEquals("(3,4)=1 (1,2)=0", render(counts));
  }

  /** V2: int facet sets with decodeInts, same slice trigger. */
  public void testDecodeIntsSliceInMemoryIndex() throws Exception {
    BytesRef first = FacetSetsField.create("fs", new IntFacetSet(1, 2)).binaryValue();
    BytesRef second = FacetSetsField.create("fs", new IntFacetSet(3, 4)).binaryValue();
    byte[] buffer = new byte[first.length + second.length];
    System.arraycopy(first.bytes, first.offset, buffer, 0, first.length);
    System.arraycopy(second.bytes, second.offset, buffer, first.length, second.length);
    BytesRef slice = new BytesRef(buffer, first.length, second.length);
    MemoryIndex mi = new MemoryIndex();
    mi.addField(new BinaryDocValuesField("fs", slice), null);
    IndexSearcher searcher = mi.createSearcher();
    FacetsCollector fc = searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
    MatchingFacetSetsCounts counts =
        new MatchingFacetSetsCounts(
            "fs",
            fc,
            FacetSetDecoder::decodeInts,
            new ExactFacetSetMatcher("(3,4)", new IntFacetSet(3, 4)),
            new ExactFacetSetMatcher("(1,2)", new IntFacetSet(1, 2)));
    assertEquals("(3,4)=1 (1,2)=0", render(counts));
  }

  /**
   * V3: only FacetSetsField.create values (offset 0 on the user side), written through IndexWriter
   * with a stock, SPI-registered, writable doc-values format whose BEST_COMPRESSION reader returns
   * each value as a slice of a decompressed block.
   */
  public void testStockCompressedDocValuesFormatReturnsSlices() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(
          TestUtil.alwaysDocValuesFormat(
              new Lucene80DocValuesFormat(Lucene80DocValuesFormat.Mode.BEST_COMPRESSION)));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document d0 = new Document();
        d0.add(FacetSetsField.create("fs", new LongFacetSet(1, 2)));
        w.addDocument(d0);
        Document d1 = new Document();
        d1.add(FacetSetsField.create("fs", new LongFacetSet(3, 4)));
        w.addDocument(d1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        LeafReader leaf = r.leaves().get(0).reader();
        BinaryDocValues dv = leaf.getBinaryDocValues("fs");
        assertTrue(dv.advanceExact(1));
        BytesRef v1 = dv.binaryValue();
        assertEquals(
            BytesRef.deepCopyOf(FacetSetsField.create("fs", new LongFacetSet(3, 4)).binaryValue()),
            v1);
        System.out.println("V3: doc 1 binaryValue offset=" + v1.offset + " length=" + v1.length);
        IndexSearcher searcher = new IndexSearcher(r);
        FacetsCollector fc =
            searcher.search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        MatchingFacetSetsCounts counts =
            new MatchingFacetSetsCounts(
                "fs",
                fc,
                FacetSetDecoder::decodeLongs,
                new ExactFacetSetMatcher("(3,4)", new LongFacetSet(3, 4)),
                new ExactFacetSetMatcher("(1,2)", new LongFacetSet(1, 2)));
        assertEquals("(3,4)=1 (1,2)=1", render(counts));
      }
    }
  }
}
