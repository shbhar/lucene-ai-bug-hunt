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
import org.apache.lucene.tests.analysis.MockTokenizer;
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
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Independent check: payload-sensitive queries highlighted by the UnifiedHighlighter when offsets
 * come from re-analysis (field indexed without offsets) and the default flags are used.
 *
 * <p>Document text: "alpha fox beta fox". Each token gets a one-byte payload equal to its position
 * (0..3), so the first "fox" carries payload {1} and the second carries payload {3}. The query
 * accepts only "fox" with payload {3}, so only the second "fox" matches.
 *
 * <p>Tests named testControl* use another offset source or flag set and are expected to pass; they
 * show what the correct highlight is. Tests named testReanalysis* use re-analysis with the default
 * flags and assert that same highlight.
 */
public class TestReanalysisPayloadQueries extends LuceneTestCase {

  private static final String FIELD = "body";
  private static final String TEXT = "alpha fox beta fox";
  private static final String EXPECTED = "alpha fox beta <b>fox</b>";
  private static final BytesRef PAYLOAD_OF_SECOND_FOX = new BytesRef(new byte[] {3});

  /** Sets payload = one byte holding the token position. */
  private static final class PositionPayloadFilter extends TokenFilter {
    private final PayloadAttribute payloadAtt = addAttribute(PayloadAttribute.class);
    private int pos;

    PositionPayloadFilter(TokenStream in) {
      super(in);
    }

    @Override
    public boolean incrementToken() throws IOException {
      if (input.incrementToken() == false) {
        return false;
      }
      payloadAtt.setPayload(new BytesRef(new byte[] {(byte) pos++}));
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      pos = 0;
    }
  }

  private static Analyzer payloadAnalyzer() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tok = new MockTokenizer(MockTokenizer.WHITESPACE, true);
        return new TokenStreamComponents(tok, new PositionPayloadFilter(tok));
      }
    };
  }

  private static Query spanPayloadQuery() {
    return new SpanPayloadCheckQuery(
        new SpanTermQuery(new Term(FIELD, "fox")), List.of(PAYLOAD_OF_SECOND_FOX));
  }

  private static Query intervalPayloadQuery() {
    return new IntervalQuery(
        FIELD, Intervals.term("fox", p -> p != null && p.bytesEquals(PAYLOAD_OF_SECOND_FOX)));
  }

  private static FieldType reanalysisType() {
    return TextField.TYPE_STORED; // positions, no offsets, no term vectors -> ANALYSIS
  }

  private static FieldType postingsOffsetsType() {
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.freeze();
    return ft;
  }

  /**
   * Indexes TEXT once with the given field type, checks the query matches the document through
   * normal search, then highlights it. weightMatches=true is the builder default; false calls
   * Builder#withWeightMatches(false) (the other flags keep their defaults).
   */
  private String searchAndHighlight(FieldType ft, Query query, boolean weightMatches)
      throws IOException {
    Analyzer analyzer = payloadAnalyzer();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new Field(FIELD, TEXT, ft));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs topDocs = searcher.search(query, 10);
        assertEquals("query must match the document in normal search", 1, topDocs.scoreDocs.length);
        UnifiedHighlighter.Builder b = UnifiedHighlighter.builder(searcher, analyzer);
        if (weightMatches == false) {
          b.withWeightMatches(false);
        }
        UnifiedHighlighter uh = b.build();
        assertEquals(
            weightMatches,
            uh.getFlags(FIELD).contains(UnifiedHighlighter.HighlightFlag.WEIGHT_MATCHES));
        String[] snippets = uh.highlight(FIELD, query, topDocs);
        assertEquals(1, snippets.length);
        return snippets[0];
      }
    } finally {
      analyzer.close();
    }
  }

  // ---- controls (expected to pass) ----

  public void testControlSpanPayloadPostingsOffsetsDefaultFlags() throws IOException {
    assertEquals(EXPECTED, searchAndHighlight(postingsOffsetsType(), spanPayloadQuery(), true));
  }

  public void testControlIntervalPayloadPostingsOffsetsDefaultFlags() throws IOException {
    assertEquals(EXPECTED, searchAndHighlight(postingsOffsetsType(), intervalPayloadQuery(), true));
  }

  public void testControlSpanPayloadReanalysisWithoutWeightMatches() throws IOException {
    assertEquals(
        EXPECTED,
        searchAndHighlight(reanalysisType(), spanPayloadQuery(), false));
  }

  // ---- the claim (expected to fail on affected versions) ----

  public void testReanalysisSpanPayloadDefaultFlags() throws IOException {
    assertEquals(EXPECTED, searchAndHighlight(reanalysisType(), spanPayloadQuery(), true));
  }

  public void testReanalysisIntervalPayloadDefaultFlags() throws IOException {
    assertEquals(EXPECTED, searchAndHighlight(reanalysisType(), intervalPayloadQuery(), true));
  }

  /** highlightWithoutSearcher is documented to always use OffsetSource.ANALYSIS. */
  public void testWithoutSearcherSpanPayloadDefaultFlags() throws IOException {
    Analyzer analyzer = payloadAnalyzer();
    try {
      UnifiedHighlighter uh = UnifiedHighlighter.builderWithoutSearcher(analyzer).build();
      Object res = uh.highlightWithoutSearcher(FIELD, spanPayloadQuery(), TEXT, 1);
      assertEquals(EXPECTED, String.valueOf(res));
    } finally {
      analyzer.close();
    }
  }
}
