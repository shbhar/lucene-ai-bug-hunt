// main() variant of TestPerFieldPostingsStaleAttributes for 7.x releases (the JUnit runner cannot run 7.6's junit
// 4.10 test framework). Exits 1 when the committed index cannot be opened (the bug), 0 when it opens.
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.lucene50.Lucene50PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.codecs.simpletext.SimpleTextCodec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

public class StaleAttributes7x {
  public static void main(String[] args) throws Exception {
    Directory dir = new RAMDirectory();
    final PostingsFormat first = new Lucene50PostingsFormat();
    final PostingsFormat second = new Lucene50PostingsFormat();
    final Codec base = Codec.getDefault();
    Codec perFieldCodec =
        new FilterCodec(base.getName(), base) {
          final PostingsFormat perField =
              new PerFieldPostingsFormat() {
                @Override
                public PostingsFormat getPostingsFormatForField(String field) {
                  return field.equals("g") ? second : first;
                }
              };

          @Override
          public PostingsFormat postingsFormat() {
            return perField;
          }
        };
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(perFieldCodec))) {
      Document doc = new Document();
      doc.add(new StringField("id", "0", Field.Store.YES));
      doc.add(new StringField("f", "x", Field.Store.NO));
      w.addDocument(doc);
      doc = new Document();
      doc.add(new StringField("id", "1", Field.Store.YES));
      doc.add(new StringField("g", "y", Field.Store.NO));
      w.addDocument(doc);
    }
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(new SimpleTextCodec()))) {
      w.deleteDocuments(new Term("id", "1"));
      w.forceMerge(1);
    }
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(base))) {
      Document doc = new Document();
      doc.add(new StringField("id", "2", Field.Store.YES));
      doc.add(new StringField("f", "z", Field.Store.NO));
      w.addDocument(doc);
      w.forceMerge(1);
    }
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      System.out.println("OK: index opens, numDocs=" + r.numDocs());
    } catch (Exception e) {
      System.out.println("BUG: committed index cannot be opened: " + e);
      Throwable c = e.getCause();
      if (c != null) System.out.println("  caused by: " + c);
      System.exit(1);
    }
  }
}
