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
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * With the default flags (WEIGHT_MATCHES) and a field indexed without offsets, the
 * UnifiedHighlighter re-analyzes the text into a MemoryIndex that does not store payloads, so a
 * payload-checking query that matched the document never matches during highlighting and nothing
 * is highlighted. The same document indexed with offsets (or highlighted with WEIGHT_MATCHES
 * disabled) gets the correct highlight.
 */
public class TestUnifiedHighlighterReanalysisPayloads extends LuceneTestCase {

  /** Sets the payload of every token to "p" + its position. */
  static final class PositionPayloadFilter extends TokenFilter {
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

  static final Analyzer ANALYZER =
      new Analyzer() {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
          Tokenizer tokenizer = new MockTokenizer(MockTokenizer.SIMPLE, true);
          return new TokenStreamComponents(tokenizer, new PositionPayloadFilter(tokenizer));
        }
      };

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
      // positions: both=0 terms=1 only=2 terms=3; only the second "terms" has payload "p3"
      doc.add(new Field("body", "both terms. only terms.", type));
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

  public void testSpanPayloadCheckQuery() throws IOException {
    Query query =
        new SpanPayloadCheckQuery(
            new SpanTermQuery(new Term("body", "terms")), List.of(new BytesRef("p3")));
    String expected = "both terms. only <b>terms</b>.";
    // offsets in postings: correct
    assertEquals(expected, highlight(true, true, query));
    // re-analysis without WEIGHT_MATCHES: correct
    assertEquals(expected, highlight(false, false, query));
    // re-analysis with the default WEIGHT_MATCHES: nothing is highlighted
    assertEquals(expected, highlight(false, true, query));
  }

  public void testIntervalsTermWithPayloadFilter() throws IOException {
    Query query =
        new IntervalQuery(
            "body", Intervals.term("terms", payload -> new BytesRef("p3").equals(payload)));
    String expected = "both terms. only <b>terms</b>.";
    assertEquals(expected, highlight(true, true, query));
    assertEquals(expected, highlight(false, true, query));
  }
}
