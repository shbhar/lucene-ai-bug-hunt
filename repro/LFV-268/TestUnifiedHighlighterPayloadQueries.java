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
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
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
import org.apache.lucene.queries.spans.SpanNotQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Payload-checking queries must be highlighted the same way whether the UnifiedHighlighter reads
 * offsets from postings or re-analyzes the text into a MemoryIndex. Every token of "both terms.
 * only terms." gets the payload "p" + its position, so only the second "terms" (position 3) carries
 * "p3". Each test first checks a configuration that highlights correctly, then the default flags
 * (WEIGHT_MATCHES on) with re-analysis.
 */
public class TestUnifiedHighlighterPayloadQueries extends LuceneTestCase {

  private static final String TEXT = "both terms. only terms.";
  private static final String SECOND = "both terms. only <b>terms</b>.";
  private static final String FIRST = "both <b>terms</b>. only terms.";
  private static final BytesRef P3 = new BytesRef("p3");

  /** Sets the payload of every token to "p" + its position. */
  private static final class PositionPayloadFilter extends TokenFilter {
    private final PayloadAttribute payloadAtt = addAttribute(PayloadAttribute.class);
    private final PositionIncrementAttribute posIncAtt =
        addAttribute(PositionIncrementAttribute.class);
    private int position = -1;

    PositionPayloadFilter(TokenStream in) {
      super(in);
    }

    @Override
    public boolean incrementToken() throws IOException {
      if (input.incrementToken() == false) {
        return false;
      }
      position += posIncAtt.getPositionIncrement();
      payloadAtt.setPayload(new BytesRef("p" + position));
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      position = -1;
    }
  }

  private static final Analyzer ANALYZER =
      new Analyzer() {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
          Tokenizer tokenizer = new MockTokenizer(MockTokenizer.SIMPLE, true);
          return new TokenStreamComponents(tokenizer, new PositionPayloadFilter(tokenizer));
        }
      };

  /**
   * Indexes TEXT in field "body" (a plain TextField, or with offsets in postings), checks that the
   * query matches it, and highlights it.
   */
  private String highlight(boolean offsetsInPostings, boolean weightMatches, Query query)
      throws IOException {
    FieldType type = new FieldType(TextField.TYPE_STORED);
    if (offsetsInPostings) {
      type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    }
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

  /** "terms" with payload p3. */
  private static SpanQuery termsWithP3() {
    return new SpanPayloadCheckQuery(new SpanTermQuery(new Term("body", "terms")), List.of(P3));
  }

  /** A plain TextField: the matching "terms" is not highlighted. */
  public void testSpanPayloadCheck() throws IOException {
    assertEquals("offsets in postings", SECOND, highlight(true, true, termsWithP3()));
    assertEquals("re-analysis, WEIGHT_MATCHES off", SECOND, highlight(false, false, termsWithP3()));
    assertEquals("re-analysis, default flags", SECOND, highlight(false, true, termsWithP3()));
  }

  /** A field with offsets: the wildcard part makes the highlighter re-analyze the text anyway. */
  public void testSpanPayloadCheckWildcardOnFieldWithOffsets() throws IOException {
    Query query =
        new SpanPayloadCheckQuery(
            new SpanMultiTermQueryWrapper<>(new WildcardQuery(new Term("body", "te*"))),
            List.of(P3));
    assertEquals("offsets in postings, WEIGHT_MATCHES off", SECOND, highlight(true, false, query));
    assertEquals("offsets in postings, default flags", SECOND, highlight(true, true, query));
  }

  /** "terms, except the terms with payload p3": the excluded "terms" is highlighted too. */
  public void testSpanNotPayloadExclusion() throws IOException {
    Query query = new SpanNotQuery(new SpanTermQuery(new Term("body", "terms")), termsWithP3());
    assertEquals("offsets in postings", FIRST, highlight(true, true, query));
    assertEquals("re-analysis, default flags", FIRST, highlight(false, true, query));
  }

  /** A payload-filtered interval: highlight() throws IllegalArgumentException. */
  public void testIntervalPayloadFilter() throws IOException {
    Query query = new IntervalQuery("body", Intervals.term("terms", P3::equals));
    assertEquals("offsets in postings", SECOND, highlight(true, true, query));
    assertEquals("re-analysis, default flags", SECOND, highlight(false, true, query));
  }

  /** highlightWithoutSearcher always re-analyzes. */
  public void testHighlightWithoutSearcher() throws IOException {
    UnifiedHighlighter weightMatchesOff =
        UnifiedHighlighter.builderWithoutSearcher(ANALYZER)
            .withBreakIterator(WholeBreakIterator::new)
            .withWeightMatches(false)
            .build();
    UnifiedHighlighter defaults =
        UnifiedHighlighter.builderWithoutSearcher(ANALYZER)
            .withBreakIterator(WholeBreakIterator::new)
            .build();
    assertEquals(
        "WEIGHT_MATCHES off",
        SECOND,
        String.valueOf(weightMatchesOff.highlightWithoutSearcher("body", termsWithP3(), TEXT, 1)));
    assertEquals(
        "default flags",
        SECOND,
        String.valueOf(defaults.highlightWithoutSearcher("body", termsWithP3(), TEXT, 1)));
  }
}
