package compat;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.tst.TSTLookup;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;

/**
 * Store-format compatibility probe for TSTLookup (public API only).
 *
 * <pre>
 *   write DIR LABEL   build two dictionaries, print their answers, store them with
 *                     Lookup.store(OutputStream) to DIR/LABEL-sup.bin and DIR/LABEL-ascii.bin
 *   read FILE...      load each file with Lookup.load(InputStream) into a fresh TSTLookup and
 *                     print load()'s result (or the exception) and the same answers
 * </pre>
 *
 * Run it once per TSTLookup build (stock, fix A, fix B) to see which build reads whose files.
 */
public class TstStoreCompatProbe {

  static final String CJK = "\uD842\uDFB7\u91CE\u5BB6"; // U+20BB7 + two BMP chars
  static final String EMOJI = "\uD83D\uDE00 smile"; // U+1F600
  static final String FULLWIDTH = "\uFF21b"; // U+FF21 + 'b'

  static final String[] SUP_KEYS = {CJK, EMOJI, FULLWIDTH, "apple", "ab"};
  static final long[] SUP_WEIGHTS = {5, 4, 3, 2, 1};
  static final String[] SUP_PREFIXES = {
    "\uD842\uDFB7", "\uD83D\uDE00", "\uFF21", "\uFFFD", "ap", "a"
  };

  // the 15 ASCII keys of upstream TestPersistence, weights 0..14
  static final String[] ASCII_KEYS = {
    "one", "two", "three", "four", "oneness", "onerous", "onesimus", "twofold", "twonk", "thrive",
    "through", "threat", "foundation", "fourier", "fourty"
  };
  static final String[] ASCII_PREFIXES = {"o", "one", "tw", "thr", "fou", "f"};

  public static void main(String[] args) throws Exception {
    if (args[0].equals("write")) {
      Path dir = Path.of(args[1]);
      String label = args[2];
      write(dir.resolve(label + "-sup.bin"), SUP_KEYS, SUP_WEIGHTS, SUP_PREFIXES);
      write(dir.resolve(label + "-ascii.bin"), ASCII_KEYS, asciiWeights(), ASCII_PREFIXES);
    } else {
      for (int i = 1; i < args.length; i++) {
        Path f = Path.of(args[i]);
        boolean sup = f.getFileName().toString().endsWith("-sup.bin");
        read(f, sup ? SUP_KEYS : ASCII_KEYS, sup ? SUP_PREFIXES : ASCII_PREFIXES);
      }
    }
  }

  static long[] asciiWeights() {
    long[] w = new long[ASCII_KEYS.length];
    for (int i = 0; i < w.length; i++) {
      w[i] = i;
    }
    return w;
  }

  static void write(Path file, String[] keys, long[] weights, String[] prefixes) throws Exception {
    try (Directory tmp = new ByteBuffersDirectory()) {
      TSTLookup lookup = new TSTLookup(tmp, "tst");
      lookup.build(iter(keys, weights));
      System.out.println("== write " + file.getFileName());
      System.out.println("  ANSWERS " + answers(lookup, keys, prefixes));
      boolean ok;
      try (OutputStream os = Files.newOutputStream(file)) {
        ok = lookup.store(os);
      }
      byte[] b = Files.readAllBytes(file);
      System.out.println(
          "  store()="
              + ok
              + " bytes="
              + b.length
              + " EF_BF_BD="
              + count(b, false)
              + " ED_A0-BF="
              + count(b, true));
    }
  }

  static void read(Path file, String[] keys, String[] prefixes) throws Exception {
    System.out.println("== read " + file.getFileName());
    try (Directory tmp = new ByteBuffersDirectory()) {
      TSTLookup lookup = new TSTLookup(tmp, "tst");
      boolean ok;
      try (InputStream is = Files.newInputStream(file)) {
        ok = lookup.load(is);
      } catch (Throwable t) {
        System.out.println("  load() threw " + t.getClass().getName() + ": " + t.getMessage());
        return;
      }
      String a;
      try {
        a = answers(lookup, keys, prefixes);
      } catch (Throwable t) {
        a = "lookup threw " + t.getClass().getName() + ": " + t.getMessage();
      }
      System.out.println("  load()=" + ok + " getCount()=" + lookup.getCount());
      System.out.println("  ANSWERS " + a);
    }
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

  static int count(byte[] b, boolean edSurrogate) {
    int n = 0;
    for (int i = 0; i + 2 < b.length; i++) {
      int b0 = b[i] & 0xFF, b1 = b[i + 1] & 0xFF, b2 = b[i + 2] & 0xFF;
      if (edSurrogate ? (b0 == 0xED && b1 >= 0xA0 && b1 <= 0xBF) : (b0 == 0xEF && b1 == 0xBF && b2 == 0xBD)) {
        n++;
      }
    }
    return n;
  }

  /** ASCII-only rendering: non-ASCII chars as U+XXXX, so logs are byte-stable. */
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
