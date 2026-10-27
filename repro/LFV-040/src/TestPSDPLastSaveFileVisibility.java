import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.KeepOnlyLastCommitDeletionPolicy;
import org.apache.lucene.index.PersistentSnapshotDeletionPolicy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Behavioural probe for candidate static-analysis-tools #8.
 *
 * <p>A reader thread polls {@code psdp.getLastSaveFile()} (no lock, no other synchronization) in a
 * loop, waiting for it to become non-null. After the loop has run long enough to be OSR-compiled
 * by C2, the main thread calls {@code psdp.snapshot()}, which (under the policy's monitor) writes
 * snapshots_0 and bumps {@code nextWriteGen} from 0 to 1. The JMM (JLS 17.4) gives the reader no
 * happens-before edge with that write, so the JIT may hoist the field load out of the loop and the
 * reader may never observe the update.
 *
 * <p>Expected (if the getter were properly synchronized or the field volatile): the reader
 * observes the update promptly. Controls use the same loop but read under {@code
 * synchronized(psdp)} or through the synchronized {@code getSnapshotCount()}.
 *
 * <p>The spinning reader is a daemon thread; if it never terminates it is abandoned (thread leak
 * checking disabled for this class only).
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class TestPSDPLastSaveFileVisibility extends LuceneTestCase {

  static final long WARMUP_MS = Long.getLong("probe.warmupMs", 1500);
  static final long WAIT_MS = Long.getLong("probe.waitMs", 5000);

  /** Reader body variants. Each loop spins until it sees a snapshot was persisted. */
  abstract static class Reader implements Runnable {
    final PersistentSnapshotDeletionPolicy psdp;
    volatile long seenAtNanos = -1; // written once, after the loop exits
    volatile boolean started;

    Reader(PersistentSnapshotDeletionPolicy psdp) {
      this.psdp = psdp;
    }
  }

  /** The candidate: unsynchronized public getter. */
  static final class RawGetterReader extends Reader {
    RawGetterReader(PersistentSnapshotDeletionPolicy p) {
      super(p);
    }

    @Override
    public void run() {
      started = true;
      while (psdp.getLastSaveFile() == null) {
        // spin
      }
      seenAtNanos = System.nanoTime();
    }
  }

  /** Control 1: same getter, but the caller takes the policy's monitor. */
  static final class LockedGetterReader extends Reader {
    LockedGetterReader(PersistentSnapshotDeletionPolicy p) {
      super(p);
    }

    @Override
    public void run() {
      started = true;
      while (true) {
        String s;
        synchronized (psdp) {
          s = psdp.getLastSaveFile();
        }
        if (s != null) break;
      }
      seenAtNanos = System.nanoTime();
    }
  }

  /** Control 2: an inherited accessor that IS synchronized (SnapshotDeletionPolicy:163). */
  static final class SyncAccessorReader extends Reader {
    SyncAccessorReader(PersistentSnapshotDeletionPolicy p) {
      super(p);
    }

    @Override
    public void run() {
      started = true;
      while (psdp.getSnapshotCount() == 0) {
        // spin
      }
      seenAtNanos = System.nanoTime();
    }
  }

  interface ReaderFactory {
    Reader create(PersistentSnapshotDeletionPolicy p);
  }

  /** Returns the observed latency in ms, or -1 if the reader never observed the update. */
  private long probe(String name, ReaderFactory factory) throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      PersistentSnapshotDeletionPolicy psdp =
          new PersistentSnapshotDeletionPolicy(
              new KeepOnlyLastCommitDeletionPolicy(), dir, OpenMode.CREATE);
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setIndexDeletionPolicy(psdp))) {
        w.addDocument(new Document());
        w.commit();
        assertNull(psdp.getLastSaveFile());
        assertEquals(0, psdp.getSnapshotCount());

        Reader r = factory.create(psdp);
        Thread t = new Thread(r, "psdp-reader-" + name);
        t.setDaemon(true);
        t.start();
        while (!r.started) Thread.onSpinWait();
        Thread.sleep(WARMUP_MS); // let the spin loop get OSR-compiled

        psdp.snapshot(); // synchronized; persist() writes snapshots_0 and sets nextWriteGen = 1
        long wroteAt = System.nanoTime();
        String mainView = psdp.getLastSaveFile(); // same thread as the writer: guaranteed fresh
        t.join(WAIT_MS);
        long seen = r.seenAtNanos;
        String state = t.getState().toString();
        String lockedView;
        synchronized (psdp) {
          lockedView = psdp.getLastSaveFile();
        }
        long latencyMs =
            seen < 0 ? -1 : TimeUnit.NANOSECONDS.toMillis(Math.max(0, seen - wroteAt));
        System.out.println(
            "PROBE "
                + name
                + ": writerView="
                + mainView
                + " lockedView="
                + lockedView
                + " readerObserved="
                + (seen >= 0)
                + (seen >= 0 ? " latencyMs=" + latencyMs : " (still spinning after " + WAIT_MS + " ms)")
                + " readerThreadState="
                + state
                + " vm="
                + System.getProperty("java.vm.info"));
        assertEquals("snapshots_0", mainView);
        assertEquals("snapshots_0", lockedView);
        return latencyMs;
      }
    }
  }

  public void testUnsynchronizedGetterEventuallyObservesSnapshot() throws Exception {
    long latency = probe("raw-getLastSaveFile", RawGetterReader::new);
    assertTrue(
        "reader polling getLastSaveFile() without the monitor never observed nextWriteGen=1 "
            + "within "
            + WAIT_MS
            + " ms after snapshot() returned (stale read)",
        latency >= 0);
  }

  public void testControlLockedGetterObservesSnapshot() throws Exception {
    assertTrue(probe("control-synchronized(psdp)", LockedGetterReader::new) >= 0);
  }

  public void testControlSynchronizedAccessorObservesSnapshot() throws Exception {
    assertTrue(probe("control-getSnapshotCount", SyncAccessorReader::new) >= 0);
  }
}
