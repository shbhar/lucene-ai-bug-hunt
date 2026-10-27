package verify.sa2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterMergePolicy;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.ParallelLeafReader;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.SlowCodecReaderWrapper;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.LuceneTestCase.SuppressSysoutChecks;
import org.apache.lucene.util.IOUtils;

/**
 * Supplementary, PROBABILISTIC check of the merge path used by TestDemoParallelLeafReader:
 * OneMerge.wrapForMerge returns SlowCodecReaderWrapper.wrap(new ParallelLeafReader(parallel,
 * segment)) with the parallel (derived) reader first. Both readers vector "body" (same schema, so
 * IndexWriter accepts the merge). Postings of the merged segment come from the first reader; with
 * "first reader wins" its term vectors must too. Under the #14373 code which reader's vector wins
 * depends on IdentityHashMap order of two fresh objects, so each attempt fails with roughly 1/2
 * probability; a correct implementation never fails.
 */
@SuppressSysoutChecks(bugUrl = "prints observations for the verification write-up")
public class TestWrapForMergeParallelTermVectors extends LuceneTestCase {

  private static final FieldType TV = new FieldType(TextField.TYPE_NOT_STORED);

  static {
    TV.setStoreTermVectors(true);
    TV.freeze();
  }

  private static Document doc(String value) {
    Document d = new Document();
    d.add(new Field("body", value, TV));
    return d;
  }

  public void testMergedSegmentStaysConsistent() throws Exception {
    final int attempts = 8;
    int broken = 0;
    for (int i = 0; i < attempts; i++) {
      try (Directory pdir = new ByteBuffersDirectory();
          Directory mdir = new ByteBuffersDirectory()) {
        try (IndexWriter pw = new IndexWriter(pdir, new IndexWriterConfig())) {
          pw.addDocument(doc("alpha"));
        }
        try (DirectoryReader parallel = DirectoryReader.open(pdir)) {
          final LeafReader pleaf = getOnlyLeafReader(parallel);
          final List<ParallelLeafReader> opened = new ArrayList<>();
          final AtomicBoolean returned = new AtomicBoolean();
          MergePolicy mp =
              new FilterMergePolicy(NoMergePolicy.INSTANCE) {
                @Override
                public MergeSpecification findForcedMerges(
                    SegmentInfos infos,
                    int maxSegmentCount,
                    Map<SegmentCommitInfo, Boolean> segmentsToMerge,
                    MergeContext mergeContext) {
                  if (returned.getAndSet(true)) {
                    return null; // merge exactly once, otherwise forceMerge never terminates
                  }
                  MergeSpecification spec = new MergeSpecification();
                  spec.add(
                      new OneMerge(new ArrayList<>(infos.asList())) {
                        @Override
                        public CodecReader wrapForMerge(CodecReader reader) throws IOException {
                          ParallelLeafReader plr = new ParallelLeafReader(false, pleaf, reader);
                          opened.add(plr);
                          return SlowCodecReaderWrapper.wrap(plr);
                        }

                        @Override
                        public void mergeFinished(boolean success, boolean segmentDropped)
                            throws IOException {
                          super.mergeFinished(success, segmentDropped);
                          IOUtils.close(opened);
                        }
                      });
                  return spec;
                }
              };
          try (IndexWriter w = new IndexWriter(mdir, new IndexWriterConfig()
                      .setMergePolicy(mp)
                      .setMergeScheduler(new SerialMergeScheduler()))) {
            w.addDocument(doc("beta"));
            w.commit();
            w.forceMerge(1);
            w.commit();
          }
          assertEquals("wrapForMerge must have been used", 1, opened.size());
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        CheckIndex.Status st;
        try (CheckIndex ci = new CheckIndex(mdir)) {
          ci.setInfoStream(new PrintStream(bos, true, StandardCharsets.UTF_8));
          ci.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS);
          st = ci.checkIndex();
        }
        String err = "";
        for (String line : bos.toString(StandardCharsets.UTF_8).split("\n")) {
          if (line.contains("test: term vectors") && line.contains("ERROR")) {
            err = line.trim();
          }
        }
        if (st.clean == false) {
          broken++;
        }
        System.out.println(
            "[wrapForMerge] attempt " + i + " CheckIndex clean=" + st.clean + " " + err);
      }
    }
    assertEquals("merged indexes (of " + attempts + ") that fail CheckIndex", 0, broken);
  }
}
