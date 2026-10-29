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
package org.apache.lucene.queries.spans;

import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;

/**
 * SpanMultiTermQueryWrapper.equals/hashCode compare only the wrapped query, not the span rewrite
 * method, although the rewrite method decides which documents the wrapper matches. Two wrappers of
 * the same PrefixQuery, one with the default rewrite (every matching term) and one with
 * TopTermsSpanBooleanQueryRewrite(1), match 6 and 1 documents but compare equal.
 *
 * <p>IndexWriter buffers delete-by-query in a map keyed by the Query, so two such deletes in one
 * flush window collapse into one entry that keeps the first query object: one delete is lost
 * (documents the second delete matches survive), or the first, broader query is applied to
 * documents added after it (documents no delete matched are removed).
 */
public class TestSpanMultiTermQueryWrapperEqualsRewriteMethod extends LuceneTestCase {

  private static final String FIELD = "f";

  /** Every term with prefix "aa" (the default span rewrite). */
  private static SpanMultiTermQueryWrapper<PrefixQuery> allTerms() {
    return new SpanMultiTermQueryWrapper<>(new PrefixQuery(new Term(FIELD, "aa")));
  }

  /** Only the top term with prefix "aa" (all boosts tie, so the smallest term: aa1). */
  private static SpanMultiTermQueryWrapper<PrefixQuery> topTerm() {
    SpanMultiTermQueryWrapper<PrefixQuery> q =
        new SpanMultiTermQueryWrapper<>(new PrefixQuery(new Term(FIELD, "aa")));
    q.setRewriteMethod(new SpanMultiTermQueryWrapper.TopTermsSpanBooleanQueryRewrite(1));
    return q;
  }

  private static void addDoc(IndexWriter w, String id, String value) throws Exception {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new TextField(FIELD, value, Field.Store.NO)); // spans need positions
    w.addDocument(d);
  }

  /** aa1 in 1 document, aa2 in 2, aa3 in 3; ids 0..5. */
  private static void addSixDocs(IndexWriter w) throws Exception {
    String[] values = {"aa1", "aa2", "aa2", "aa3", "aa3", "aa3"};
    for (int i = 0; i < values.length; i++) {
      addDoc(w, Integer.toString(i), values[i]);
    }
  }

  private static Set<String> liveIds(Directory dir) throws Exception {
    Set<String> ids = new TreeSet<>();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      for (var ctx : r.leaves()) {
        Bits live = ctx.reader().getLiveDocs();
        StoredFields sf = ctx.reader().storedFields();
        for (int d = 0; d < ctx.reader().maxDoc(); d++) {
          if (live == null || live.get(d)) {
            ids.add(sf.document(d).get("id"));
          }
        }
      }
    }
    return ids;
  }

  private static IndexWriterConfig config() {
    // default analyzer and settings: no automatic flush for this handful of documents
    return new IndexWriterConfig();
  }

  /** Query#equals: equal queries must have identical document-filtering properties. */
  public void testWrappersThatMatchDifferentDocumentsAreUnequal() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        addSixDocs(w);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        Query all = allTerms();
        Query top = topTerm();
        int allCount = s.count(all);
        int topCount = s.count(top);
        assertEquals(6, allCount);
        assertEquals(1, topCount);

        // The wrapped MultiTermQuery itself is unequal when only its rewrite method differs.
        PrefixQuery pqAll = new PrefixQuery(new Term(FIELD, "aa"));
        PrefixQuery pqTop =
            new PrefixQuery(
                new Term(FIELD, "aa"), new MultiTermQuery.TopTermsScoringBooleanQueryRewrite(1));
        assertFalse("control: PrefixQuery with another rewrite method is unequal", pqAll.equals(pqTop));

        assertFalse(
            "wrappers that match "
                + allCount
                + " and "
                + topCount
                + " documents compare equal (hashCode equal: "
                + (all.hashCode() == top.hashCode())
                + ")",
            all.equals(top));
      }
    }
  }

  /**
   * Two deletes in one flush window: deleteDocuments(top) then deleteDocuments(all). Every
   * document with prefix "aa" must be gone; on the affected versions only the aa1 document is.
   */
  public void testSecondDeleteIsLost() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        addSixDocs(w);
        w.commit();
        w.deleteDocuments(topTerm()); // deletes id 0 (aa1)
        w.deleteDocuments(allTerms()); // deletes ids 0..5
        w.commit();
      }
      assertEquals("documents that deleteDocuments(allTerms) matched survived", Set.of(), liveIds(dir));
    }
  }

  /** Same, with both queries in a single deleteDocuments(Query...) call. */
  public void testSecondDeleteIsLostInOneCall() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        addSixDocs(w);
        w.commit();
        w.deleteDocuments(topTerm(), allTerms());
        w.commit();
      }
      assertEquals("documents that deleteDocuments(allTerms) matched survived", Set.of(), liveIds(dir));
    }
  }

  /**
   * deleteDocuments(all) is called before ids 1 and 2 exist, so it must remove only id 0. A later
   * deleteDocuments(top) removes the aa1 documents (ids 0, 1). id 2 (aa2) must survive: no delete
   * issued after it was added matches it. On the affected versions it is deleted.
   */
  public void testEarlierBroadDeleteRemovesLaterDocument() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        addDoc(w, "0", "aa1");
        w.deleteDocuments(allTerms()); // only id 0 exists at this point
        addDoc(w, "1", "aa1");
        addDoc(w, "2", "aa2");
        w.deleteDocuments(topTerm()); // aa1 only: ids 0 and 1
        w.commit();
      }
      assertEquals(
          "a document added after deleteDocuments(allTerms) and not matched by the later"
              + " deleteDocuments(topTerm) was deleted",
          Set.of("2"),
          liveIds(dir));
    }
  }
}
