package org.apache.lucene.search.suggest.tst;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;

/**
 * White-box probe: is the information lost in a stored TSTLookup file recoverable from the file
 * itself? It loads FILE (written by the stock store(OutputStream), see TstStoreCompatProbe) with the
 * stock load(InputStream), prints the answers, then re-derives every node's split char from a token
 * stored at or below the node (split char = token.charAt(number of eq edges from the root)), keeping
 * the tree shape, and prints the answers again. It compares both with a TSTLookup freshly built from
 * the same dictionary. It sits in the tst package only to reach the package-private tree; the
 * lookups are public API. Exit 1 if the repaired copy differs from the fresh build.
 *
 * <pre>
 *   TstRepairFromTokensProbe FILE...   (FILE names end in -sup.bin or -ascii.bin)
 * </pre>
 */
public class TstRepairFromTokensProbe {

  static final String CJK = "\uD842\uDFB7\u91CE\u5BB6"; // U+20BB7 + two BMP chars
  static final String EMOJI = "\uD83D\uDE00 smile"; // U+1F600
  static final String FULLWIDTH = "\uFF21b"; // U+FF21 + 'b'
  static final String[] SUP_KEYS = {CJK, EMOJI, FULLWIDTH, "apple", "ab"};
  static final long[] SUP_WEIGHTS = {5, 4, 3, 2, 1};
  static final String[] SUP_PREFIXES = {
    "\uD842\uDFB7", "\uD83D\uDE00", "\uFF21", "\uFFFD", "ap", "a"
  };
  static final String[] ASCII_KEYS = {
    "one", "two", "three", "four", "oneness", "onerous", "onesimus", "twofold", "twonk", "thrive",
    "through", "threat", "foundation", "fourier", "fourty"
  };
  static final String[] ASCII_PREFIXES = {"o", "one", "tw", "thr", "fou", "f"};

  public static void main(String[] args) throws Exception {
    boolean allOk = true;
    for (String a : args) {
      Path f = Path.of(a);
      boolean sup = f.getFileName().toString().endsWith("-sup.bin");
      String[] keys = sup ? SUP_KEYS : ASCII_KEYS;
      String[] prefixes = sup ? SUP_PREFIXES : ASCII_PREFIXES;
      long[] weights = sup ? SUP_WEIGHTS : asciiWeights();
      try (Directory tmp = new ByteBuffersDirectory()) {
        TSTLookup fresh = new TSTLookup(tmp, "tst");
        fresh.build(iter(keys, weights));
        String expected = answers(fresh, keys, prefixes);
        TSTLookup loaded = new TSTLookup(tmp, "tst");
        boolean ok;
        try (InputStream is = Files.newInputStream(f)) {
          ok = loaded.load(is);
        }
        String asLoaded = answers(loaded, keys, prefixes);
        int[] stats = new int[2]; // nodes visited, split chars changed by the repair
        repair(loaded.root, 0, stats);
        String repaired = answers(loaded, keys, prefixes);
        System.out.println("== " + f.getFileName() + " load()=" + ok);
        System.out.println("  FRESH    " + expected);
        System.out.println("  LOADED   " + asLoaded + "   [equals fresh: " + asLoaded.equals(expected) + "]");
        System.out.println(
            "  REPAIRED " + repaired + "   [equals fresh: " + repaired.equals(expected) + "]");
        System.out.println("  nodes=" + stats[0] + " split_chars_rederived_differently=" + stats[1]);
        allOk &= repaired.equals(expected);
      }
    }
    System.out.println("ALL_REPAIRED_EQUAL_FRESH=" + allOk);
    if (!allOk) {
      System.exit(1);
    }
  }

  /** depth = number of eq edges from the root; a key passes a node at its char index depth. */
  static void repair(TernaryTreeNode n, int depth, int[] stats) {
    if (n == null) {
      return;
    }
    stats[0]++;
    String t = n.token != null ? n.token : anyToken(n.eqKid);
    if (t != null && depth < t.length() && t.charAt(depth) != n.splitchar) {
      n.splitchar = t.charAt(depth);
      stats[1]++;
    }
    repair(n.loKid, depth, stats);
    repair(n.eqKid, depth + 1, stats);
    repair(n.hiKid, depth, stats);
  }

  static String anyToken(TernaryTreeNode n) {
    if (n == null) {
      return null;
    }
    if (n.token != null) {
      return n.token;
    }
    String t = anyToken(n.eqKid);
    if (t == null) {
      t = anyToken(n.loKid);
    }
    return t != null ? t : anyToken(n.hiKid);
  }

  static long[] asciiWeights() {
    long[] w = new long[ASCII_KEYS.length];
    for (int i = 0; i < w.length; i++) {
      w[i] = i;
    }
    return w;
  }

  static String answers(TSTLookup lookup, String[] keys, String[] prefixes) throws Exception {
    List<String> out = new ArrayList<>();
    for (String p : prefixes) {
      out.add("lookup(" + esc(p) + ")=" + esc(lookup.lookup(p, true, 10).toString()));
    }
    for (String k : keys) {
      out.add("get(" + esc(k) + ")=" + lookup.get(k));
    }
    return String.join("; ", out);
  }

  static String esc(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      if (c >= 0x20 && c < 0x7F) {
        sb.append(c);
      } else {
        sb.append(String.format("<%04X>", (int) c));
      }
    }
    return sb.toString();
  }

  static InputIterator iter(String[] keys, long[] weights) {
    return new InputIterator() {
      int i = -1;

      @Override
      public BytesRef next() {
        return ++i < keys.length ? new BytesRef(keys[i].getBytes(StandardCharsets.UTF_8)) : null;
      }

      @Override
      public long weight() {
        return weights[i];
      }

      @Override
      public BytesRef payload() {
        return null;
      }

      @Override
      public boolean hasPayloads() {
        return false;
      }

      @Override
      public Set<BytesRef> contexts() {
        return null;
      }

      @Override
      public boolean hasContexts() {
        return false;
      }
    };
  }
}
