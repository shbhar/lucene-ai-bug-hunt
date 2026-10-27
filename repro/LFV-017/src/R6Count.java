import java.util.concurrent.atomic.LongAdder;

/** Counts entries into SegmentInfos.changed() (agent "count" mode). Each entry is one version++. */
public final class R6Count {
  public static final LongAdder CALLS = new LongAdder();

  public static void enter() {
    CALLS.increment();
  }
}
