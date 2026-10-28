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
package verify.v1;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.TokenSources;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check (public API only, own package): the classic Highlighter, fed a token stream
 * built from term vectors with positions and offsets, should highlight the matching term of a
 * query that also contains a FieldExistsQuery (or a query that rewrites to one), as it does for a
 * re-analysed token stream.
 */
public class TestFieldExistsTermVectorHighlight extends LuceneTestCase {

  private static final String TEXT = "the quick brown fox";
  private static final String EXPECTED = "the quick brown <B>fox</B>";

  private Directory dir;
  private DirectoryReader reader;
  private Analyzer analyzer;
  private int docId;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    dir = newDirectory();
    FieldType tvType = new FieldType(TextField.TYPE_STORED);
    tvType.setStoreTermVectors(true);
    tvType.setStoreTermVectorPositions(true);
    tvType.setStoreTermVectorOffsets(true);
    tvType.freeze();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new Field("body", TEXT, tvType));
      doc.add(new NumericDocValuesField("dv", 7L));
      doc.add(new LongField("num", 42L, Field.Store.NO));
      w.addDocument(doc);
    }
    reader = DirectoryReader.open(dir);
    docId = new IndexSearcher(reader).search(new TermQuery(new Term("body", "fox")), 1).scoreDocs[0]
        .doc;
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    analyzer.close();
    super.tearDown();
  }

  private static Query foxAnd(Query filter) {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("body", "fox")), BooleanClause.Occur.MUST)
        .add(filter, BooleanClause.Occur.FILTER)
        .build();
  }

  private TokenStream termVectorStream() throws Exception {
    Fields tv = reader.termVectors().get(docId);
    TokenStream ts = TokenSources.getTermVectorTokenStreamOrNull("body", tv, -1);
    assertNotNull("term vectors with offsets expected", ts);
    return ts;
  }

  private void assertHighlights(Query query, TokenStream ts) throws Exception {
    // precondition: the document matches the query on the real index
    assertEquals(1, new IndexSearcher(reader).count(query));
    Highlighter highlighter = new Highlighter(new QueryScorer(query, "body"));
    String fragment;
    try {
      fragment = highlighter.getBestFragment(ts, TEXT);
    } catch (RuntimeException e) {
      throw new AssertionError("highlighting threw for query " + query + ": " + e, e);
    }
    assertEquals(EXPECTED, fragment);
  }

  /** Bug claim: FieldExistsQuery on a doc-values field, term-vector token stream. */
  public void testTermVectorStreamFieldExistsOnDocValuesField() throws Exception {
    assertHighlights(foxAnd(new FieldExistsQuery("dv")), termVectorStream());
  }

  /** Bug claim: FieldExistsQuery on the highlighted field itself (it has norms). */
  public void testTermVectorStreamFieldExistsOnHighlightedField() throws Exception {
    assertHighlights(foxAnd(new FieldExistsQuery("body")), termVectorStream());
  }

  /** Bug claim: an open-ended long range, which rewrites to a FieldExistsQuery. */
  public void testTermVectorStreamOpenEndedLongRange() throws Exception {
    assertHighlights(
        foxAnd(LongField.newRangeQuery("num", Long.MIN_VALUE, Long.MAX_VALUE)),
        termVectorStream());
  }

  /** Control: same FieldExistsQuery query, re-analysed token stream. */
  public void testControlReanalysedStreamFieldExists() throws Exception {
    assertHighlights(
        foxAnd(new FieldExistsQuery("dv")), analyzer.tokenStream("body", TEXT));
  }

  /** Control: term-vector token stream, query without FieldExistsQuery. */
  public void testControlTermVectorStreamPlainTerm() throws Exception {
    assertHighlights(new TermQuery(new Term("body", "fox")), termVectorStream());
  }
}
