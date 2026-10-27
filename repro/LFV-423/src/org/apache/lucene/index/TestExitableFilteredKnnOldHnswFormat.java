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

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Random;
import org.apache.lucene.backward_codecs.lucene95.Lucene95RWHnswVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.knn.KnnSearchStrategy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * ExitableDirectoryReader with a timeout that never fires must not change search results. Index:
 * 5000 documents, a 16-dim float vector field "v" in the Lucene 9.5 HNSW format (the format that
 * Lucene 9.5-9.8 wrote; current versions read it through the backward codecs) and a keyword field
 * "f" that is "yes" on about 15% of the documents. Query: k=10 nearest to a fixed vector with the
 * filter f:yes, default search strategy (filtered HNSW search when <= 60% of the vectors pass).
 */
public class TestExitableFilteredKnnOldHnswFormat extends LuceneTestCase {

  static final int N = 5000, DIM = 16;

  static String render(TopDocs td) {
    StringBuilder sb = new StringBuilder();
    for (ScoreDoc sd : td.scoreDocs) {
      sb.append(sd.doc).append(':').append(sd.score).append(' ');
    }
    return sb.toString();
  }

  /** Same documents, same order and same query vector as the index in hnsw-9.8.0.zip. */
  static float[] writeDocs(IndexWriter w) throws Exception {
    Random r = new Random(42);
    for (int i = 0; i < N; i++) {
      Document d = new Document();
      float[] v = new float[DIM];
      for (int j = 0; j < DIM; j++) {
        v[j] = r.nextFloat();
      }
      d.add(new KnnFloatVectorField("v", v, VectorSimilarityFunction.EUCLIDEAN));
      d.add(new StringField("f", r.nextInt(100) < 15 ? "yes" : "no", Field.Store.NO));
      if (w != null) {
        w.addDocument(d);
      }
    }
    float[] q = new float[DIM];
    for (int j = 0; j < DIM; j++) {
      q[j] = r.nextFloat();
    }
    return q;
  }

  static void assertSameWithAndWithoutWrapper(DirectoryReader plain, float[] q) throws Exception {
    DirectoryReader exitable = new ExitableDirectoryReader(plain, () -> false); // never times out
    Query query = new KnnFloatVectorQuery("v", q, 10, new TermQuery(new Term("f", "yes")));
    IndexSearcher plainSearcher = new IndexSearcher(plain);
    plainSearcher.setQueryCache(null);
    IndexSearcher exitableSearcher = new IndexSearcher(exitable);
    exitableSearcher.setQueryCache(null);
    assertEquals(
        "filtered kNN through ExitableDirectoryReader (timeout never fires)",
        render(plainSearcher.search(query, 10)),
        render(exitableSearcher.search(query, 10)));
  }

  /** Index written on this version with the 9.5 HNSW vectors format (Lucene's own RW test format). */
  public void testLucene95FormatWrittenHere() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.alwaysKnnVectorsFormat(new Lucene95RWHnswVectorsFormat(16, 100)));
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      float[] q = writeDocs(w);
      w.forceMerge(1);
      try (DirectoryReader plain = DirectoryReader.open(w)) {
        assertSameWithAndWithoutWrapper(plain, q);
      }
    }
  }

  /**
   * KnnSearchStrategy.Hnsw(100) means "always use filtered search" and Hnsw(0) "never" (javadoc and
   * MIGRATE.md). On the plain reader the two give different hits for this query, so the format
   * honours the setting; through the wrapper Hnsw(100) must still give the Hnsw(100) hits.
   */
  public void testFilteredSearchStrategyThroughWrapper() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.alwaysKnnVectorsFormat(new Lucene95RWHnswVectorsFormat(16, 100)));
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      float[] q = writeDocs(w);
      w.forceMerge(1);
      try (DirectoryReader plain = DirectoryReader.open(w)) {
        DirectoryReader exitable = new ExitableDirectoryReader(plain, () -> false);
        Query filter = new TermQuery(new Term("f", "yes"));
        Query always = new KnnFloatVectorQuery("v", q, 10, filter, new KnnSearchStrategy.Hnsw(100));
        Query never = new KnnFloatVectorQuery("v", q, 10, filter, new KnnSearchStrategy.Hnsw(0));
        IndexSearcher ps = new IndexSearcher(plain);
        ps.setQueryCache(null);
        IndexSearcher es = new IndexSearcher(exitable);
        es.setQueryCache(null);
        String plainAlways = render(ps.search(always, 10));
        String plainNever = render(ps.search(never, 10));
        String exitableAlways = render(es.search(always, 10));
        assumeTrue("the plain reader honours the strategy", plainAlways.equals(plainNever) == false);
        assertEquals(
            "Hnsw(100) through ExitableDirectoryReader; equals the Hnsw(0) hits: "
                + exitableAlways.equals(plainNever),
            plainAlways,
            exitableAlways);
      }
    }
  }

  /** Index written by the Lucene 9.8.0 release (optional resource hnsw-9.8.0.zip on the classpath). */
  public void testIndexWrittenByLucene980() throws Exception {
    InputStream in = getClass().getResourceAsStream("hnsw-9.8.0.zip");
    assumeTrue("resource hnsw-9.8.0.zip not on the classpath", in != null);
    Path path = createTempDir("hnsw-9.8.0");
    try (in) {
      TestUtil.unzip(in, path);
    }
    try (Directory dir = newFSDirectory(path);
        DirectoryReader plain = DirectoryReader.open(dir)) {
      assertSameWithAndWithoutWrapper(plain, writeDocs(null));
    }
  }
}
