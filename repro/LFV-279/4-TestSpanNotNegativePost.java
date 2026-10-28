package org.apache.lucene.queries.spans;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SpanNotQuery(include, exclude, pre, post): "negative values for pre and/or post allow a certain amount of overlap to
 * occur". Once the exclude spans are exhausted in a document, accept() evaluates
 * excludeSpans.startPosition() - post with startPosition() == NO_MORE_POSITIONS (Integer.MAX_VALUE); for post < 0
 * this overflows to a negative number, so every later include span of that document is rejected.
 */
public class TestSpanNotNegativePost extends LuceneTestCase {

  static SpanTermQuery t(String s) {
    return new SpanTermQuery(new Term("f", s));
  }

  static List<String> spans(IndexSearcher searcher, SpanQuery q) throws Exception {
    List<String> out = new ArrayList<>();
    SpanWeight w = ((SpanQuery) searcher.rewrite(q)).createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f);
    Spans spans = w.getSpans(searcher.getIndexReader().leaves().get(0), SpanWeight.Postings.POSITIONS);
    if (spans == null || spans.nextDoc() != 0) return out;
    while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
      out.add("[" + spans.startPosition() + "," + spans.endPosition() + ")");
    }
    return out;
  }

  public void testIncludeSpansAfterLastExclude() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
        Document doc = new Document();
        doc.add(new TextField("f", "b a a c", Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        // "b" at 0 is before both "a"s; neither "a" overlaps it, with or without the allowed overlap
        assertEquals(List.of("[1,2)", "[2,3)"), spans(searcher, new SpanNotQuery(t("a"), t("b"), 0, 0)));
        assertEquals(
            "post = -1", List.of("[1,2)", "[2,3)"), spans(searcher, new SpanNotQuery(t("a"), t("b"), 0, -1)));

        // the dropped span changes matching once the SpanNotQuery is nested: "a c" at 2..3
        SpanQuery near0 =
            new SpanNearQuery(new SpanQuery[] {new SpanNotQuery(t("a"), t("b"), 0, 0), t("c")}, 0, true);
        SpanQuery nearNeg =
            new SpanNearQuery(new SpanQuery[] {new SpanNotQuery(t("a"), t("b"), 0, -1), t("c")}, 0, true);
        assertEquals(1, searcher.count(near0));
        assertEquals(nearNeg.toString(), 1, searcher.count(nearNeg));
      }
    }
  }
}
