import java.io.InputStream;
import java.nio.file.Path;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestInt7FixtureCodecName extends LuceneTestCase {
  // expected: the checked-in fixture names a codec of Lucene's jars ("Lucene..."); actual: "Asserting"
  public void testFixtureNamesADefaultCodec() throws Exception {
    Path dir = createTempDir();
    try (InputStream in = getClass().getResourceAsStream("/org/apache/lucene/backward_index/int7_hnsw.10.4.0.zip")) {
      TestUtil.unzip(in, dir);
    }
    try (Directory d = FSDirectory.open(dir)) {
      String codec = SegmentInfos.readLatestCommit(d).info(0).info.getCodec().getName();
      assertTrue(codec, codec.startsWith("Lucene"));
    }
  }
}
