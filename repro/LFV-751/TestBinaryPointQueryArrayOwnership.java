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
 * A one-dimensional BinaryPoint exact or range query should keep the value it was built for when
 * the caller later writes into its own byte[], as the two-dimension range query and newSetQuery do.
 */
public class TestBinaryPointQueryArrayOwnership extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  private static byte[] encode(int key) {
    byte[] bytes = new byte[Integer.BYTES];
    NumericUtils.intToSortableBytes(key, bytes, 0);
    return bytes;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int key = 1; key <= 3; key++) {
        Document doc = new Document();
        doc.add(new BinaryPoint("key", encode(key)));
        doc.add(new BinaryPoint("key2d", encode(key), encode(key)));
        w.addDocument(doc);
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null); // no caching involved
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /** Encode keys 1, 2 and 3 into one scratch buffer and OR one query per key. */
  public void testOrOfExactQueriesFromOneScratchBuffer() throws Exception {
    byte[] scratch = new byte[Integer.BYTES];
    BooleanQuery.Builder exact = new BooleanQuery.Builder();
    BooleanQuery.Builder set = new BooleanQuery.Builder();
    for (int key = 1; key <= 3; key++) {
      NumericUtils.intToSortableBytes(key, scratch, 0);
      exact.add(BinaryPoint.newExactQuery("key", scratch), BooleanClause.Occur.SHOULD);
      set.add(BinaryPoint.newSetQuery("key", scratch), BooleanClause.Occur.SHOULD);
    }
    assertEquals("OR of one-value newSetQuery", 3, searcher.count(set.build()));
    assertEquals("OR of newExactQuery", 3, searcher.count(exact.build()));
  }

  /** Build a range for keys [1, 2], then reuse the bound arrays for something else. */
  public void testRangeQueryKeepsItsBounds() throws Exception {
    byte[] lo = encode(1);
    byte[] hi = encode(2);
    Query twoDims =
        BinaryPoint.newRangeQuery("key2d", new byte[][] {lo, lo}, new byte[][] {hi, hi});
    Query oneDim = BinaryPoint.newRangeQuery("key", lo, hi);
    NumericUtils.intToSortableBytes(3, lo, 0);
    NumericUtils.intToSortableBytes(3, hi, 0);
    assertEquals("two dimensions, range [1, 2]", 2, searcher.count(twoDims));
    assertEquals("one dimension, range [1, 2]", 2, searcher.count(oneDim));
  }

  /** The query's toString and equals should not follow the caller's array either. */
  public void testExactQueryDoesNotFollowCallerArray() {
    byte[] scratch = encode(1);
    Query key1 = BinaryPoint.newExactQuery("key", scratch);
    String printed = key1.toString();
    NumericUtils.intToSortableBytes(2, scratch, 0);
    assertEquals("query built for key 1", printed, key1.toString());
    assertFalse(key1.equals(BinaryPoint.newExactQuery("key", encode(2))));
  }
}
