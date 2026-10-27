import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.InfoStream;
import org.apache.lucene.util.Version;

/**
 * Independent, framework-free repro (public API only, default IndexWriterConfig apart from the
 * overlays knob). Holds the merge and the in-flight packet with InfoStream hooks (no Directory
 * wrapper, no blocking Query).
 *
 * <p>args: run <overlays|-1> <tie|lt> <v1|v2> <indexPath> | read <indexPath>
 *
 * <p>v1: packet P = DV update + delete-by-TERM, resolved by an NRT reopen on thread "refresher",
 * parked BEFORE its DV updates resolve; newer updates finish via tryUpdateDocValue.
 *
 * <p>v2: no flush/reopen/tryUpdate/query at all: a tiny live RAM buffer makes each
 * updateNumericDocValue freeze+resolve its own packet on the calling indexing thread; P runs on
 * thread "indexer-A" and is parked AFTER its DV updates resolved on the old segments; the newer
 * update(s) are resolved by the main indexing thread.
 */
public class CarryOverRace {

  /** One-shot park point. */
  static final class Gate {
    private final AtomicBoolean armed = new AtomicBoolean();
    private final CompletableFuture<String> reached = new CompletableFuture<>();
    private final CompletableFuture<Void> opened = new CompletableFuture<>();

    void arm() {
      armed.set(true);
    }

    void maybePark(String where) {
      if (armed.compareAndSet(true, false)) {
        reached.complete(Thread.currentThread().getName() + ": " + where);
        opened.join();
      }
    }

    String awaitReached() throws Exception {
      return reached.get(60, TimeUnit.SECONDS);
    }

    void open() {
      opened.complete(null);
    }
  }

  static final class Hooks extends InfoStream {
    final Gate merge = new Gate();
    final Gate packet = new Gate();
    volatile String packetThread = null;
    volatile String packetMessagePrefix = null;
    final List<String> trace = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void message(String component, String message) {
      if (component.equals("BD")
          && (message.startsWith("push new packet") || message.startsWith("finished packet"))) {
        trace.add("[" + Thread.currentThread().getName() + "] " + message);
      }
      if (component.equals("SM") && message.contains(" ms to merge ")) {
        merge.maybePark(message);
      }
      if (component.equals("BD")
          && packetMessagePrefix != null
          && message.startsWith(packetMessagePrefix)
          && Thread.currentThread().getName().equals(packetThread)) {
        packet.maybePark(message);
      }
    }

    @Override
    public boolean isEnabled(String component) {
      return component.equals("SM") || component.equals("BD");
    }

    @Override
    public void close() {}
  }

  static Document doc(int id) {
    Document d = new Document();
    d.add(new StringField("id", Integer.toString(id), Field.Store.YES));
    d.add(new NumericDocValuesField("v", 1000 + id));
    return d;
  }

  static Map<String, Long> values(DirectoryReader r) throws Exception {
    Map<String, Long> out = new TreeMap<>();
    for (LeafReaderContext ctx : r.leaves()) {
      LeafReader leaf = ctx.reader();
      NumericDocValues v = leaf.getNumericDocValues("v");
      Bits live = leaf.getLiveDocs();
      for (int d = 0; d < leaf.maxDoc(); d++) {
        if (live != null && live.get(d) == false) continue;
        String id = leaf.storedFields().document(d).get("id");
        out.put(id, v != null && v.advanceExact(d) ? v.longValue() : null);
      }
    }
    return out;
  }

  static int globalDocOf(DirectoryReader r, String id) throws Exception {
    for (LeafReaderContext ctx : r.leaves()) {
      LeafReader leaf = ctx.reader();
      for (int d = 0; d < leaf.maxDoc(); d++) {
        if (id.equals(leaf.storedFields().document(d).get("id"))) return ctx.docBase + d;
      }
    }
    throw new IllegalStateException("no doc " + id);
  }

  static Thread start(String name, AtomicReference<Throwable> failure, ThrowingRunnable body) {
    Thread t =
        new Thread(
            () -> {
              try {
                body.run();
              } catch (Throwable e) {
                failure.compareAndSet(null, e);
              }
            },
            name);
    t.start();
    return t;
  }

  interface ThrowingRunnable {
    void run() throws Exception;
  }

  static String overlays(IndexWriterConfig iwc, int n) {
    try {
      Method m = IndexWriterConfig.class.getMethod("setMaxDocValuesOverlays", int.class);
      Method g = IndexWriterConfig.class.getMethod("getMaxDocValuesOverlays");
      if (n >= 0) m.invoke(iwc, n);
      return "maxDocValuesOverlays=" + g.invoke(iwc) + (n < 0 ? " (default)" : "");
    } catch (NoSuchMethodException e) {
      return "maxDocValuesOverlays: API not present in this Lucene";
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static String fmt(Throwable t) {
    return t == null ? "none" : t.getClass().getName() + ": " + t.getMessage();
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Lucene " + Version.LATEST + ", java " + System.getProperty("java.version")
        + ", assertions " + (CarryOverRace.class.desiredAssertionStatus() ? "ON (-ea)" : "OFF (-da)"));
    if (args[0].equals("read")) {
      try (Directory dir = FSDirectory.open(Paths.get(args[1]));
          DirectoryReader r = DirectoryReader.open(dir)) {
        System.out.println("fresh-JVM DirectoryReader.open(dir): segments=" + r.leaves().size()
            + " values=" + values(r));
      }
      return;
    }
    int ov = Integer.parseInt(args[1]);
    boolean lt = args[2].equals("lt");
    boolean v2 = args[3].equals("v2");
    Path path = Paths.get(args[4]);

    Hooks hooks = new Hooks();
    IndexWriterConfig iwc = new IndexWriterConfig().setInfoStream(hooks);
    System.out.println(overlays(iwc, ov) + ", scenario=" + args[2] + ", variant=" + args[3]
        + ", mergePolicy=" + iwc.getMergePolicy().getClass().getSimpleName()
        + ", scheduler=" + iwc.getMergeScheduler().getClass().getSimpleName());

    Directory dir = FSDirectory.open(path);
    IndexWriter w = new IndexWriter(dir, iwc);
    // _0 = {0, 1}, _1 = {2, 3}; v = 1000 + id
    w.addDocument(doc(0));
    w.addDocument(doc(1));
    w.commit();
    w.addDocument(doc(2));
    w.addDocument(doc(3));
    w.commit();

    AtomicReference<Throwable> mergeFailure = new AtomicReference<>();
    hooks.merge.arm();
    Thread merger = start("merger", mergeFailure, () -> w.forceMerge(1));
    System.out.println("merge parked at: " + hooks.merge.awaitReached());

    // OLD update: doc 0 := 111, committed (so written to _0's files) while _0 is merging
    w.updateNumericDocValue(new Term("id", "0"), "v", 111L);
    w.commit();
    DirectoryReader nrt = DirectoryReader.open(w); // only used by v1's tryUpdateDocValue

    AtomicReference<Throwable> packetFailure = new AtomicReference<>();
    Thread holder;
    Map<String, Long> expected = new TreeMap<>(Map.of("0", 222L, "1", 333L, "2", 1002L, "3", 1003L));
    if (v2 == false) {
      // NEW update: doc 0 := 222, frozen with a delete-by-term into one packet P that the
      // refresher thread resolves and that parks before its DV updates are applied
      w.updateNumericDocValue(new Term("id", "0"), "v", 222L);
      w.deleteDocuments(new Term("id", "no-such-id"));
      hooks.packetThread = "refresher";
      hooks.packetMessagePrefix = "applyTermDeletes took";
      hooks.packet.arm();
      holder = start("refresher", packetFailure, () -> DirectoryReader.open(w).close());
      System.out.println("packet parked at: " + hooks.packet.awaitReached());
      if (lt) {
        w.tryUpdateDocValue(nrt, globalDocOf(nrt, "2"), new NumericDocValuesField("v", 444L));
        expected.put("2", 444L);
      }
      w.tryUpdateDocValue(nrt, globalDocOf(nrt, "1"), new NumericDocValuesField("v", 333L));
    } else {
      double defaultRam = w.getConfig().getRAMBufferSizeMB();
      w.getConfig().setRAMBufferSizeMB(1e-6); // every buffered update now exceeds the budget
      hooks.packetThread = "indexer-A";
      hooks.packetMessagePrefix = "applyDocValuesUpdates";
      hooks.packet.arm();
      holder =
          start(
              "indexer-A",
              packetFailure,
              () -> w.updateNumericDocValue(new Term("id", "0"), "v", 222L));
      System.out.println("packet parked at: " + hooks.packet.awaitReached());
      if (lt) {
        w.updateNumericDocValue(new Term("id", "2"), "v", 444L);
        expected.put("2", 444L);
      }
      w.updateNumericDocValue(new Term("id", "1"), "v", 333L);
      w.getConfig().setRAMBufferSizeMB(defaultRam);
    }
    nrt.close();

    // let the merge commit (carry-over runs now), then let P finish (it re-resolves on the merged
    // segment)
    hooks.merge.open();
    merger.join();
    hooks.packet.open();
    holder.join();

    System.out.println("packet trace:");
    synchronized (hooks.trace) {
      for (String s : hooks.trace) System.out.println("   " + s);
    }
    System.out.println("merge thread failure : " + fmt(mergeFailure.get()));
    System.out.println("packet thread failure: " + fmt(packetFailure.get()));
    System.out.println("writer tragic exc    : " + fmt(w.getTragicException()) + ", isOpen=" + w.isOpen());
    System.out.println("expected             : " + expected);
    if (w.isOpen()) {
      try (DirectoryReader r = DirectoryReader.open(w)) {
        System.out.println("NRT reader           : segments=" + r.leaves().size() + " values=" + values(r));
      }
      long seq = w.commit();
      System.out.println("commit() ok, seqNo=" + seq);
      w.close();
    } else {
      System.out.println("writer is closed; on-disk state = last successful commit");
    }
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      Map<String, Long> got = values(r);
      System.out.println("DirectoryReader.open(dir): segments=" + r.leaves().size() + " values=" + got);
      System.out.println("RESULT: " + (got.equals(expected) ? "OK (newest update kept)" : "MISMATCH doc0=" + got.get("0") + " (expected " + expected.get("0") + ")"));
    }
    dir.close();
  }
}
