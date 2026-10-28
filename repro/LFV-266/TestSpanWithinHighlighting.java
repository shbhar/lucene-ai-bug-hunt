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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
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
import org.apache.lucene.queries.spans.SpanNearQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.queries.spans.SpanWithinQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.matchhighlight.MatchRegionRetriever;
import org.apache.lucene.search.matchhighlight.OffsetRange;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * "ab ac bc" is indexed twice: with offsets in the postings and with positions only. A
 * SpanWithinQuery(big = "ab bc" with slop 1, little = ac) matches "ac" only, so every highlighter
 * should mark "ac" only, and the same text should give the same result in both fields.
 */
public class TestSpanWithinHighlighting extends LuceneTestCase {

  private static final String OFFSETS = "offsets"; // DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
  private static final String POSITIONS = "positions"; // TextField default: positions, no offsets
  private static final String TEXT = "ab ac bc"; // ab [0,2) ac [3,5) bc [6,8)

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;
  private Analyzer analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new StandardAnalyzer();
    dir = newDirectory();
    FieldType withOffsets = new FieldType(TextField.TYPE_STORED);
    withOffsets.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new Field(OFFSETS, TEXT, withOffsets));
      doc.add(new Field(POSITIONS, TEXT, TextField.TYPE_STORED));
      writer.addDocument(doc);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    analyzer.close();
    super.tearDown();
  }

  private static Query spanWithin(String field) {
    SpanQuery big =
        new SpanNearQuery(
            new SpanQuery[] {
              new SpanTermQuery(new Term(field, "ab")), new SpanTermQuery(new Term(field, "bc"))
            },
            1,
            true);
    return new SpanWithinQuery(big, new SpanTermQuery(new Term(field, "ac")));
  }

  private static Query intervalsContainedBy(String field) {
    return new IntervalQuery(
        field,
        Intervals.containedBy(
            Intervals.term("ac"),
            Intervals.maxgaps(1, Intervals.ordered(Intervals.term("ab"), Intervals.term("bc")))));
  }

  /** The UnifiedHighlighter with its default settings (weight matches on). */
  private String highlight(String field, Query query) throws IOException {
    TopDocs topDocs = searcher.search(query, 10);
    assertEquals(1, topDocs.scoreDocs.length);
    UnifiedHighlighter highlighter = UnifiedHighlighter.builder(searcher, analyzer).build();
    return highlighter.highlight(field, query, topDocs)[0];
  }

  /** The offset ranges MatchRegionRetriever reports for the only document. */
  private String matchRegions(String field, Query query) throws IOException {
    TopDocs topDocs = searcher.search(query, 10);
    List<String> regions = new ArrayList<>();
    MatchRegionRetriever retriever =
        new MatchRegionRetriever(
            searcher, searcher.rewrite(query), analyzer, f -> false, f -> true);
    retriever.highlightDocuments(
        topDocs,
        (docId, leafReader, leafDocId, fieldValueProvider, hits) -> {
          for (OffsetRange range : hits.getOrDefault(field, List.of())) {
            regions.add("[" + range.from + "," + range.to + ")");
          }
        });
    return regions.toString();
  }

  public void testUnifiedHighlighterFieldWithOffsets() throws IOException {
    assertEquals("ab <b>ac</b> bc", highlight(OFFSETS, spanWithin(OFFSETS)));
  }

  public void testUnifiedHighlighterPositionsOnlyField() throws IOException {
    assertEquals("ab <b>ac</b> bc", highlight(POSITIONS, spanWithin(POSITIONS)));
  }

  public void testMatchRegionRetrieverSameOnBothFields() throws IOException {
    String withOffsets = matchRegions(OFFSETS, spanWithin(OFFSETS));
    String positionsOnly = matchRegions(POSITIONS, spanWithin(POSITIONS));
    assertEquals("[[3,5)]", positionsOnly);
    assertEquals("same text, same query, field with offsets", positionsOnly, withOffsets);
  }

  /** Control: the intervals equivalent gives "ac" only, on both fields, with both highlighters. */
  public void testIntervalsContainedByControl() throws IOException {
    for (String field : new String[] {OFFSETS, POSITIONS}) {
      assertEquals(field, "ab <b>ac</b> bc", highlight(field, intervalsContainedBy(field)));
      assertEquals(field, "[[3,5)]", matchRegions(field, intervalsContainedBy(field)));
    }
  }
}
