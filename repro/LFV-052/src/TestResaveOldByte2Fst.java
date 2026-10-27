import java.util.HexFormat;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRefBuilder;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.PositiveIntOutputs;
import org.apache.lucene.util.fst.Util;

/**
 * Same root cause, stronger consequence: FST.FSTMetadata.save always writes VERSION_CURRENT (9).
 * An FST loaded from a version-7 file (written by the real lucene-core-8.11.4.jar, bytes embedded
 * below; BYTE2 input type, PositiveIntOutputs) is read correctly by main because readLabel
 * byte-swaps BYTE2 labels when version < 8 (FST.java:553). Saving that loaded FST copies the
 * big-endian label bytes verbatim but labels the file as version 9, so the reloaded FST decodes
 * every BYTE2 label with the wrong endianness: all lookups miss, no exception.
 */
public class TestResaveOldByte2Fst extends LuceneTestCase {

  // Produced by src8/Write8.java with lucene-core 8.11.4 (FST VERSION_CURRENT = 7).
  static final byte[] META = HexFormat.of().parseHex("3fd76c17034653540000000700013f40");
  static final byte[] DATA =
      HexFormat.of()
          .parseHex(
              "000264001f0163001d62000d74000f61000667000f6f0006019e8a1f2c67071e08e565121e07fc0019"
                  + "1706640010110563001005046200190b01610010050620");
  static final String[] KEYS = {"ab", "ac", "ad", "b", "cat", "dog", "ü", "日本",
    "日本語"};

  static FST<Long> load(byte[] meta, byte[] data) throws Exception {
    FST.FSTMetadata<Long> m =
        FST.readMetadata(new ByteArrayDataInput(meta), PositiveIntOutputs.getSingleton());
    return new FST<>(m, new ByteArrayDataInput(data));
  }

  static void assertAllKeys(String what, FST<Long> fst) throws Exception {
    IntsRefBuilder s = new IntsRefBuilder();
    for (int i = 0; i < KEYS.length; i++) {
      assertEquals(what + " key=" + KEYS[i], Long.valueOf(i + 1), Util.get(fst, Util.toUTF16(KEYS[i], s)));
    }
  }

  /** Control: main reads the 8.11.4 file correctly. PASSES. */
  public void testLoadVersion7Byte2Fst() throws Exception {
    FST<Long> f = load(META, DATA);
    assertEquals(7, f.getMetadata().getVersion());
    assertAllKeys("loaded from 8.11.4 file", f);
  }

  /** Bug: save + reload of the same FST loses the version and corrupts all BYTE2 labels. */
  public void testResaveKeepsLookups() throws Exception {
    FST<Long> f = load(META, DATA);
    ByteBuffersDataOutput meta = new ByteBuffersDataOutput();
    ByteBuffersDataOutput data = new ByteBuffersDataOutput();
    f.save(meta, data);
    FST<Long> g = load(meta.toArrayCopy(), data.toArrayCopy());
    System.out.println(
        "resaved: header/getVersion=" + g.getMetadata().getVersion() + ", Util.get(\"ab\")="
            + Util.get(g, Util.toUTF16("ab", new IntsRefBuilder())));
    assertAllKeys("after save+reload", g);
  }

  /** Bug, metadata view: the version the FST was loaded with is not preserved by save. */
  public void testResaveKeepsVersion() throws Exception {
    FST<Long> f = load(META, DATA);
    ByteBuffersDataOutput meta = new ByteBuffersDataOutput();
    f.save(meta, new ByteBuffersDataOutput());
    FST.FSTMetadata<Long> m =
        FST.readMetadata(
            new ByteArrayDataInput(meta.toArrayCopy()), PositiveIntOutputs.getSingleton());
    assertEquals(7, m.getVersion());
  }
}
