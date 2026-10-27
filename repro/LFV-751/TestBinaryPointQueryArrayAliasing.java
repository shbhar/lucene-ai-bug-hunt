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

import org.apache.lucene.document.BinaryPoint;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * BinaryPoint's one-dimensional query factories keep the caller's byte[] as the query's bounds
 * (BinaryPoint.pack returns new BytesRef(point[0]) for one dimension), while the same factory
 * copies the values for two or more dimensions and every other point factory encodes into a new
 * array. So a caller that encodes each key into one scratch array changes the queries it already
 * built.
 */
public class TestBinaryPointQueryArrayAliasing extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (byte v = 1; v <= 3; v++) {
        Document doc = new Document();
        doc.add(new BinaryPoint("p1", new byte[] {v}));
        doc.add(new BinaryPoint("p2", new byte[] {v}, new byte[] {v}));
        w.addDocument(doc);
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private static Query or(Query a, Query b) {
    return new BooleanQuery.Builder()
        .add(a, BooleanClause.Occur.SHOULD)
        .add(b, BooleanClause.Occur.SHOULD)
        .build();
  }

  /** Two exact queries built from one scratch array, OR-ed: the first key is lost. */
  public void testExactQueriesFromOneScratchArray() throws Exception {
    byte[] scratch = new byte[1];
    scratch[0] = 1;
    Query key1 = BinaryPoint.newExactQuery("p1", scratch);
    scratch[0] = 2;
    Query key2 = BinaryPoint.newExactQuery("p1", scratch);

    assertEquals("documents with key 1 or key 2", 2, searcher.count(or(key1, key2)));
    assertFalse("queries built for different keys", key1.equals(key2));
  }

  /** The n-dimensional overload copies when it has two dimensions, but not when it has one. */
  public void testRangeQueryCopiesOnlyForTwoOrMoreDimensions() throws Exception {
    byte[] lo = {1};
    byte[] hi = {1};
    Query twoDims = BinaryPoint.newRangeQuery("p2", new byte[][] {lo, lo}, new byte[][] {hi, hi});
    Query oneDim = BinaryPoint.newRangeQuery("p1", new byte[][] {lo}, new byte[][] {hi});
    lo[0] = 3; // the caller reuses its arrays
    hi[0] = 3;
    Query docWithValue1 = BinaryPoint.newExactQuery("p1", new byte[] {1});
    Query twoDimsAndValue1 =
        new BooleanQuery.Builder()
            .add(twoDims, BooleanClause.Occur.MUST)
            .add(docWithValue1, BooleanClause.Occur.MUST)
            .build();
    Query oneDimAndValue1 =
        new BooleanQuery.Builder()
            .add(oneDim, BooleanClause.Occur.MUST)
            .add(docWithValue1, BooleanClause.Occur.MUST)
            .build();
    assertEquals("two dimensions: still the range built for [1, 1]", 1, searcher.count(twoDimsAndValue1));
    assertEquals("one dimension: still the range built for [1, 1]", 1, searcher.count(oneDimAndValue1));
  }
}
