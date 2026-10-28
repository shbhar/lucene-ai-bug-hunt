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
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;
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
import org.apache.lucene.queries.spans.SpanNotQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Security-reach probe for the re-analysis payload bug: which way do the wrong highlights go, and
 * what does the exception leave behind? Public API only.
 *
 * <p>Document text "alpha fox beta fox" (positions 0..3; "fox" at 1 and 3). The tests assert the
 * correct highlight, which is what the highlighter returns when offsets are in postings (the
 * postings controls). On main the re-analysis tests with the default flags (WEIGHT_MATCHES) show
 * the bug: a payload condition that should EXCLUDE an occurrence is ignored, so the excluded
 * occurrence is highlighted too.
 */
public class TestSecurityReachReanalysisPayloads extends LuceneTestCase {

  static final String TEXT = "alpha fox beta fox";

  /** Sets payload "p" + position on the positions accepted by the predicate, none elsewhere. */
  static final class SelectivePayloadFilter extends TokenFilter {
    private final PayloadAttribute payloadAtt = addAttribute(PayloadAttribute.class);
    private final PositionIncrementAttribute posIncAtt =
        addAttribute(PositionIncrementAttribute.class);
    private final IntPredicate withPayload;
    private int position = -1;

    SelectivePayloadFilter(TokenStream in, IntPredicate withPayload) {
      super(in);
      this.withPayload = withPayload;
    }

    @Override
    public boolean incrementToken() throws IOException {
      if (input.incrementToken() == false) {
        return false;
      }
      position += posIncAtt.getPositionIncrement();
      payloadAtt.setPayload(withPayload.test(position) ? new BytesRef("p" + position) : null);
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      position = -1;
    }
  }

  static Analyzer analyzer(IntPredicate withPayload) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new MockTokenizer(MockTokenizer.SIMPLE, true);
        return new TokenStreamComponents(
            tokenizer, new SelectivePayloadFilter(tokenizer, withPayload));
      }
    };
  }

  /** Every position has a payload. */
  static final Analyzer ALL_PAYLOADS = analyzer(p -> true);

  /** Only position 1 (the first "fox") has a payload. */
  static final Analyzer FIRST_FOX_ONLY = analyzer(p -> p == 1);

  private String highlight(Analyzer analyzer, boolean offsetsInPostings, Query query)
      throws IOException {
    FieldType type = new FieldType(TextField.TYPE_STORED);
    if (offsetsInPostings) {
      type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    }
    type.freeze();
    try (Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new Field("body", TEXT, type));
      writer.addDocument(doc);
      writer.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        // plain IndexSearcher: the test framework's asserting searcher calls Query#toString, which
        // throws for a null payload entry in SpanPayloadCheckQuery (unrelated side observation)
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs topDocs = searcher.search(query, 10);
        assertEquals("the query matches the document", 1, topDocs.scoreDocs.length);
        UnifiedHighlighter highlighter =
            UnifiedHighlighter.builder(searcher, analyzer)
                .withBreakIterator(WholeBreakIterator::new)
                .build(); // default flags: WEIGHT_MATCHES on
        assertTrue(
            highlighter.getFlags("body").contains(UnifiedHighlighter.HighlightFlag.WEIGHT_MATCHES));
        String out = highlighter.highlight("body", query, topDocs)[0];
        System.out.println(
            "PROBE " + getTestName() + " offsetsInPostings=" + offsetsInPostings + " -> " + out);
        return out;
      }
    }
  }

  /** "fox" but not the "fox" whose payload is p3: only the first "fox" is a match. */
  private static Query spanNotPayload() {
    return new SpanNotQuery(
        new SpanTermQuery(new Term("body", "fox")),
        new SpanPayloadCheckQuery(
            new SpanTermQuery(new Term("body", "fox")), List.of(new BytesRef("p3"))));
  }

  /** "fox" with no payload (a null entry; supported by SpanPayloadCheckQuery's PayloadChecker). */
  private static Query noPayloadCheck() {
    return new SpanPayloadCheckQuery(
        new SpanTermQuery(new Term("body", "fox")), Arrays.asList((BytesRef) null));
  }

  public void testSpanNotPayloadExclusionPostingsControl() throws IOException {
    assertEquals("alpha <b>fox</b> beta fox", highlight(ALL_PAYLOADS, true, spanNotPayload()));
  }

  public void testSpanNotPayloadExclusionReanalysis() throws IOException {
    // the exclusion never matches in the payload-less MemoryIndex, so the excluded "fox" is
    // highlighted too
    assertEquals("alpha <b>fox</b> beta fox", highlight(ALL_PAYLOADS, false, spanNotPayload()));
  }

  public void testNoPayloadCheckPostingsControl() throws IOException {
    assertEquals("alpha fox beta <b>fox</b>", highlight(FIRST_FOX_ONLY, true, noPayloadCheck()));
  }

  public void testNoPayloadCheckReanalysis() throws IOException {
    // every position looks payload-less in the MemoryIndex, so the first "fox" (which has a
    // payload) is highlighted too
    assertEquals("alpha fox beta <b>fox</b>", highlight(FIRST_FOX_ONLY, false, noPayloadCheck()));
  }

  /**
   * The exception variant: it is a plain IllegalArgumentException out of highlight(), and the same
   * highlighter, searcher and reader keep working afterwards. Passes on main (throws) and with the
   * fix (does not throw); the printed line records which.
   */
  public void testIntervalExceptionLeavesNoState() throws IOException {
    FieldType type = new FieldType(TextField.TYPE_STORED);
    type.freeze();
    try (Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(ALL_PAYLOADS))) {
      for (int i = 0; i < 3; i++) {
        Document doc = new Document();
        doc.add(new Field("body", TEXT, type));
        writer.addDocument(doc);
      }
      writer.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        // plain IndexSearcher: the test framework's asserting searcher calls Query#toString, which
        // throws for a null payload entry in SpanPayloadCheckQuery (unrelated side observation)
        IndexSearcher searcher = new IndexSearcher(reader);
        UnifiedHighlighter highlighter =
            UnifiedHighlighter.builder(searcher, ALL_PAYLOADS)
                .withBreakIterator(WholeBreakIterator::new)
                .build();
        Query payloadIntervals =
            new IntervalQuery(
                "body", Intervals.term("fox", payload -> new BytesRef("p3").equals(payload)));
        TopDocs hits = searcher.search(payloadIntervals, 10);
        assertEquals("the interval query matches all 3 documents", 3, hits.scoreDocs.length);
        String outcome;
        try {
          String[] out = highlighter.highlight("body", payloadIntervals, hits);
          outcome = "no exception: " + Arrays.toString(out);
        } catch (IllegalArgumentException e) {
          outcome = "IllegalArgumentException: " + e.getMessage();
        }
        System.out.println("PROBE " + getTestName() + " payload interval highlight -> " + outcome);
        // afterwards, the same instances still work for an ordinary query
        Query plain = new TermQuery(new Term("body", "alpha"));
        TopDocs plainHits = searcher.search(plain, 10);
        assertEquals(3, plainHits.scoreDocs.length);
        String[] plainOut = highlighter.highlight("body", plain, plainHits);
        assertEquals(3, plainOut.length);
        for (String s : plainOut) {
          assertEquals("<b>alpha</b> fox beta fox", s);
        }
        assertEquals(3, reader.numDocs());
      }
    }
  }
}
