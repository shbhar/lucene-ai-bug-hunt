import java.util.concurrent.CountDownLatch;

/**
 * Called by the instrumented SegmentInfos.changed() between "read version" and "write version+1".
 * Pauses only the armed thread, only once, and only when that thread holds the SegmentInfos
 * monitor but NOT the IndexWriter monitor (i.e. it is inside IndexWriter.newSegmentName()).
 */
public final class R6Hook {
  private static volatile Thread target;
  private static volatile Object sisMonitor;
  private static volatile Object writerMonitor;
  public static volatile CountDownLatch loaded = new CountDownLatch(1);
  public static volatile CountDownLatch release = new CountDownLatch(1);
  public static volatile long loadedValue = -1;
  public static volatile StackTraceElement[] pausedAt;

  public static void arm(Thread t, Object sis, Object writer) {
    loaded = new CountDownLatch(1);
    release = new CountDownLatch(1);
    loadedValue = -1;
    pausedAt = null;
    sisMonitor = sis;
    writerMonitor = writer;
    target = t;
  }

  public static void disarm() {
    target = null;
    release.countDown();
  }

  public static void afterLoad(long v) {
    Thread t = target;
    if (t == null || t != Thread.currentThread()) {
      return;
    }
    Object s = sisMonitor, w = writerMonitor;
    if (s == null || Thread.holdsLock(s) == false || (w != null && Thread.holdsLock(w))) {
      return;
    }
    target = null; // one shot
    loadedValue = v;
    pausedAt = Thread.currentThread().getStackTrace();
    loaded.countDown();
    try {
      release.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
