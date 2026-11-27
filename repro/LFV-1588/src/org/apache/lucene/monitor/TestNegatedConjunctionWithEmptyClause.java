package org.apache.lucene.monitor;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNegatedConjunctionWithEmptyClause extends LuceneTestCase {

  public void testEmptyTermInSetQueryInNegatedConjunction() throws IOException {
    Document doc = new Document();
    doc.add(new TextField("tag", "red", Field.Store.NO));
    Query negated =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("tag", "red")), Occur.MUST)
            .add(new TermInSetQuery("id", List.of()), Occur.MUST)
            .build();
    Query query =
        new BooleanQuery.Builder()
            .add(MatchAllDocsQuery.INSTANCE, Occur.MUST)
            .add(negated, Occur.MUST_NOT)
            .build();
    Analyzer analyzer = new StandardAnalyzer();
    assertEquals(1, MemoryIndex.fromDocument(doc, analyzer).createSearcher().count(query));

    Presearcher presearcher =
        new TermFilteredPresearcher(TermWeightor.DEFAULT, List.of(), Set.of(), 1);
    try (Monitor monitor = new Monitor(analyzer, presearcher)) {
      monitor.register(new MonitorQuery("q", query));
      // expected 1: *:* -(+tag:red +id:()) matches the document (the count above); actual 0
      assertEquals(1, monitor.match(doc, QueryMatch.SIMPLE_MATCHER).getMatchCount());
    }
  }
}
