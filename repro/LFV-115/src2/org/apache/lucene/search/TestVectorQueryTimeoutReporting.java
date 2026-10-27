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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
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
 * IndexSearcher#timedOut(): "Returns true if any search hit the timeout." Vector queries check the
 * searcher's timeout during rewrite (kNN) or scorerSupplier (similarity) and return partial,
 * possibly empty, results. When nothing was found before the timeout, the search returns 0 hits
 * and timedOut() stays false, so a timed-out search looks like a search without matches. A term
 * query over the same index under the same timeout returns 0 hits with timedOut() == true.
 */
public class TestVectorQueryTimeoutReporting extends LuceneTestCase {

  private static Directory index() throws Exception {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      for (int i = 0; i < 100; i++) {
        Document d = new Document();
        d.add(new StringField("f", "t", Field.Store.NO));
        d.add(new KnnFloatVectorField("v", new float[] {i, 1}, VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(d);
      }
    }
    return dir;
  }

  private static void assertReported(IndexReader reader, Query query) throws Exception {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null);
    assertTrue(searcher.search(query, 10).scoreDocs.length > 0); // matches without a timeout
    searcher.setTimeout(() -> true); // every timeout check says: exit
    TopDocs td = searcher.search(query, 10);
    assertTrue(
        query + ": " + td.scoreDocs.length + " hits but timedOut() == false",
        td.scoreDocs.length > 0 || searcher.timedOut());
  }

  /** Control: a term query that matches; the search loop reports the timeout. */
  public void testTermQueryControl() throws Exception {
    try (Directory dir = index();
        IndexReader reader = DirectoryReader.open(dir)) {
      assertReported(reader, new TermQuery(new Term("f", "t")));
    }
  }

  public void testKnnQuery() throws Exception {
    try (Directory dir = index();
        IndexReader reader = DirectoryReader.open(dir)) {
      assertReported(reader, new KnnFloatVectorQuery("v", new float[] {0, 1}, 5));
    }
  }

  public void testSimilarityQuery() throws Exception {
    try (Directory dir = index();
        IndexReader reader = DirectoryReader.open(dir)) {
      // graph search in scorerSupplier, which honours the timeout (pre-10.5 constructor)
      assertReported(reader, new FloatVectorSimilarityQuery("v", new float[] {0, 1}, 0f));
    }
  }
}
