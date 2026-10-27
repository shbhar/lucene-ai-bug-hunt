import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.lucene.search.FieldStats;
import org.apache.lucene.search.TermStats;
import org.apache.lucene.search.similarities.AxiomaticF2EXP;
import org.apache.lucene.search.similarities.AxiomaticF2LOG;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.SmallFloat;

/**
 * Independent random search (verifier's own tool) for index-realizable statistics where
 * AxiomaticF2LOG / AxiomaticF2EXP SimScorer.score(fHi, norm) < score(fLo, norm) with fHi > fLo.
 *
 * <p>Inner loop is a transcription of Axiomatic.score + AxiomaticF2*.tfln/idf +
 * SimilarityBase.BasicSimScorer.score (same Java operation order); every hit is re-checked on the
 * REAL Lucene classes and only real-class-confirmed hits are printed.
 *
 * <p>Statistics model (what an index can produce): docCount N (= maxDoc), docFreq n of the term,
 * sumTotalTermFreq = sum; the two "crucial" docs have field length L = byte4ToInt(normByte), so the
 * decoded docLen equals the real length. fLo and fHi are adjacent floats at the binade boundary
 * 2^e (fLo = 2^e - 1 for e <= 24, else 2^e - 2^(e-24)), both exact integers.
 *
 * <p>args: model(LOG|EXP) s k boost e trialsPerThread threads Nmin Nmax seed
 */
public class F2Search {
  public static void main(String[] a) throws Exception {
    final boolean log = a[0].equals("LOG");
    final float s = Float.parseFloat(a[1]);
    final float k = Float.parseFloat(a[2]);
    final float boost = Float.parseFloat(a[3]);
    final int e = Integer.parseInt(a[4]);
    final long trials = Long.parseLong(a[5]);
    final int threads = Integer.parseInt(a[6]);
    final int nMin = Integer.parseInt(a[7]);
    final int nMax = Integer.parseInt(a[8]);
    final long seed = Long.parseLong(a[9]);

    final float fHi = (float) (1L << e);
    final float fLo = Math.nextDown(fHi);
    if (fLo != (float) (long) fLo) throw new AssertionError();
    // norm bytes whose decoded length is >= fHi and fits in an int field length
    List<Integer> normBytes = new ArrayList<>();
    for (int b = 1; b < 256; b++) {
      long len = SmallFloat.byte4ToInt((byte) b);
      if (len >= (long) fHi && len <= Integer.MAX_VALUE) normBytes.add(b);
    }
    System.out.println(
        "model=F2"
            + (log ? "LOG" : "EXP")
            + " s="
            + s
            + " k="
            + k
            + " boost="
            + boost
            + " fLo="
            + (long) fLo
            + " fHi="
            + (long) fHi
            + " candidate norm bytes="
            + normBytes);
    final int[] nb = normBytes.stream().mapToInt(Integer::intValue).toArray();
    final AtomicInteger hits = new AtomicInteger();
    final AtomicLong dblDrops = new AtomicLong();
    Thread[] ts = new Thread[threads];
    for (int t = 0; t < threads; t++) {
      final long tseed = seed * 1000003L + t;
      ts[t] =
          new Thread(
              () -> {
                SplittableRandom r = new SplittableRandom(tseed);
                final double sd = s;
                final double bd = boost;
                final double fl = fLo, fh = fHi;
                long localDrops = 0;
                for (long i = 0; i < trials && hits.get() < 5; i++) {
                  int N = nMin + r.nextInt(nMax - nMin + 1);
                  int n = 512 + r.nextInt(N - 512 + 1);
                  int b = nb[r.nextInt(nb.length)];
                  long L = SmallFloat.byte4ToInt((byte) b);
                  // 3 crucial docs of length L, the N-3 others of length in [1, 2^31-1]
                  long sumMin = 3 * L + (N - 3);
                  long sumMax = 3 * L + (long) (N - 3) * Integer.MAX_VALUE;
                  long sum = sumMin + (long) (r.nextDouble() * (sumMax - sumMin));
                  double avg = sum / (double) N;
                  double docLen = (double) (float) L;
                  double c2 = sd * docLen / avg;
                  double qLo = fl / (fl + sd + c2);
                  double qHi = fh / (fh + sd + c2);
                  if (qHi >= qLo) continue;
                  localDrops++;
                  double idf = log ? Math.log((N + 1.0) / n) : Math.pow((N + 1.0) / n, (double) k);
                  double scLo = 1.0 * 1.0 * qLo * idf - 0.0;
                  scLo *= bd;
                  double scHi = 1.0 * 1.0 * qHi * idf - 0.0;
                  scHi *= bd;
                  float oLo = (float) Math.max(0, scLo);
                  float oHi = (float) Math.max(0, scHi);
                  if (oHi < oLo) {
                    // confirm on the real classes
                    Similarity sim = log ? new AxiomaticF2LOG(s) : new AxiomaticF2EXP(s, k);
                    FieldStats fs = new FieldStats("body", N, N, sum, (long) N * 2);
                    TermStats tst = new TermStats(new BytesRef("t"), n, n);
                    Similarity.SimScorer sc = sim.scorer(boost, fs, tst);
                    float rLo = sc.score(fLo, b);
                    float rHi = sc.score(fHi, b);
                    if (rLo != oLo || rHi != oHi) {
                      System.out.println("REPLICA MISMATCH " + rLo + " " + oLo + " " + rHi + " " + oHi);
                      continue;
                    }
                    hits.incrementAndGet();
                    synchronized (F2Search.class) {
                      System.out.println(
                          "HIT N="
                              + N
                              + " n="
                              + n
                              + " normByte="
                              + b
                              + " L="
                              + L
                              + " sumTotalTermFreq="
                              + sum
                              + " score(fLo)="
                              + rLo
                              + " (0x"
                              + Integer.toHexString(Float.floatToIntBits(rLo))
                              + ") score(fHi)="
                              + rHi
                              + " (0x"
                              + Integer.toHexString(Float.floatToIntBits(rHi))
                              + ") qLo-qHi ulps="
                              + Math.round((qLo - qHi) / Math.ulp(qLo)));
                    }
                  }
                }
                dblDrops.addAndGet(localDrops);
              });
      ts[t].start();
    }
    for (Thread t : ts) t.join();
    System.out.println("double-level drops seen: " + dblDrops.get() + ", confirmed hits: " + hits.get());
  }
}
