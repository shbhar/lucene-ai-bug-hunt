import java.io.IOException;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.lucene95.OrdToDocDISIReaderConfiguration;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

/**
 * Independent re-implementation of "drop the full-precision vectors" (what the javadoc of
 * OffHeapScalarQuantizedFloatVectorValues:48-51 describes): the Lucene99 flat raw-vector data file
 * (.vec) is replaced by header+footer only, and its metadata (.vemf) is rewritten so that the single
 * field reports vectorDataLength=0, size=0, empty ordToDoc. The quantized files (.veq/.vemq) and the
 * HNSW graph (.vex/.vem) are untouched. Assumes exactly one vector field per segment.
 */
final class DropRaw {
  private DropRaw() {}

  static int drop(Directory dir) throws IOException {
    int touched = 0;
    for (String f : dir.listAll()) {
      if (f.endsWith(".vec")) {
        emptyData(dir, f);
        touched++;
      } else if (f.endsWith(".vemf")) {
        emptyMeta(dir, f);
        touched++;
      }
    }
    return touched;
  }

  private static void emptyData(Directory dir, String f) throws IOException {
    byte[] header;
    try (IndexInput in = dir.openInput(f, IOContext.DEFAULT)) {
      header = CodecUtil.readIndexHeader(in);
    }
    dir.deleteFile(f);
    try (IndexOutput out = dir.createOutput(f, IOContext.DEFAULT)) {
      out.writeBytes(header, header.length);
      CodecUtil.writeFooter(out);
    }
  }

  // Lucene99FlatVectorsReader.FieldEntry.create order: int field, int encoding, int similarity,
  // vlong dataOffset, vlong dataLength, vint dim, int size, OrdToDoc stored meta; -1 terminates.
  private static void emptyMeta(Directory dir, String f) throws IOException {
    byte[] header;
    int field, enc, sim, dim;
    long off;
    try (IndexInput in = dir.openInput(f, IOContext.DEFAULT)) {
      header = CodecUtil.readIndexHeader(in);
      field = in.readInt();
      enc = in.readInt();
      sim = in.readInt();
      off = in.readVLong();
      in.readVLong();
      dim = in.readVInt();
    }
    dir.deleteFile(f);
    try (IndexOutput out = dir.createOutput(f, IOContext.DEFAULT)) {
      out.writeBytes(header, header.length);
      out.writeInt(field);
      out.writeInt(enc);
      out.writeInt(sim);
      out.writeVLong(off);
      out.writeVLong(0L);
      out.writeVInt(dim);
      out.writeInt(0); // size
      OrdToDocDISIReaderConfiguration.writeStoredMeta(16, out, null, 0, 0, null);
      out.writeInt(-1);
      CodecUtil.writeFooter(out);
    }
  }
}
