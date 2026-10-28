package org.apache.lucene.queries.intervals;

import java.io.IOException;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Version;

/**
 * Version-portable history probe for the RepeatingIntervalsSource width() sign bug.
 *
 * <p>It compiles and runs unchanged against Lucene 8.4.x .. main: it uses no test-framework classes
 * (the test-framework package moved in 9.x) and no IntervalIterator#width() (added in 8.5.0). The
 * hit-count and iterator-gaps checks use public API only. The Matches-gaps diagnostic reads
 * IntervalMatchesIterator#gaps(), which is public from 8.5.0 and package-private before; the probe
 * lives in the intervals package so it can read it on every version, as Lucene's own tests do.
 *
 * <p>Exit 0: every check behaves as documented. Exit 1: at least one bug check fails and all
 * controls pass. Exit 3: a control fails (the probe is not meaningful on this version).
 */
public class RepeatWidthHistoryProbe {

  static int bugFails = 0;
  static int controlFails = 0;

  public static void main(String[] args) throws Exception {
    System.out.println("lucene Version.LATEST=" + Version.LATEST);
    IntervalsSource ny = Intervals.phrase("new", "york");
    IntervalsSource abc = Intervals.phrase("a", "b", "c");

    // B1/B2: two copies of a two-position phrase, back to back: 0 gaps, so maxgaps(0) must match.
    countCheck("B1", false, "new york new york", Intervals.maxgaps(0, Intervals.ordered(ny, ny)), 1);
    countCheck("B2", false, "new york new york", Intervals.maxgaps(0, Intervals.unordered(ny, ny)), 1);
    // B3: one position between the copies: 1 gap, so maxgaps(1) must match.
    countCheck("B3", false, "new york x new york", Intervals.maxgaps(1, Intervals.ordered(ny, ny)), 1);
    // B4/B5: iterator gaps() of the first interval.
    gapsCheck("B4", false, "new york new york", Intervals.ordered(ny, ny), 0, 3, 0);
    gapsCheck("B5", false, "a b c a b c", Intervals.ordered(abc, abc), 0, 5, 0);

    // Controls: no repeated sub-source (C1), and a repeated single-position source (C2).
    countCheck(
        "C1",
        true,
        "new york new jersey",
        Intervals.maxgaps(0, Intervals.ordered(ny, Intervals.phrase("new", "jersey"))),
        1);
    countCheck(
        "C2",
        true,
        "a a",
        Intervals.maxgaps(0, Intervals.ordered(Intervals.term("a"), Intervals.term("a"))),
        1);
    gapsCheck(
        "C3",
        true,
        "new york new jersey",
        Intervals.ordered(ny, Intervals.phrase("new", "jersey")),
        0,
        3,
        0);

    System.out.println("SUMMARY bugFails=" + bugFails + " controlFails=" + controlFails);
    if (controlFails > 0) {
      System.exit(3);
    }
    System.exit(bugFails > 0 ? 1 : 0);
  }

  static void record(String id, boolean control, boolean ok, String msg) {
    System.out.println((ok ? "OK   " : "FAIL ") + id + (control ? " [control] " : " [bug] ") + msg);
    if (!ok) {
      if (control) {
        controlFails++;
      } else {
        bugFails++;
      }
    }
  }

  static Directory index(String text) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
      Document doc = new Document();
      doc.add(new TextField("f", text, Field.Store.NO));
      w.addDocument(doc);
    }
    return dir;
  }

  static void countCheck(String id, boolean control, String text, IntervalsSource src, int expected)
      throws IOException {
    try (Directory dir = index(text);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setQueryCache(null);
      Query q = new IntervalQuery("f", src);
      int count = searcher.count(q);
      Weight weight = searcher.createWeight(searcher.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1f);
      Matches m = weight.matches(reader.leaves().get(0), 0);
      String matchesStr = m == null ? "null" : "non-null";
      String consistency = ((m != null) == (count == 1)) ? "consistent" : "INCONSISTENT";
      record(
          id,
          control,
          count == expected,
          q + " on '" + text + "': count=" + count + " (expected " + expected + "), Weight#matches="
              + matchesStr + " (" + consistency + " with the scorer)");
    }
  }

  static void gapsCheck(
      String id, boolean control, String text, IntervalsSource src, int expStart, int expEnd,
      int expGaps)
      throws IOException {
    try (Directory dir = index(text);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      LeafReaderContext ctx = reader.leaves().get(0);
      IntervalIterator it = src.intervals("f", ctx);
      String ivStr;
      boolean ok;
      if (it == null || it.nextDoc() != 0) {
        ivStr = "no intervals for doc 0";
        ok = false;
      } else if (it.nextInterval() == IntervalIterator.NO_MORE_INTERVALS) {
        ivStr = "doc 0 has no interval";
        ok = false;
      } else {
        ivStr = "[" + it.start() + "," + it.end() + "] gaps=" + it.gaps();
        ok = it.start() == expStart && it.end() == expEnd && it.gaps() == expGaps;
      }
      String miStr;
      MatchesIterator mi = src.matches("f", ctx, 0);
      if (mi == null) {
        miStr = "matches=null";
      } else if (mi.next() == false) {
        miStr = "matches=empty";
      } else if (mi instanceof IntervalMatchesIterator) {
        miStr =
            "matches [" + mi.startPosition() + "," + mi.endPosition() + "] gaps="
                + ((IntervalMatchesIterator) mi).gaps();
      } else {
        miStr =
            "matches [" + mi.startPosition() + "," + mi.endPosition() + "] gaps=n/a ("
                + mi.getClass().getSimpleName() + ")";
      }
      record(
          id,
          control,
          ok,
          src + " on '" + text + "': iterator " + ivStr + " (expected [" + expStart + "," + expEnd
              + "] gaps=" + expGaps + "); " + miStr);
    }
  }
}
