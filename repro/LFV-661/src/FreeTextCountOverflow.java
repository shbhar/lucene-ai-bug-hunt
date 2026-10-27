import java.util.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.search.suggest.analyzing.FreeTextSuggester;
import org.apache.lucene.util.BytesRef;

/**
 * Builds a unigram FreeTextSuggester in which the word "a" occurs more than Integer.MAX_VALUE
 * times and "ab" once, then checks the documented score range [0, 1] and the order.
 * Args: [occurrences of "a" (default 2^31 + 2^20)] [tokens per entry (default 2^20)]. Exits 1 on a violation.
 */
public class FreeTextCountOverflow {
  public static void main(String[] args) throws Exception {
    long total = args.length > 0 ? Long.parseLong(args[0]) : (1L << 31) + (1L << 20);
    int perEntry = args.length > 1 ? Integer.parseInt(args[1]) : 1 << 20;
    StringBuilder sb = new StringBuilder(perEntry * 2);
    for (int i = 0; i < perEntry; i++) sb.append("a ");
    final BytesRef chunk = new BytesRef(sb.toString());
    final long entries = (total + perEntry - 1) / perEntry;
    InputIterator it = new InputIterator() {
      long i = 0;
      boolean extra = true;
      public BytesRef next() {
        if (i < entries) { i++; return chunk; }
        if (extra) { extra = false; return new BytesRef("ab"); }
        return null;
      }
      public long weight() { return 1; }
      public BytesRef payload() { return null; }
      public boolean hasPayloads() { return false; }
      public Set<BytesRef> contexts() { return null; }
      public boolean hasContexts() { return false; }
    };
    Analyzer a = new WhitespaceAnalyzer();
    FreeTextSuggester s = new FreeTextSuggester(a, a, 1);
    long t0 = System.nanoTime();
    s.build(it);
    System.out.printf(Locale.ROOT, "built: entries=%d count(a)=%d build_s=%.1f%n", entries + 1, entries * perEntry, (System.nanoTime() - t0) / 1e9);
    List<LookupResult> rs = s.lookup("a", 2);
    boolean bad = false;
    for (LookupResult r : rs) {
      double score = r.value / (double) Long.MAX_VALUE;
      System.out.printf(Locale.ROOT, "  %s value=%d score=%.6f%n", r.key, r.value, score);
      if (score < 0 || score > 1) bad = true;
    }
    if (rs.isEmpty() || !rs.get(0).key.toString().equals("a")) bad = true; // "a" is by far the most frequent
    System.out.println(bad ? "VIOLATION: score outside [0, 1] or 'a' not first" : "ok");
    if (bad) System.exit(1);
  }
}
