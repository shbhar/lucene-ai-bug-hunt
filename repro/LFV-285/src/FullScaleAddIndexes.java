import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterCodecReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;

/**
 * Full-scale check (no test hooks): two real indexes of 1,073,741,760 empty documents each, so the
 * inputs' maxDoc() sum is IndexWriter.MAX_DOCS + 1. Each leaf is filtered down to 2 live documents
 * (FilterCodecReader with custom live docs, as index splitters do). addIndexes of all filtered
 * leaves in one call adds only 4 * #leaves documents. Exit 0 if it succeeds, 1 if it throws.
 */
public class FullScaleAddIndexes {
  static final long PER_INDEX = 1_073_741_760L;

  static void build(Path p, int threads) throws Exception {
    long t0 = System.nanoTime();
    try (Directory dir = FSDirectory.open(p);
        IndexWriter w =
            new IndexWriter(
                dir,
                new IndexWriterConfig()
                    .setMergePolicy(NoMergePolicy.INSTANCE)
                    .setRAMBufferSizeMB(1024)
                    .setUseCompoundFile(false))) {
      AtomicLong next = new AtomicLong();
      List<Thread> ts = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        Thread th =
            new Thread(
                () -> {
                  Document doc = new Document();
                  try {
                    long i;
                    while ((i = next.getAndIncrement()) < PER_INDEX) {
                      w.addDocument(doc);
                      if (i % 100_000_000L == 0) {
                        System.out.println(p.getFileName() + " " + i + " docs " + (System.nanoTime() - t0) / 1_000_000_000 + "s");
                      }
                    }
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                });
        th.start();
        ts.add(th);
      }
      for (Thread th : ts) {
        th.join();
      }
      w.commit();
      System.out.println(p.getFileName() + " built: maxDoc=" + w.getDocStats().maxDoc + " in " + (System.nanoTime() - t0) / 1_000_000_000 + "s");
    }
  }

  static List<CodecReader> filtered(DirectoryReader r, int keepFrom) {
    List<CodecReader> out = new ArrayList<>();
    for (LeafReaderContext ctx : r.leaves()) {
      CodecReader in = (CodecReader) ctx.reader();
      final int maxDoc = in.maxDoc();
      final int lo = Math.min(keepFrom, maxDoc), hi = Math.min(keepFrom + 2, maxDoc);
      out.add(
          new FilterCodecReader(in) {
            @Override
            public Bits getLiveDocs() {
              return new Bits() {
                @Override
                public boolean get(int index) {
                  return index >= lo && index < hi;
                }

                @Override
                public int length() {
                  return maxDoc;
                }
              };
            }

            @Override
            public int numDocs() {
              return hi - lo;
            }

            @Override
            public CacheHelper getCoreCacheHelper() {
              return null;
            }

            @Override
            public CacheHelper getReaderCacheHelper() {
              return null;
            }
          });
    }
    return out;
  }

  static long sumMaxDoc(List<CodecReader> rs) {
    return rs.stream().mapToLong(CodecReader::maxDoc).sum();
  }

  static long sumNumDocs(List<CodecReader> rs) {
    return rs.stream().mapToLong(CodecReader::numDocs).sum();
  }

  public static void main(String[] args) throws Exception {
    Path base = Path.of(args[0]);
    int threads = Integer.parseInt(args[1]);
    System.out.println("IndexWriter.MAX_DOCS=" + IndexWriter.MAX_DOCS);
    Path a = base.resolve("a"), b = base.resolve("b");
    build(a, threads);
    build(b, threads);
    int exit;
    try (Directory da = FSDirectory.open(a);
        Directory db = FSDirectory.open(b);
        DirectoryReader ra = DirectoryReader.open(da);
        DirectoryReader rb = DirectoryReader.open(db)) {
      List<CodecReader> fa = filtered(ra, 0), fb = filtered(rb, 0);
      List<CodecReader> all = new ArrayList<>(fa);
      all.addAll(fb);
      System.out.println("leaves a=" + fa.size() + " b=" + fb.size() + " sum maxDoc=" + sumMaxDoc(all) + " (MAX_DOCS+" + (sumMaxDoc(all) - IndexWriter.MAX_DOCS) + ") sum numDocs=" + sumNumDocs(all));
      // control: each index's filtered leaves in its own call
      try (Directory t = FSDirectory.open(base.resolve("control"));
          IndexWriter w = new IndexWriter(t, new IndexWriterConfig())) {
        w.addIndexes(fa.toArray(CodecReader[]::new));
        w.addIndexes(fb.toArray(CodecReader[]::new));
        w.commit();
        System.out.println("CONTROL two calls: OK, numDocs=" + w.getDocStats().numDocs);
      }
      // the call under test: all filtered leaves at once, default IndexWriterConfig
      try (Directory t = FSDirectory.open(base.resolve("onecall"));
          IndexWriter w = new IndexWriter(t, new IndexWriterConfig())) {
        w.addIndexes(all.toArray(CodecReader[]::new));
        w.commit();
        System.out.println("ONE CALL: OK, numDocs=" + w.getDocStats().numDocs);
        exit = 0;
      } catch (IllegalArgumentException e) {
        System.out.println("ONE CALL: FAILED " + e);
        Throwable c = e;
        for (StackTraceElement el : c.getStackTrace()) {
          if (el.getClassName().startsWith("org.apache.lucene")) System.out.println("    at " + el);
        }
        exit = 1;
      }
    }
    System.exit(exit);
  }
}
