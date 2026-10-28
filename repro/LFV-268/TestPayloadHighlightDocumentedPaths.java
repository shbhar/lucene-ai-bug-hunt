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
package org.apache.lucene.search.uhighlight;

import java.io.IOException;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.intervals.IntervalQuery;
import org.apache.lucene.queries.intervals.Intervals;
import org.apache.lucene.queries.payloads.SpanPayloadCheckQuery;
import org.apache.lucene.queries.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * The field is indexed WITH offsets in postings (DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS), which
 * is exactly what the highlight(field, query, topDocs) javadoc asks for. A payload-checking query
 * that also contains a multi-term part makes the UnifiedHighlighter switch from the POSTINGS offset
 * source to re-analysis on its own (getOptimizedOffsetSource), so the payload-less MemoryIndex is
 * used and the payload check cannot be evaluated. Controls: the same query with WEIGHT_MATCHES
 * disabled highlights correctly.
 */
public class TestPayloadHighlightDocumentedPaths extends LuceneTestCase {

  /** Sets the payload of every token to "p" + its position. */
  static final class PositionPayloadFilter extends TokenFilter {
    private final PayloadAttribute payloadAtt = addAttribute(PayloadAttribute.class);
    private int position = -1;

    PositionPayloadFilter(TokenStream in) {
      super(in);
    }

    @Override
    public boolean incrementToken() throws IOException {
      if (input.incrementToken() == false) {
        return false;
      }
      position++;
      payloadAtt.setPayload(new BytesRef("p" + position));
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      position = -1;
    }
  }

  static final Analyzer ANALYZER =
      new Analyzer() {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
          Tokenizer tokenizer = new MockTokenizer(MockTokenizer.SIMPLE, true);
          return new TokenStreamComponents(tokenizer, new PositionPayloadFilter(tokenizer));
        }
      };

  // positions: alpha=0 fox=1 beta=2 fox=3; only the second "fox" has payload "p3"
  static final String TEXT = "alpha fox beta fox";
  static final String EXPECTED = "alpha fox beta <b>fox</b>";

  private String highlightWithOffsetsInPostings(boolean weightMatches, Query query)
      throws IOException {
    FieldType type = new FieldType(TextField.TYPE_STORED);
    type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    type.freeze();
    try (Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(ANALYZER))) {
      Document doc = new Document();
      doc.add(new Field("body", TEXT, type));
      writer.addDocument(doc);
      writer.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        TopDocs topDocs = searcher.search(query, 10);
        assertEquals("the query matches the document", 1, topDocs.scoreDocs.length);
        UnifiedHighlighter highlighter =
            UnifiedHighlighter.builder(searcher, ANALYZER)
                .withBreakIterator(WholeBreakIterator::new)
                .withWeightMatches(weightMatches)
                .build();
        return highlighter.highlight("body", query, topDocs)[0];
      }
    }
  }

  private static Query wildcardPayloadQuery() {
    SpanQuery fo = new SpanMultiTermQueryWrapper<>(new WildcardQuery(new Term("body", "fo*")));
    return new SpanPayloadCheckQuery(fo, List.of(new BytesRef("p3")));
  }

  /** Control: WEIGHT_MATCHES off, field with offsets in postings. */
  public void testControlWildcardPayloadWithoutWeightMatches() throws IOException {
    assertEquals(EXPECTED, highlightWithOffsetsInPostings(false, wildcardPayloadQuery()));
  }

  /** Default flags, field with offsets in postings: nothing highlighted. */
  public void testWildcardPayloadDefaultFlagsOffsetsInPostings() throws IOException {
    assertEquals(EXPECTED, highlightWithOffsetsInPostings(true, wildcardPayloadQuery()));
  }

  /**
   * Default flags, field with offsets in postings, interval query with a payload filter plus a
   * prefix source: the highlight call throws IllegalArgumentException.
   */
  public void testIntervalPayloadPlusPrefixDefaultFlagsOffsetsInPostings() throws IOException {
    Query query =
        new IntervalQuery(
            "body",
            Intervals.or(
                Intervals.term("fox", payload -> new BytesRef("p3").equals(payload)),
                Intervals.prefix(new BytesRef("zz"))));
    assertEquals(EXPECTED, highlightWithOffsetsInPostings(true, query));
  }

  /** highlightWithoutSearcher always uses re-analysis (its javadoc); default flags. */
  public void testHighlightWithoutSearcherDefaultFlags() throws IOException {
    UnifiedHighlighter highlighter =
        UnifiedHighlighter.builderWithoutSearcher(ANALYZER)
            .withBreakIterator(WholeBreakIterator::new)
            .build();
    Query query =
        new SpanPayloadCheckQuery(
            new org.apache.lucene.queries.spans.SpanTermQuery(new Term("body", "fox")),
            List.of(new BytesRef("p3")));
    assertEquals(
        EXPECTED, String.valueOf(highlighter.highlightWithoutSearcher("body", query, TEXT, 1)));
  }

  /** Control for the above: highlightWithoutSearcher with WEIGHT_MATCHES off. */
  public void testControlHighlightWithoutSearcherNoWeightMatches() throws IOException {
    UnifiedHighlighter highlighter =
        UnifiedHighlighter.builderWithoutSearcher(ANALYZER)
            .withBreakIterator(WholeBreakIterator::new)
            .withWeightMatches(false)
            .build();
    Query query =
        new SpanPayloadCheckQuery(
            new org.apache.lucene.queries.spans.SpanTermQuery(new Term("body", "fox")),
            List.of(new BytesRef("p3")));
    assertEquals(
        EXPECTED, String.valueOf(highlighter.highlightWithoutSearcher("body", query, TEXT, 1)));
  }
}
