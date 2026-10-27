import java.nio.file.Paths;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterBinaryDocValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;

/**
 * Opens the index written by MakeIndex and makes ONE bulk BinaryDocValues#binaryValues call per
 * doc-id list given on the command line (e.g. "0,2" "0,1,2"). Each returned value is compared with
 * the per-value API (advanceExact + binaryValue) on a second iterator. With the first argument
 * "default", the iterator is wrapped in a FilterBinaryDocValues, which does not override
 * binaryValues, so the documented default implementation runs instead (control).
 *
 * <p>Usage: java -cp lucene-core.jar:. BulkCall [default] DIR DOCS...
 */
public class BulkCall {
  public static void main(String[] args) throws Exception {
    int a = 0;
    boolean useDefault = args[0].equals("default");
    if (useDefault) a++;
    String path = args[a++];
    boolean ea = false;
    assert ea = true;
    System.out.println(
        "lucene=" + Version.LATEST + " assertions=" + ea + " mode=" + (useDefault ? "default-impl" : "codec-bulk"));
    try (Directory dir = FSDirectory.open(Paths.get(path));
        DirectoryReader r = DirectoryReader.open(dir)) {
      LeafReader leaf = r.leaves().get(0).reader();
      for (; a < args.length; a++) {
        String[] parts = args[a].split(",");
        int[] docs = new int[parts.length];
        for (int i = 0; i < docs.length; i++) docs[i] = Integer.parseInt(parts[i]);
        BinaryDocValues bulk = leaf.getBinaryDocValues("f");
        if (useDefault) bulk = new FilterBinaryDocValues(bulk) {};
        BytesRef[] values = new BytesRef[docs.length];
        long requested = 0;
        BinaryDocValues single = leaf.getBinaryDocValues("f");
        for (int d : docs) {
          single.advanceExact(d);
          requested += single.binaryValue().length;
        }
        String head = "binaryValues(" + docs.length + ", {" + args[a] + "}) requested=" + requested + " B";
        try {
          bulk.binaryValues(docs.length, docs, values);
        } catch (Throwable t) {
          StackTraceElement top = t.getStackTrace().length > 0 ? t.getStackTrace()[0] : null;
          System.out.println(head + " -> THREW " + t + (top == null ? "" : "  at " + top));
          continue;
        }
        System.out.println(head + " -> returned normally");
        BinaryDocValues check = leaf.getBinaryDocValues("f");
        int wrong = 0;
        for (int i = 0; i < docs.length; i++) {
          BytesRef v = values[i];
          check.advanceExact(docs[i]);
          BytesRef expected = check.binaryValue();
          String valid;
          try {
            v.isValid();
            valid = "true";
          } catch (Throwable t) {
            valid = "false (" + t.getClass().getSimpleName() + ")";
          }
          String same;
          try {
            same = String.valueOf(expected.bytesEquals(v));
          } catch (Throwable t) {
            same = "n/a (" + t.getClass().getSimpleName() + ")";
          }
          String last;
          try {
            last = String.valueOf((char) v.bytes[v.offset + v.length - 1]);
          } catch (Throwable t) {
            last = t.getClass().getSimpleName();
          }
          if (!"true".equals(same)) wrong++;
          if (docs.length <= 8 || !"true".equals(same) && (i < 3 || i >= docs.length - 3)) {
            System.out.println(
                "  doc "
                    + docs[i]
                    + ": offset="
                    + v.offset
                    + " length="
                    + v.length
                    + " bytes.length="
                    + v.bytes.length
                    + " isValid="
                    + valid
                    + " equalsPerValue="
                    + same
                    + " lastByte="
                    + last
                    + " (expected "
                    + (char) expected.bytes[expected.offset + expected.length - 1]
                    + ")");
          }
        }
        System.out.println("  values differing from the per-value API: " + wrong + " of " + docs.length);
      }
    }
  }
}
