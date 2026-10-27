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
package org.apache.lucene.facet.range;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.FunctionMatchQuery;
import org.apache.lucene.queries.function.valuesource.QueryValueSource;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DoubleRange#getQuery(Query, DoubleValuesSource) and LongRange#getQuery(Query, LongValuesSource)
 * return a Query that calls getValues() on the values source as given and never rewrites it against
 * the searcher, although DoubleValuesSource#rewrite says "Queries that use DoubleValuesSource
 * objects should call rewrite() during Query#createWeight(IndexSearcher, ScoreMode, float)".
 */
public class TestRangeGetQueryRewritesValuesSource extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    String[] bodies = {"a", "a a", "a a a a", "a b c d e f g h", "b"};
    for (String body : bodies) {
      Document doc = new Document();
      doc.add(new TextField("body", body, Field.Store.NO));
      w.addDocument(doc);
    }
    reader = w.getReader();
    w.close();
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /** A query-score source: DoubleValuesSource.fromQuery needs rewrite() before getValues(). */
  public void testFromQuerySourceInDoubleRangeQuery() throws Exception {
    IndexSearcher searcher = newSearcher(reader);
    DoubleValuesSource scores = DoubleValuesSource.fromQuery(new TermQuery(new Term("body", "a")));
    DoubleRange range = new DoubleRange("matches a", Math.nextUp(0d), true, Double.MAX_VALUE, true);
    int expected = searcher.count(new FunctionMatchQuery(scores, range::accept));
    assertEquals(4, expected); // the four documents that contain "a"
    assertEquals(expected, searcher.count(range.getQuery(null, scores)));
  }

  /** Same source through the LongRange query (DoubleValuesSource#toLongValuesSource). */
  public void testFromQuerySourceInLongRangeQuery() throws Exception {
    IndexSearcher searcher = newSearcher(reader);
    // a boost of 10 makes every score of the four "a" documents at least 1 after the long cast
    DoubleValuesSource boosted =
        DoubleValuesSource.fromQuery(
            new BoostQuery(new TermQuery(new Term("body", "a")), 10f));
    LongRange range = new LongRange("score >= 1", 1, true, Long.MAX_VALUE, true);
    int expected =
        searcher.count(
            new FunctionMatchQuery(boosted, v -> (long) v >= 1)); // FunctionMatchQuery rewrites
    assertTrue(expected > 0);
    assertEquals(expected, searcher.count(range.getQuery(null, boosted.toLongValuesSource())));
  }

  /**
   * Silent variant: a ValueSource exposed as a DoubleValuesSource reads the searcher (and so its
   * Similarity) from the context that rewrite() provides. Without rewrite, QueryValueSource falls
   * back to a fresh IndexSearcher with the default Similarity, so the range query matches by other
   * scores than the searcher it runs on.
   */
  public void testQueryValueSourceUsesTheSearchersSimilarity() throws Exception {
    IndexSearcher searcher = newSearcher(reader);
    searcher.setSimilarity(new ClassicSimilarity());
    Query q = new TermQuery(new Term("body", "a"));
    DoubleValuesSource source = new QueryValueSource(q, 0f).asDoubleValuesSource();

    // pick a range that contains exactly the top ClassicSimilarity score of the searcher
    DoubleValuesSource rewritten = source.rewrite(searcher);
    double top = searcher.search(q, 1).scoreDocs[0].score;
    DoubleRange range = new DoubleRange("top", top, true, top, true);
    int viaFunctionMatch = searcher.count(new FunctionMatchQuery(source, range::accept));
    int viaRewrittenSource = searcher.count(range.getQuery(null, rewritten));
    assertTrue(viaFunctionMatch > 0);
    assertEquals(viaFunctionMatch, viaRewrittenSource);
    // the facet range query over the very same source must agree with both
    assertEquals(viaFunctionMatch, searcher.count(range.getQuery(null, source)));
  }
}
