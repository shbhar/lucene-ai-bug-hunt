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
package org.apache.lucene.queries.intervals;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.payloads.DelimitedPayloadTokenFilter;
import org.apache.lucene.analysis.payloads.IdentityEncoder;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The MatchesIterator of a payload-filtered term source throws UnsupportedOperationException from
 * getQuery(). MatchesIterator#getQuery must return the query of the current match (a TermQuery for
 * sub-matches), and the minimizing conjunctions call it while caching sub-matches, so
 * Weight#matches of ordered/unordered/maxgaps/atLeast over a payload-filtered term throws, and so
 * does Matches-based highlighting of most positional combinations.
 */
public class TestPayloadFilteredIntervalsMatchesGetQuery extends LuceneTestCase {

  private static Analyzer payloadAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new WhitespaceTokenizer();
        return new TokenStreamComponents(
            t, new DelimitedPayloadTokenFilter(t, '|', new IdentityEncoder()));
      }
    };
  }

  private static IntervalsSource dogAsNoun() {
    return Intervals.term("dog", p -> p != null && p.utf8ToString().equals("noun"));
  }

  public void testWeightMatchesOfConjunctions() throws IOException {
    try (Directory dir = newDirectory();
        Analyzer analyzer = payloadAnalyzer()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new TextField("f", "big|adj dog|noun", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        LeafReaderContext ctx = searcher.getIndexReader().leaves().get(0);
        IntervalsSource big = Intervals.term("big");
        IntervalsSource[] sources = {
          Intervals.ordered(big, dogAsNoun()),
          Intervals.unordered(big, dogAsNoun()),
          Intervals.maxgaps(1, Intervals.ordered(big, dogAsNoun())),
        };
        StringBuilder failures = new StringBuilder();
        for (IntervalsSource source : sources) {
          Query q = new IntervalQuery("f", source);
          assertEquals(q.toString(), 1, searcher.count(q)); // the document matches
          Weight weight = searcher.createWeight(searcher.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1f);
          try {
            Matches matches = weight.matches(ctx, 0);
            assertNotNull(q.toString(), matches);
            MatchesIterator mi = matches.getMatches("f");
            assertTrue(mi.next());
            assertEquals(0, mi.startPosition());
            assertEquals(1, mi.endPosition());
          } catch (UnsupportedOperationException e) {
            failures.append("\n  Weight#matches of ").append(q).append(" threw ").append(e);
          }
        }
        assertEquals("unexpected exceptions:" + failures, 0, failures.length());
      }
    }
  }

  /** The default UnifiedHighlighter (WEIGHT_MATCHES) on a field with offsets in the postings. */
  public void testUnifiedHighlighter() throws IOException {
    FieldType withOffsets = new FieldType(TextField.TYPE_STORED);
    withOffsets.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    withOffsets.freeze();
    try (Directory dir = newDirectory();
        Analyzer analyzer = payloadAnalyzer()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new Field("f", "big|adj dog|noun", withOffsets));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        Query q = new IntervalQuery("f", Intervals.phrase(Intervals.term("big"), dogAsNoun()));
        TopDocs hits = searcher.search(q, 10);
        assertEquals(1, hits.scoreDocs.length);
        UnifiedHighlighter highlighter = UnifiedHighlighter.builder(searcher, analyzer).build();
        String[] snippets = highlighter.highlight("f", q, hits);
        assertEquals(1, snippets.length);
        assertTrue(snippets[0], snippets[0].contains("<b>"));
      }
    }
  }
}
