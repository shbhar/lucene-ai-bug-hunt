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
package org.apache.lucene.monitor;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.join.ParentsChildrenBlockJoinQuery;
import org.apache.lucene.search.join.QueryBitSetProducer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Lucene Monitor's HighlightsMatch.MATCHER decides which documents a stored query matches by
 * calling Weight#matches on every document of the batch and recording a match whenever the result
 * is non-null. QueryMatch.SIMPLE_MATCHER decides by running the query (scorer). For a stored query
 * that contains a ParentsChildrenBlockJoinQuery, the two matchers must report the same documents.
 *
 * <p>The batch is indexed by the monitor as one addDocuments block:
 *
 * <pre>
 *   doc 0  child   body "size large"
 *   doc 1  child   body "size small"
 *   doc 2  parent  acl public      body "shirt"
 *   doc 3  child   body "size medium"
 *   doc 4  parent  acl restricted  body "shirt"
 * </pre>
 *
 * Only docs 0 and 1 are children of a public parent.
 */
public class TestMonitorHighlightsMatchParentsChildrenJoin extends LuceneTestCase {

  private static Document child(String body) {
    Document d = new Document();
    d.add(new StringField("type", "child", Field.Store.NO));
    d.add(new TextField("body", body, Field.Store.NO));
    return d;
  }

  private static Document parent(String acl) {
    Document d = new Document();
    d.add(new StringField("type", "parent", Field.Store.NO));
    d.add(new StringField("acl", acl, Field.Store.NO));
    d.add(new TextField("body", "shirt", Field.Store.NO));
    return d;
  }

  private static Document[] batch() {
    return new Document[] {
      child("size large"), child("size small"), parent("public"), child("size medium"),
      parent("restricted")
    };
  }

  /** Children matching childQuery whose parent has the given acl, at most limit per parent. */
  private static Query childrenOf(String acl, Query childQuery, int limit) {
    return new ParentsChildrenBlockJoinQuery(
        new QueryBitSetProducer(new TermQuery(new Term("type", "parent"))),
        new TermQuery(new Term("acl", acl)),
        childQuery,
        limit);
  }

  private static Query childrenOfPublicParents(Query childQuery, int limit) {
    return childrenOf("public", childQuery, limit);
  }

  private static <T extends QueryMatch> Set<Integer> matchedDocs(
      Query q, MatcherFactory<T> factory) throws IOException {
    return matchedDocs(q, factory, batch());
  }

  private static <T extends QueryMatch> Set<Integer> matchedDocs(
      Query q, MatcherFactory<T> factory, Document[] docs) throws IOException {
    try (Monitor monitor = new Monitor(new StandardAnalyzer())) {
      monitor.register(new MonitorQuery("q", q));
      MultiMatchingQueries<T> result = monitor.match(docs, factory);
      assertTrue("matcher errors: " + result.getErrors(), result.getErrors().isEmpty());
      Set<Integer> out = new TreeSet<>();
      for (int d = 0; d < docs.length; d++) {
        if (result.matches("q", d) != null) {
          out.add(d);
        }
      }
      return out;
    }
  }

  private static void assertSameDocs(Query q, Set<Integer> expected) throws IOException {
    Set<Integer> simple = matchedDocs(q, QueryMatch.SIMPLE_MATCHER);
    assertEquals("QueryMatch.SIMPLE_MATCHER for " + q, expected, simple);
    Set<Integer> highlights = matchedDocs(q, HighlightsMatch.MATCHER);
    assertEquals(
        "HighlightsMatch.MATCHER must report the same documents as QueryMatch.SIMPLE_MATCHER for "
            + q,
        simple,
        highlights);
  }

  /** The join alone: only children of the public parent may match. */
  public void testJoinAloneRestrictsToChildrenOfPublicParents() throws IOException {
    Query q =
        childrenOfPublicParents(
            new TermQuery(new Term("body", "size")),
            ParentsChildrenBlockJoinQuery.DEFAULT_CHILD_LIMIT_PER_PARENT);
    assertSameDocs(q, Set.of(0, 1));
  }

  /** The join as a FILTER restriction next to the user's own clause. */
  public void testJoinAsFilterRestriction() throws IOException {
    Query q =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("body", "size")), Occur.MUST)
            .add(
                childrenOfPublicParents(
                    new TermQuery(new Term("type", "child")),
                    ParentsChildrenBlockJoinQuery.DEFAULT_CHILD_LIMIT_PER_PARENT),
                Occur.FILTER)
            .build();
    assertSameDocs(q, Set.of(0, 1));
  }

  /** The join as one of two required SHOULD clauses (minimumNumberShouldMatch = 2). */
  public void testJoinInMinShouldMatch() throws IOException {
    Query q =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("body", "size")), Occur.SHOULD)
            .add(
                childrenOfPublicParents(
                    new TermQuery(new Term("type", "child")),
                    ParentsChildrenBlockJoinQuery.DEFAULT_CHILD_LIMIT_PER_PARENT),
                Occur.SHOULD)
            .setMinimumNumberShouldMatch(2)
            .build();
    assertSameDocs(q, Set.of(0, 1));
  }

  /** childLimitPerParent = 1: one child of the public parent. */
  public void testChildLimitPerParent() throws IOException {
    Query q = childrenOfPublicParents(new TermQuery(new Term("body", "size")), 1);
    assertSameDocs(q, Set.of(0));
  }

  /**
   * The join as a MUST_NOT deny rule: exclude children of restricted parents. Expected: docs 0 and
   * 1. The wrong Matches can only remove documents here: every document HighlightsMatch.MATCHER
   * reports must also be reported by SIMPLE_MATCHER (checked first), but true matches are dropped.
   */
  public void testJoinAsMustNotDenyRuleOnlyDropsMatches() throws IOException {
    Query q =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("body", "size")), Occur.MUST)
            .add(
                childrenOf(
                    "restricted",
                    new TermQuery(new Term("type", "child")),
                    ParentsChildrenBlockJoinQuery.DEFAULT_CHILD_LIMIT_PER_PARENT),
                Occur.MUST_NOT)
            .build();
    Set<Integer> simple = matchedDocs(q, QueryMatch.SIMPLE_MATCHER);
    assertEquals("QueryMatch.SIMPLE_MATCHER for " + q, Set.of(0, 1), simple);
    Set<Integer> highlights = matchedDocs(q, HighlightsMatch.MATCHER);
    assertTrue(
        "HighlightsMatch.MATCHER reported a document the deny rule removes: " + highlights,
        simple.containsAll(highlights));
    assertEquals(
        "HighlightsMatch.MATCHER must report the same documents as QueryMatch.SIMPLE_MATCHER for "
            + q,
        simple,
        highlights);
  }

  /**
   * A single-document batch (the monitor indexes it in a MemoryIndex): one parent with acl:public
   * and no children. The join returns only children, so nothing may match.
   */
  public void testSingleParentDocument() throws IOException {
    Document[] docs = new Document[] {parent("public")};
    Query q =
        childrenOfPublicParents(
            new TermQuery(new Term("body", "size")),
            ParentsChildrenBlockJoinQuery.DEFAULT_CHILD_LIMIT_PER_PARENT);
    Set<Integer> simple = matchedDocs(q, QueryMatch.SIMPLE_MATCHER, docs);
    assertEquals("QueryMatch.SIMPLE_MATCHER for " + q, Set.of(), simple);
    assertEquals(
        "HighlightsMatch.MATCHER must report the same documents as QueryMatch.SIMPLE_MATCHER for "
            + q,
        simple,
        matchedDocs(q, HighlightsMatch.MATCHER, docs));
  }

  /**
   * A single-document batch holding one child and no parent (for example a child sent on its own).
   * Its parent is not in the batch, so the parent condition cannot be satisfied: nothing may match.
   */
  public void testSingleChildDocumentWithoutParent() throws IOException {
    Document[] docs = new Document[] {child("size large")};
    Query q =
        childrenOfPublicParents(
            new TermQuery(new Term("body", "size")),
            ParentsChildrenBlockJoinQuery.DEFAULT_CHILD_LIMIT_PER_PARENT);
    Set<Integer> simple = matchedDocs(q, QueryMatch.SIMPLE_MATCHER, docs);
    assertEquals("QueryMatch.SIMPLE_MATCHER for " + q, Set.of(), simple);
    assertEquals(
        "HighlightsMatch.MATCHER must report the same documents as QueryMatch.SIMPLE_MATCHER for "
            + q,
        simple,
        matchedDocs(q, HighlightsMatch.MATCHER, docs));
  }
}
