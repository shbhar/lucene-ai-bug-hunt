package org.apache.lucene.analysis.th;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.KeywordRepeatFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestThaiRepeatFilterStackedTokens extends LuceneTestCase {

  public void testMaiyamokWordWithStackedToken() throws Exception {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new WhitespaceTokenizer();
            return new TokenStreamComponents(
                tokenizer, new ThaiRepeatFilter(new KeywordRepeatFilter(tokenizer)));
          }
        };
    try (IndexWriter writer =
        new IndexWriter(new ByteBuffersDirectory(), new IndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new TextField("body", "เร็วๆ", Field.Store.NO));
      // expected: document indexed; actual: IAE "offsets must not go backwards" (stacked copy emitted after the repeat)
      writer.addDocument(doc);
    }
  }
}
