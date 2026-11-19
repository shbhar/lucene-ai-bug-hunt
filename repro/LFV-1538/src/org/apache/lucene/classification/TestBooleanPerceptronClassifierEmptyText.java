package org.apache.lucene.classification;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBooleanPerceptronClassifierEmptyText extends LuceneTestCase {

  // expected: a classifier; actual: IOException "term vectors must be stored for field text"
  public void testTrainingDocumentWithEmptyText() throws Exception {
    FieldType textWithTermVectors = new FieldType(TextField.TYPE_STORED);
    textWithTermVectors.setStoreTermVectors(true);
    try (Analyzer analyzer = new StandardAnalyzer();
        Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        for (String[] textAndClass : new String[][] {{"fox", "false"}, {"", "true"}}) {
          Document doc = new Document();
          doc.add(new Field("text", textAndClass[0], textWithTermVectors));
          doc.add(new StringField("class", textAndClass[1], Field.Store.YES));
          writer.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        new BooleanPerceptronClassifier(reader, analyzer, null, 1, null, "class", "text");
      }
    }
  }
}
