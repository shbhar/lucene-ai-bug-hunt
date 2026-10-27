// Version-portable main() variant of TestIndexSplitterDocValuesUpdates#testCopySurvivesIndexWriterOpen
// for releases whose test framework lives in another package (IndexSplitter in org.apache.lucene.misc.index).
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.misc.index.IndexSplitter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class IndexSplitterUpdatesMain {
  static String prices(Path path) throws Exception {
    try (Directory dir = FSDirectory.open(path); DirectoryReader reader = DirectoryReader.open(dir)) {
      LeafReader leaf = reader.leaves().get(0).reader();
      NumericDocValues dv = leaf.getNumericDocValues("price");
      StringBuilder sb = new StringBuilder();
      for (int doc = 0; doc < leaf.maxDoc(); doc++) {
        sb.append(dv != null && dv.advanceExact(doc) ? Long.toString(dv.longValue()) : "none").append(' ');
      }
      return sb.toString().trim();
    }
  }

  public static void main(String[] args) throws Exception {
    Path tmp = Files.createTempDirectory("indexsplitter-updates");
    Path src = tmp.resolve("src");
    Path dest = tmp.resolve("dest");
    try (Directory dir = FSDirectory.open(src);
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      for (int i = 0; i < 4; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.YES));
        doc.add(new NumericDocValuesField("price", 10 + i));
        w.addDocument(doc);
      }
      w.commit();
      w.updateNumericDocValue(new Term("id", "1"), "price", 999);
      w.commit();
    }
    String segment = new IndexSplitter(src).infos.info(0).info.name;
    new IndexSplitter(src).split(dest, new String[] {segment});
    String srcPrices = prices(src);
    String copyPrices = prices(dest);
    System.out.println("source=" + srcPrices + " copy=" + copyPrices);
    try (Directory dir = FSDirectory.open(dest)) {
      new IndexWriter(dir, new IndexWriterConfig()).close();
    }
    String afterWriter;
    try {
      afterWriter = prices(dest);
    } catch (java.io.IOException e) {
      afterWriter = e.toString();
    }
    System.out.println("copy after IndexWriter open/close=" + afterWriter);
    if (!srcPrices.equals(copyPrices) || !srcPrices.equals(afterWriter)) {
      System.out.println("FAIL: copied segment differs from the source");
      System.exit(1);
    }
    System.out.println("PASS");
  }
}
