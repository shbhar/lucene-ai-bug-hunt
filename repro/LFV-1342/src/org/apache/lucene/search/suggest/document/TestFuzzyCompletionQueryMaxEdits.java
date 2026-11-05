package org.apache.lucene.search.suggest.document;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.automaton.Operations;

public class TestFuzzyCompletionQueryMaxEdits extends LuceneTestCase {

  // Expected: maxEdits=3 is rejected with IllegalArgumentException (like FuzzySuggester/FuzzyQuery); actual: suggest() throws NullPointerException.
  public void testMaxEditsAboveSupportedDistance() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    IndexWriterConfig iwc = newIndexWriterConfig(analyzer);
    iwc.setCodec(TestUtil.alwaysPostingsFormat(new Completion104PostingsFormat()));
    try (Directory dir = newDirectory();
        IndexWriter iw = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      doc.add(new SuggestField("suggest", "lucene", 1));
      iw.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(iw)) {
        FuzzyCompletionQuery query;
        try {
          query =
              new FuzzyCompletionQuery(
                  analyzer, new Term("suggest", "lucene"), null, 3, true, 1, 3, false,
                  Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
        } catch (IllegalArgumentException expected) {
          return;
        }
        new SuggestIndexSearcher(reader).suggest(query, 1, false);
      }
    }
  }
}
