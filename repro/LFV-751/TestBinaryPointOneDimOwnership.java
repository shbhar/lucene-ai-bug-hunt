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
import org.apache.lucene.util.NumericUtils;

/**
 * Intended-behaviour probe for BinaryPoint's one-dimensional query factories. Each test asserts the
 * behaviour the class shows elsewhere (a query keeps the value it was built for). The set-query
 * control is expected to pass; the exact/range tests show whether the one-dimensional factories
 * keep the caller's array.
 */
public class TestBinaryPointOneDimOwnership extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  private static byte[] enc(int v) {
    byte[] b = new byte[Integer.BYTES];
    NumericUtils.intToSortableBytes(v, b, 0);
    return b;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int v = 1; v <= 3; v++) {
        Document doc = new Document();
        doc.add(new BinaryPoint("k", enc(v)));
        w.addDocument(doc);
      }
    }
    reader = DirectoryReader.open(dir);
    // no query cache: the effect must not depend on caching
    searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /** Control: newSetQuery with one value keeps the value it was built for. */
  public void testSingleValueSetQueryKeepsItsValue() throws Exception {
    byte[] scratch = new byte[Integer.BYTES];
    NumericUtils.intToSortableBytes(1, scratch, 0);
    Query set = BinaryPoint.newSetQuery("k", scratch);
    NumericUtils.intToSortableBytes(2, scratch, 0);
    int hitsForKey1 =
        searcher.count(
            new BooleanQuery.Builder()
                .add(set, BooleanClause.Occur.MUST)
                .add(BinaryPoint.newExactQuery("k", enc(1)), BooleanClause.Occur.MUST)
                .build());
    System.out.println("V3 set(1 value) after scratch refill: hits for key 1 = " + hitsForKey1);
    assertEquals(1, hitsForKey1);
  }

  /** Same scenario with newExactQuery, the class's documented factory for one exact 1D value. */
  public void testExactQueryKeepsItsValue() throws Exception {
    byte[] scratch = new byte[Integer.BYTES];
    NumericUtils.intToSortableBytes(1, scratch, 0);
    Query exact = BinaryPoint.newExactQuery("k", scratch);
    String before = exact.toString();
    int hashBefore = exact.hashCode();
    NumericUtils.intToSortableBytes(2, scratch, 0);
    int hitsForKey1 =
        searcher.count(
            new BooleanQuery.Builder()
                .add(exact, BooleanClause.Occur.MUST)
                .add(BinaryPoint.newExactQuery("k", enc(1)), BooleanClause.Occur.MUST)
                .build());
    System.out.println(
        "V3 exact after scratch refill: hits for key 1 = "
            + hitsForKey1
            + "; toString "
            + before
            + " -> "
            + exact
            + "; hashCode changed = "
            + (hashBefore != exact.hashCode()));
    assertEquals(1, hitsForKey1);
  }

  /**
   * Typical client loop: encode each key into one scratch buffer with NumericUtils (an API that
   * writes into a caller-supplied array) and OR the exact queries. Built and run before any search
   * sees the queries, with no query cache.
   */
  public void testOrOfExactQueriesFromEncodeLoop() throws Exception {
    byte[] scratch = new byte[Integer.BYTES];
    BooleanQuery.Builder or = new BooleanQuery.Builder();
    BooleanQuery.Builder orSet = new BooleanQuery.Builder();
    for (int key : new int[] {1, 2, 3}) {
      NumericUtils.intToSortableBytes(key, scratch, 0);
      or.add(BinaryPoint.newExactQuery("k", scratch), BooleanClause.Occur.SHOULD);
      orSet.add(BinaryPoint.newSetQuery("k", scratch), BooleanClause.Occur.SHOULD);
    }
    int exactHits = searcher.count(or.build());
    int setHits = searcher.count(orSet.build());
    System.out.println(
        "V3 encode loop, keys 1..3: OR of exact queries = "
            + exactHits
            + " hits; OR of one-value set queries = "
            + setHits
            + " hits (want 3)");
    assertEquals("set-query control", 3, setHits);
    assertEquals("exact queries", 3, exactHits);
  }

  /** 1-D range from the 1-D overload. */
  public void testOneDimRangeKeepsItsBounds() throws Exception {
    byte[] lo = enc(1);
    byte[] hi = enc(1);
    Query range = BinaryPoint.newRangeQuery("k", lo, hi);
    NumericUtils.intToSortableBytes(3, lo, 0);
    NumericUtils.intToSortableBytes(3, hi, 0);
    int hits =
        searcher.count(
            new BooleanQuery.Builder()
                .add(range, BooleanClause.Occur.MUST)
                .add(BinaryPoint.newExactQuery("k", enc(1)), BooleanClause.Occur.MUST)
                .build());
    System.out.println("V3 1-D range [1,1] after bounds refilled to 3: hits for key 1 = " + hits);
    assertEquals(1, hits);
  }
}
