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
package org.example.securityprobe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PointInSetQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;

/**
 * Security-relevant uses of a two-dimensional PointInSetQuery (public API only, from a package
 * outside org.apache.lucene). Every assertion states the correct result. Tests 1-3 are expected
 * to fail on releases with the segment early exit of GITHUB#14268 and to pass on 10.2.2 and with
 * a per-dimension bounding box; the two controls pass everywhere.
 */
public class TestMultiDimSetQueryDenyRule extends LuceneTestCase {

  /** A 2-D int set query, points given as flat (x, y) pairs, sorted as the constructor requires. */
  private static Query setQuery(String field, int... flat) {
    List<byte[]> packed = new ArrayList<>();
    for (int i = 0; i < flat.length; i += 2) {
      byte[] p = new byte[2 * Integer.BYTES];
      NumericUtils.intToSortableBytes(flat[i], p, 0);
      NumericUtils.intToSortableBytes(flat[i + 1], p, Integer.BYTES);
      packed.add(p);
    }
    packed.sort((a, b) -> new BytesRef(a).compareTo(new BytesRef(b)));
    Iterator<byte[]> it = packed.iterator();
    return new PointInSetQuery(
        field,
        2,
        Integer.BYTES,
        new PointInSetQuery.Stream() {
          @Override
          public BytesRef next() {
            return it.hasNext() ? new BytesRef(it.next()) : null;
          }
        }) {
      @Override
      protected String toString(byte[] value) {
        return NumericUtils.sortableBytesToInt(value, 0)
            + ","
            + NumericUtils.sortableBytesToInt(value, Integer.BYTES);
      }
    };
  }

  private static Document doc(String id, String tenant, int x, int y) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new StringField("tenant", tenant, Field.Store.NO));
    d.add(new IntPoint("p", x, y));
    return d;
  }

  /** "Show tenant t1's documents, except those whose (category, label) pair is denied." */
  private static Query tenantMinusDenied(Query denySet) {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("tenant", "t1")), Occur.FILTER)
        .add(denySet, Occur.MUST_NOT)
        .build();
  }

  /** Test 1: a MUST_NOT deny rule over a 2-D set fails open for a skipped segment. */
  public void testDenyRuleExcludesDeniedPair() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      w.addDocument(doc("a", "t1", 1, 0)); // denied pair (1,0)
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        Query deny = tenantMinusDenied(setQuery("p", 0, 1, 1, 0));
        assertEquals(deny.toString() + " search()", 0, s.search(deny, 10).scoreDocs.length);
        assertEquals(deny.toString() + " count()", 0, s.count(deny));
      }
    }
  }

  /** Control: the same deny rule written with the denied pair alone works. */
  public void testControlSinglePairDenyRule() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      w.addDocument(doc("a", "t1", 1, 0));
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher s = new IndexSearcher(r);
        Query deny = tenantMinusDenied(setQuery("p", 1, 0));
        assertEquals(deny.toString(), 0, s.search(deny, 10).scoreDocs.length);
      }
    }
  }

  /**
   * Test 2: whether the rule holds depends on the segment's other documents. With another
   * tenant's document (0,5) in the same segment the rule works; after that document is deleted
   * and the segment is merged, the same rule lets the denied document through.
   */
  public void testDenyRuleDependsOnSegmentComposition() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(doc("a", "t1", 1, 0)); // denied pair (1,0)
      w.addDocument(doc("b", "t2", 0, 5)); // another tenant's document, not denied
      w.commit();
      Query deny = tenantMinusDenied(setQuery("p", 0, 1, 1, 0));
      int before;
      try (DirectoryReader r = DirectoryReader.open(w)) {
        before = new IndexSearcher(r).search(deny, 10).scoreDocs.length;
      }
      w.deleteDocuments(new Term("id", "b"));
      w.forceMerge(1);
      w.commit();
      int after;
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(1, r.leaves().size());
        assertEquals("the deleted document was merged away", 1, r.maxDoc());
        assertEquals(1, r.numDocs());
        after = new IndexSearcher(r).search(deny, 10).scoreDocs.length;
      }
      assertEquals("deny rule with the other tenant's document present", 0, before);
      assertEquals("deny rule after that document was deleted and merged away", 0, after);
    }
  }

  /** Test 3: IndexWriter.deleteDocuments(Query) with a 2-D set silently deletes nothing. */
  public void testDeleteByMultiDimSetQuery() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      w.addDocument(doc("a", "t1", 1, 0));
      w.commit();
      w.deleteDocuments(setQuery("p", 0, 1, 1, 0));
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals("live docs after deleteDocuments({(0,1),(1,0)})", 0, r.numDocs());
      }
    }
  }

  /** Control: delete-by-query with the single pair deletes the document. */
  public void testControlDeleteBySinglePair() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      w.addDocument(doc("a", "t1", 1, 0));
      w.commit();
      w.deleteDocuments(setQuery("p", 1, 0));
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(0, r.numDocs());
      }
    }
  }
}
