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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The span top-terms rewrites of SpanMultiTermQueryWrapper keep up to their size terms even when
 * the size is larger than IndexSearcher#getMaxClauseCount, so the rewritten SpanOrQuery fails the
 * searcher's clause check with TooManyNestedClauses.
 *
 * <p>FuzzyQuery documents for maxExpansions: "If this number is greater than
 * IndexSearcher#getMaxClauseCount when the query is rewritten, then the maxClauseCount will be used
 * instead." TopTermsSpanBooleanQueryRewrite documents "This rewrite method only uses the top scoring
 * terms so it will not overflow the boolean max clause count", and FrequentTermsSpanBooleanQueryRewrite
 * "retains only the most frequent terms so it will not overflow the boolean max clause count".
 */
public class TestSpanTopTermsRewriteMaxClauseCount extends LuceneTestCase {

  /** 676 documents: field f holds 1352 terms within 2 edits of "abcde", field g 1352 terms "p*". */
  private static Directory buildIndex() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    for (char x = 'a'; x <= 'z'; x++) {
      for (char y = 'a'; y <= 'z'; y++) {
        Document d = new Document();
        d.add(new TextField("f", "abc" + x + y + " ab" + x + "d" + y, Field.Store.NO));
        d.add(new TextField("g", "pa" + x + y + " pb" + x + y, Field.Store.NO));
        w.addDocument(d);
      }
    }
    w.close();
    return dir;
  }

  private static int countTerms(Query rewritten) {
    int[] n = new int[1];
    rewritten.visit(
        new QueryVisitor() {
          @Override
          public QueryVisitor getSubVisitor(BooleanClause.Occur occur, Query parent) {
            return this;
          }

          @Override
          public void consumeTerms(Query query, Term... terms) {
            n[0] += terms.length;
          }
        });
    return n[0];
  }

  /** A FuzzyQuery with maxExpansions above the clause limit: capped when plain, fails when span-wrapped. */
  public void testSpanWrappedFuzzyQueryMaxExpansionsAboveClauseLimit() throws Exception {
    try (Directory dir = buildIndex();
        IndexReader reader = org.apache.lucene.index.DirectoryReader.open(dir)) {
      IndexSearcher searcher = newSearcher(reader);
      FuzzyQuery fuzzy = new FuzzyQuery(new Term("f", "abcde"), 2, 0, 2000, true);
      // control: the plain FuzzyQuery is capped at maxClauseCount (1024) terms, as documented
      assertEquals(IndexSearcher.getMaxClauseCount(), countTerms(searcher.rewrite(fuzzy)));
      assertEquals(676, searcher.count(fuzzy));
      // the same query wrapped as a span query
      Query span = new SpanMultiTermQueryWrapper<>(fuzzy);
      assertEquals(676, searcher.count(span)); // throws TooManyNestedClauses on main
    }
  }

  /** The default FuzzyQuery (maxExpansions 50) under a clause limit lowered to 32. */
  public void testSpanWrappedDefaultFuzzyQueryUnderLoweredClauseLimit() throws Exception {
    int saved = IndexSearcher.getMaxClauseCount();
    try (Directory dir = buildIndex();
        IndexReader reader = org.apache.lucene.index.DirectoryReader.open(dir)) {
      IndexSearcher.setMaxClauseCount(32);
      IndexSearcher searcher = newSearcher(reader);
      FuzzyQuery fuzzy = new FuzzyQuery(new Term("f", "abcde"));
      // control: the plain query keeps maxClauseCount = 32 terms
      assertEquals(32, countTerms(searcher.rewrite(fuzzy)));
      Query span = new SpanMultiTermQueryWrapper<>(fuzzy);
      searcher.count(span); // throws TooManyNestedClauses on main
    } finally {
      IndexSearcher.setMaxClauseCount(saved);
    }
  }

  /** TopTermsSpanBooleanQueryRewrite set explicitly with a size above the clause limit. */
  public void testTopTermsSpanBooleanQueryRewriteSizeAboveClauseLimit() throws Exception {
    try (Directory dir = buildIndex();
        IndexReader reader = org.apache.lucene.index.DirectoryReader.open(dir)) {
      IndexSearcher searcher = newSearcher(reader);
      SpanMultiTermQueryWrapper<PrefixQuery> span =
          new SpanMultiTermQueryWrapper<>(new PrefixQuery(new Term("g", "p")));
      span.setRewriteMethod(new SpanMultiTermQueryWrapper.TopTermsSpanBooleanQueryRewrite(2000));
      assertEquals(676, searcher.count(span)); // throws TooManyNestedClauses on main
    }
  }

  /** FrequentTermsSpanBooleanQueryRewrite with a maxSize above the clause limit. */
  public void testFrequentTermsSpanBooleanQueryRewriteSizeAboveClauseLimit() throws Exception {
    try (Directory dir = buildIndex();
        IndexReader reader = org.apache.lucene.index.DirectoryReader.open(dir)) {
      IndexSearcher searcher = newSearcher(reader);
      SpanMultiTermQueryWrapper<PrefixQuery> span =
          new SpanMultiTermQueryWrapper<>(new PrefixQuery(new Term("g", "p")));
      span.setRewriteMethod(
          new SpanMultiTermQueryWrapper.FrequentTermsSpanBooleanQueryRewrite(2000));
      assertEquals(676, searcher.count(span)); // throws TooManyNestedClauses on main
    }
  }
}
