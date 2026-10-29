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

import java.lang.management.ManagementFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.SpanMultiTermQueryWrapper.FrequentTermsSpanBooleanQueryRewrite;
import org.apache.lucene.queries.spans.SpanMultiTermQueryWrapper.SpanRewriteMethod;
import org.apache.lucene.queries.spans.SpanMultiTermQueryWrapper.TopTermsSpanBooleanQueryRewrite;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * FrequentTermsSpanBooleanQueryRewrite(maxSize) "keeps at most maxSize terms" and differs from
 * TopTermsSpanBooleanQueryRewrite only in how it ranks terms. When maxSize is at least the number
 * of matching terms, the ranking does not matter: both rewrites keep every term. The field below
 * has 3 matching terms (aa1, aa2, aa3) in 6 documents.
 */
public class TestFrequentTermsSpanRewriteSize extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
      for (String v : new String[] {"aa1 x", "aa1 x", "aa1 x", "aa2 x", "aa2 x", "aa3 x"}) {
        Document d = new Document();
        d.add(new TextField("f", v, Field.Store.NO));
        w.addDocument(d);
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader); // no executor: rewrite runs on this thread
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private static SpanMultiTermQueryWrapper<PrefixQuery> prefix(SpanRewriteMethod method) {
    SpanMultiTermQueryWrapper<PrefixQuery> q =
        new SpanMultiTermQueryWrapper<>(new PrefixQuery(new Term("f", "aa")));
    q.setRewriteMethod(method);
    return q;
  }

  private int countNear(SpanRewriteMethod method) throws Exception {
    Query near =
        new SpanNearQuery(
            new SpanQuery[] {prefix(method), new SpanTermQuery(new Term("f", "x"))}, 0, true);
    return searcher.count(near);
  }

  /** maxSize = Integer.MAX_VALUE ("no limit") keeps all 3 terms, like the TopTerms sibling. */
  public void testMaxValueSizeKeepsEveryTerm() throws Exception {
    // controls: the sibling accepts "no limit", and FrequentTerms works with a size >= 3
    assertEquals(6, countNear(new TopTermsSpanBooleanQueryRewrite(Integer.MAX_VALUE)));
    assertEquals(6, countNear(new FrequentTermsSpanBooleanQueryRewrite(1024)));
    // fails on main: IllegalArgumentException "maxSize must be >= 0 and < 2147483631" from
    // PriorityQueue, thrown at search time (the constructor accepted the size)
    assertEquals(6, countNear(new FrequentTermsSpanBooleanQueryRewrite(Integer.MAX_VALUE)));
  }

  /**
   * Same as above with the clause limit raised to "no limit" (IndexSearcher#setMaxClauseCount
   * accepts any value >= 1). Capping maxSize at the clause limit does not help here: the queue
   * would still be sized Integer.MAX_VALUE for 3 terms.
   */
  public void testMaxValueSizeWithRaisedClauseLimit() throws Exception {
    int saved = IndexSearcher.getMaxClauseCount();
    IndexSearcher.setMaxClauseCount(Integer.MAX_VALUE);
    try {
      assertEquals(6, countNear(new TopTermsSpanBooleanQueryRewrite(Integer.MAX_VALUE)));
      assertEquals(6, countNear(new FrequentTermsSpanBooleanQueryRewrite(Integer.MAX_VALUE)));
    } finally {
      IndexSearcher.setMaxClauseCount(saved);
    }
  }

  /** The cost of rewriting 3 terms must not grow with maxSize. */
  public void testRewriteAllocationIndependentOfMaxSize() throws Exception {
    java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    assumeTrue(
        "needs per-thread allocation counters",
        bean instanceof com.sun.management.ThreadMXBean tb
            && tb.isThreadAllocatedMemorySupported()
            && tb.isThreadAllocatedMemoryEnabled());
    com.sun.management.ThreadMXBean tb = (com.sun.management.ThreadMXBean) bean;

    int small = 1024;
    int large = 1 << 26;
    // warm up both paths once
    searcher.rewrite(prefix(new FrequentTermsSpanBooleanQueryRewrite(small)));
    searcher.rewrite(prefix(new TopTermsSpanBooleanQueryRewrite(large)));

    long t0 = tb.getCurrentThreadAllocatedBytes();
    Query topTerms = searcher.rewrite(prefix(new TopTermsSpanBooleanQueryRewrite(large)));
    long t1 = tb.getCurrentThreadAllocatedBytes();
    Query frequent = searcher.rewrite(prefix(new FrequentTermsSpanBooleanQueryRewrite(large)));
    long t2 = tb.getCurrentThreadAllocatedBytes();
    long topTermsBytes = t1 - t0;
    long frequentBytes = t2 - t1;

    // both keep the same 3 terms
    assertEquals(3, ((SpanOrQuery) topTerms).getClauses().length);
    assertEquals(3, ((SpanOrQuery) frequent).getClauses().length);
    // control: the TopTerms sibling does not allocate by its size
    assertTrue("TopTerms allocated " + topTermsBytes + " bytes", topTermsBytes < (16L << 20));
    // fails on main: the PriorityQueue is pre-allocated with maxSize + 1 slots
    // (2^26 + 1 references) before any term is collected
    assertTrue(
        "FrequentTerms(" + large + ") allocated " + frequentBytes + " bytes to rewrite 3 terms",
        frequentBytes < (16L << 20));
  }
}
