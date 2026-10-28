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

import java.io.IOException;
import org.apache.lucene.backward_codecs.lucene80.Lucene80DocValuesFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterBinaryDocValues;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;

/**
 * A reader may return a binary doc value as a slice (offset > 0) of a larger array. Doc 0 holds the
 * facet set (1,2) and doc 1 holds (3,4), so each matcher must count exactly one document, whatever
 * the offset of the values.
 */
public class TestMatchingFacetSetsCountsSlicedValues extends LuceneTestCase {

  private static final String EXPECTED = "(1,2)=1 (3,4)=1";

  /** Control: the default codec returns every value at offset 0. */
  public void testDefaultCodec() throws Exception {
    try (Directory dir = newDirectory()) {
      index(dir, new IndexWriterConfig());
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(EXPECTED, counts(reader));
      }
    }
  }

  /** The Lucene80 BEST_COMPRESSION reader returns each value as a slice of a decompressed block. */
  public void testCompressedDocValuesFormat() throws Exception {
    try (Directory dir = newDirectory()) {
      Lucene80DocValuesFormat format =
          new Lucene80DocValuesFormat(Lucene80DocValuesFormat.Mode.BEST_COMPRESSION);
      index(dir, new IndexWriterConfig().setCodec(TestUtil.alwaysDocValuesFormat(format)));
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        BinaryDocValues values = getOnlyLeafReader(reader).getBinaryDocValues("fs");
        assertTrue(values.advanceExact(1));
        assertEquals("doc 1's value follows doc 0's 20 bytes", 20, values.binaryValue().offset);
        assertEquals(EXPECTED, counts(reader));
      }
    }
  }

  /** A reader that serves all values of a field from one shared array, as slices of it. */
  public void testReaderServingValuesFromOneArray() throws Exception {
    try (Directory dir = newDirectory()) {
      index(dir, new IndexWriterConfig());
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(EXPECTED, counts(new OneArrayReader(getOnlyLeafReader(reader))));
      }
    }
  }

  private static void index(Directory dir, IndexWriterConfig config) throws IOException {
    try (IndexWriter writer = new IndexWriter(dir, config)) {
      for (LongFacetSet set : new LongFacetSet[] {new LongFacetSet(1, 2), new LongFacetSet(3, 4)}) {
        Document doc = new Document();
        doc.add(FacetSetsField.create("fs", set));
        writer.addDocument(doc);
      }
    }
  }

  private static String counts(IndexReader reader) throws IOException {
    IndexSearcher searcher = new IndexSearcher(reader);
    FacetsCollector hits =
        searcher.search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
    MatchingFacetSetsCounts counts =
        new MatchingFacetSetsCounts(
            "fs",
            hits,
            FacetSetDecoder::decodeLongs,
            new ExactFacetSetMatcher("(1,2)", new LongFacetSet(1, 2)),
            new ExactFacetSetMatcher("(3,4)", new LongFacetSet(3, 4)));
    StringBuilder sb = new StringBuilder();
    for (LabelAndValue lv : counts.getAllChildren("fs").labelValues) {
      sb.append(lv.label).append('=').append(lv.value).append(' ');
    }
    return sb.toString().trim();
  }

  private static final class OneArrayReader extends FilterLeafReader {
    OneArrayReader(LeafReader in) {
      super(in);
    }

    @Override
    public BinaryDocValues getBinaryDocValues(String field) throws IOException {
      BinaryDocValues loader = in.getBinaryDocValues(field);
      if (loader == null) {
        return null;
      }
      BytesRefBuilder all = new BytesRefBuilder();
      int[] start = new int[maxDoc()];
      int[] length = new int[maxDoc()];
      for (int doc = loader.nextDoc();
          doc != DocIdSetIterator.NO_MORE_DOCS;
          doc = loader.nextDoc()) {
        BytesRef value = loader.binaryValue();
        start[doc] = all.length();
        length[doc] = value.length;
        all.append(value);
      }
      byte[] shared = all.bytes();
      return new FilterBinaryDocValues(in.getBinaryDocValues(field)) {
        @Override
        public BytesRef binaryValue() {
          return new BytesRef(shared, start[docID()], length[docID()]);
        }
      };
    }

    @Override
    public IndexReader.CacheHelper getCoreCacheHelper() {
      return null;
    }

    @Override
    public IndexReader.CacheHelper getReaderCacheHelper() {
      return null;
    }
  }
}
