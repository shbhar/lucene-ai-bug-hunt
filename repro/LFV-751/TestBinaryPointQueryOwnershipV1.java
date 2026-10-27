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
package org.apache.lucene.document;

import java.io.IOException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PointRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: does a BinaryPoint query keep using the byte[] the caller passed in?
 *
 * <p>Only public API is used. The caller never touches the Query object after building it; it
 * only reuses its own byte[] to build the next query.
 */
public class TestBinaryPointQueryOwnershipV1 extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    for (int key = 1; key <= 3; key++) {
      Document doc = new Document();
      // one-dimensional field, 1 byte per dimension
      doc.add(new BinaryPoint("k1", new byte[] {(byte) key}));
      // two-dimensional control field
      doc.add(new BinaryPoint("k2", new byte[] {(byte) key}, new byte[] {(byte) key}));
      doc.add(new StoredField("id", key));
      w.addDocument(doc);
    }
    w.forceMerge(1);
    w.close();
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null); // keep the cache out of the core legs
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private int onlyHitKey(Query q) throws IOException {
    ScoreDoc[] hits = searcher.search(q, 10).scoreDocs;
    assertEquals("hits of " + q, 1, hits.length);
    return searcher.storedFields().document(hits[0].doc).getField("id").numericValue().intValue();
  }

  /** Leg A: newExactQuery built from a scratch array that is then refilled for the next key. */
  public void testExactQueryFromReusedScratch() throws IOException {
    byte[] scratch = new byte[1];
    scratch[0] = 1;
    Query q1 = BinaryPoint.newExactQuery("k1", scratch);
    scratch[0] = 2;
    Query q2 = BinaryPoint.newExactQuery("k1", scratch);

    // reference queries built from private arrays
    Query ref1 = BinaryPoint.newExactQuery("k1", new byte[] {1});
    Query ref2 = BinaryPoint.newExactQuery("k1", new byte[] {2});

    StringBuilder sb = new StringBuilder();
    sb.append("q1=").append(q1).append(" q2=").append(q2);
    sb.append(" q1.equals(ref1)=").append(q1.equals(ref1));
    sb.append(" q1.equals(q2)=").append(q1.equals(q2));
    sb.append(" q1.lower=").append(((PointRangeQuery) q1).getLowerPoint()[0]);
    System.out.println("LEG-A " + sb);

    BooleanQuery.Builder or = new BooleanQuery.Builder();
    or.add(q1, BooleanClause.Occur.SHOULD);
    or.add(q2, BooleanClause.Occur.SHOULD);
    int orCount = searcher.count(or.build());
    System.out.println("LEG-A OR(q1,q2) count=" + orCount + " (expected 2)");

    assertEquals("q1 was built for key 1: " + sb, 1, onlyHitKey(q1));
    assertEquals("q2 was built for key 2", 2, onlyHitKey(q2));
    assertEquals("q1 must equal a query built from a private {1}", ref1, q1);
    assertFalse("queries for different keys must not be equal", q1.equals(q2));
    assertEquals("OR of key-1 and key-2 queries", 2, orCount);
    assertEquals(ref2, q2);
  }

  /** Leg B: 1-D newRangeQuery(byte[], byte[]) after the caller overwrites its bounds. */
  public void testOneDimRangeQueryAfterCallerWrite() throws IOException {
    byte[] lo = {1};
    byte[] hi = {2};
    Query q = BinaryPoint.newRangeQuery("k1", lo, hi);
    lo[0] = 9;
    hi[0] = 9;
    int c = searcher.count(q);
    System.out.println("LEG-B 1-D range [1,2] after caller write: count=" + c + " (expected 2) q=" + q);
    assertEquals("1-D range built for [1,2]", 2, c);
  }

  /** Leg C: n-D overload called with one dimension. */
  public void testNDimOverloadWithOneDimAfterCallerWrite() throws IOException {
    byte[] lo = {1};
    byte[] hi = {2};
    Query q = BinaryPoint.newRangeQuery("k1", new byte[][] {lo}, new byte[][] {hi});
    lo[0] = 9;
    hi[0] = 9;
    int c = searcher.count(q);
    System.out.println("LEG-C n-D overload, 1 dim, after caller write: count=" + c + " (expected 2)");
    assertEquals("n-D overload with one dimension built for [1,2]", 2, c);
  }

  /** Control: the same n-D overload with two dimensions is unaffected (it copies). */
  public void testControlTwoDimsUnaffected() throws IOException {
    byte[] lo0 = {1}, lo1 = {1};
    byte[] hi0 = {2}, hi1 = {2};
    Query q = BinaryPoint.newRangeQuery("k2", new byte[][] {lo0, lo1}, new byte[][] {hi0, hi1});
    lo0[0] = lo1[0] = hi0[0] = hi1[0] = 9;
    int c = searcher.count(q);
    System.out.println("CONTROL 2-D range [1,2]x[1,2] after caller write: count=" + c + " (expected 2)");
    assertEquals(2, c);
  }

  /** Control: the same class's 1-D newSetQuery from a reused scratch array is unaffected. */
  public void testControlSetQueryFromReusedScratch() throws IOException {
    byte[] scratch = {1};
    Query q = BinaryPoint.newSetQuery("k1", scratch);
    scratch[0] = 2;
    System.out.println("CONTROL newSetQuery({1}) after caller write: q=" + q);
    assertEquals(1, onlyHitKey(q));
  }

  /** Control: getters return copies, so a caller cannot change the bounds through them. */
  public void testControlGetterCopies() throws IOException {
    PointRangeQuery q = (PointRangeQuery) BinaryPoint.newExactQuery("k1", new byte[] {1});
    q.getLowerPoint()[0] = 9;
    q.getUpperPoint()[0] = 9;
    assertEquals(1, onlyHitKey(q));
  }
}
