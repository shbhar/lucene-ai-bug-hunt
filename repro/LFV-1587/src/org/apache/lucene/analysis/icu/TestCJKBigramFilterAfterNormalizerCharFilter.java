package org.apache.lucene.analysis.icu;

import java.io.Reader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.cjk.CJKBigramFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCJKBigramFilterAfterNormalizerCharFilter extends LuceneTestCase {

  public void testIdeographAfterDigitOfOneSourceCharacter() throws Exception {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected Reader initReader(String fieldName, Reader reader) {
            return new ICUNormalizer2CharFilter(reader);
          }

          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new StandardTokenizer();
            return new TokenStreamComponents(tokenizer, new CJKBigramFilter(tokenizer));
          }
        };
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new TextField("body", "月㏠", Field.Store.NO));
      writer.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        // expected: 1, as for the text 月1日 (the char filter turns ㏠ into "1日"); actual: 0
        assertEquals(1, reader.docFreq(new Term("body", "日")));
      }
    }
  }
}
