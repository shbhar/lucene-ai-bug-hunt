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
package org.apache.lucene.search.highlight;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Highlighting a hit of {@code +body:fox #FieldExistsQuery(price)} with the classic Highlighter
 * over the term-vector token stream of the hit throws IllegalStateException, while the same query
 * over a re-analysed token stream highlights "fox". GITHUB#12088 made the extractor skip
 * FieldExistsQuery ("Don't throw UOE when highlighting FieldExistsQuery").
 */
public class TestHighlightFieldExistsTermVectors extends LuceneTestCase {

  private static final String TEXT = "the quick brown fox";

  private static Query query() {
    return query(new FieldExistsQuery("price"));
  }

  private static Query query(Query filter) {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("body", "fox")), Occur.MUST)
        .add(filter, Occur.FILTER)
        .build();
  }

  private static Directory index() throws Exception {
    Directory dir = newDirectory();
    FieldType tv = new FieldType(TextField.TYPE_STORED);
    tv.setStoreTermVectors(true);
    tv.setStoreTermVectorPositions(true);
    tv.setStoreTermVectorOffsets(true);
    tv.freeze();
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
      Document d = new Document();
      d.add(new Field("body", TEXT, tv));
      d.add(new NumericDocValuesField("price", 3));
      d.add(new LongField("size", 7, Field.Store.NO));
      w.addDocument(d);
    }
    return dir;
  }

  /** Fails on 10.5.0 and later: IllegalStateException from FieldExistsQuery.rewrite. */
  public void testTermVectorTokenStream() throws Exception {
    assertHighlightsFromTermVectors(query());
  }

  /**
   * Same failure for an open-ended range on a LongField (size:[* TO *]): it rewrites to
   * FieldExistsQuery before the extractor sees it.
   */
  public void testTermVectorTokenStreamOpenEndedLongRange() throws Exception {
    assertHighlightsFromTermVectors(
        query(LongField.newRangeQuery("size", Long.MIN_VALUE, Long.MAX_VALUE)));
  }

  private static void assertHighlightsFromTermVectors(Query q) throws Exception {
    try (Directory dir = index();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      TopDocs td = new IndexSearcher(reader).search(q, 10);
      assertEquals(1, td.scoreDocs.length); // the document matches the query
      int doc = td.scoreDocs[0].doc;
      TokenStream ts =
          TokenSources.getTermVectorTokenStreamOrNull("body", reader.termVectors().get(doc), -1);
      assertNotNull(ts);
      Highlighter highlighter = new Highlighter(new QueryScorer(q, "body"));
      assertEquals("the quick brown <B>fox</B>", highlighter.getBestFragment(ts, TEXT));
    }
  }

  /** Control: the same query and document over a re-analysed token stream (passes). */
  public void testReanalysedTokenStream() throws Exception {
    Query q = query();
    TokenStream ts = new MockAnalyzer(random()).tokenStream("body", TEXT);
    Highlighter highlighter = new Highlighter(new QueryScorer(q, "body"));
    assertEquals("the quick brown <B>fox</B>", highlighter.getBestFragment(ts, TEXT));
  }
}
