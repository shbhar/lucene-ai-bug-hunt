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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.Arrays;
import org.apache.lucene.codecs.lucene102.Lucene102BinaryQuantizedVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnByteVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * (10.2-10.3 variant) Lucene102BinaryQuantizedVectorsFormat (a registered, stand-alone KnnVectorsFormat) accepts BYTE
 * vector fields and searches float32/float16 fields by brute force, but its byte search is a
 * no-op. The same kNN query then returns hits only when a filter is present, and a byte
 * similarity query returns nothing.
 */
public class TestFlatQuantizedFormatByteSearch extends LuceneTestCase {

  private static final String FIELD = "v";
  private static final byte[] TARGET = {3, 1};

  private static Directory index() throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.alwaysKnnVectorsFormat(new Lucene102BinaryQuantizedVectorsFormat()));
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 10; i++) {
        Document doc = new Document();
        doc.add(new KnnByteVectorField(FIELD, new byte[] {(byte) i, 1}, VectorSimilarityFunction.EUCLIDEAN));
        doc.add(new StringField("all", "1", Field.Store.NO));
        w.addDocument(doc);
      }
    }
    return dir;
  }

  private static int[] docs(TopDocs td) {
    return Arrays.stream(td.scoreDocs).mapToInt(sd -> sd.doc).toArray();
  }

  public void testKnnByteQueryWithAndWithoutFilter() throws IOException {
    try (Directory dir = index();
        IndexReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Query matchesEverything = new TermQuery(new Term("all", "1"));
      TopDocs filtered = searcher.search(new KnnByteVectorQuery(FIELD, TARGET, 3, matchesEverything), 10);
      TopDocs unfiltered = searcher.search(new KnnByteVectorQuery(FIELD, TARGET, 3), 10);
      // brute force: doc 3 (distance 0), then docs 2 and 4 (distance 1)
      assertArrayEquals(new int[] {3, 2, 4}, docs(filtered));
      assertArrayEquals("unfiltered kNN must return the same hits", docs(filtered), docs(unfiltered));
    }
  }

  public void testByteSimilarityQuery() throws IOException {
    try (Directory dir = index();
        IndexReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      // every EUCLIDEAN similarity is > 0, so a threshold of 0 matches all 10 documents
      assertEquals(10, searcher.count(new ByteVectorSimilarityQuery(FIELD, TARGET, 0f)));
    }
  }
}
