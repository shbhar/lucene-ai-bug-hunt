import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.classification.document.KNearestNeighborDocumentClassifier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Test;

public class TestKNearestNeighborDocumentClassifierReaderValuedField {

  // Expected: nature, by the text of the Reader-valued field; actual: food, the field is left out.
  @Test
  public void testReaderValuedTextField() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        for (String[] example : new String[][] {{"red apple", "food"}, {"blue sky", "nature"}}) {
          Document doc = new Document();
          doc.add(new TextField("text", example[0], Field.Store.NO));
          doc.add(new StringField("class", example[1], Field.Store.YES));
          writer.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        KNearestNeighborDocumentClassifier classifier =
            new KNearestNeighborDocumentClassifier(
                reader, null, null, 1, 1, 1, "class", Map.of("text", analyzer), "text");
        Document document = new Document();
        document.add(new TextField("text", new StringReader("blue sky")));
        assertEquals("nature", classifier.assignClass(document).assignedClass().utf8ToString());
      }
    }
  }
}
