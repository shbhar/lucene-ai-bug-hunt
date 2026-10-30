import java.nio.file.Path;
import java.util.Random;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.FSDirectory;

/** Writes the test index with the release on the classpath (run with Lucene 9.8.0: Lucene95 HNSW format). */
public class MakeOldHnswIndex {
  public static void main(String[] args) throws Exception {
    Random r = new Random(42);
    try (FSDirectory dir = FSDirectory.open(Path.of(args[0]));
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 5000; i++) {
        Document d = new Document();
        float[] v = new float[16];
        for (int j = 0; j < 16; j++) {
          v[j] = r.nextFloat();
        }
        d.add(new KnnFloatVectorField("v", v, VectorSimilarityFunction.EUCLIDEAN));
        d.add(new StringField("f", r.nextInt(100) < 15 ? "yes" : "no", Field.Store.NO));
        w.addDocument(d);
      }
      w.forceMerge(1);
      System.out.println("codec=" + w.getConfig().getCodec());
    }
  }
}
