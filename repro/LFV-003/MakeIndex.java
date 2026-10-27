import java.nio.file.Paths;
import java.util.Arrays;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;

/**
 * Writes one segment with a dense, fixed-length BinaryDocValuesField "f": numDocs docs, each value
 * `length` bytes. Every byte of doc d is ('A' + d), except the last byte, which is ('a' + d).
 * Public API only, default codec, default merge policy and scheduler. Compiles against 10.5.2.
 *
 * <p>Usage: java -cp lucene-core.jar:. MakeIndex DIR NUMDOCS LENGTH
 */
public class MakeIndex {
  public static void main(String[] args) throws Exception {
    String path = args[0];
    int numDocs = Integer.parseInt(args[1]);
    int length = Integer.parseInt(args[2]);
    System.out.println("lucene=" + Version.LATEST + " numDocs=" + numDocs + " length=" + length);
    byte[] bytes = new byte[length];
    try (Directory dir = FSDirectory.open(Paths.get(path));
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int d = 0; d < numDocs; d++) {
        Arrays.fill(bytes, (byte) ('A' + d));
        bytes[length - 1] = (byte) ('a' + d);
        Document doc = new Document();
        doc.add(new BinaryDocValuesField("f", new BytesRef(bytes)));
        w.addDocument(doc);
        w.flush();
      }
      w.forceMerge(1);
      w.commit();
    }
    try (Directory dir = FSDirectory.open(Paths.get(path));
        DirectoryReader r = DirectoryReader.open(dir)) {
      LeafReader leaf = r.leaves().get(0).reader();
      System.out.println(
          "leaves="
              + r.leaves().size()
              + " numDocs="
              + r.numDocs()
              + " codec="
              + ((SegmentReader) leaf).getSegmentInfo().info.getCodec().getName()
              + " dvFormat="
              + leaf.getFieldInfos().fieldInfo("f").getAttribute("PerFieldDocValuesFormat.format"));
    }
  }
}
