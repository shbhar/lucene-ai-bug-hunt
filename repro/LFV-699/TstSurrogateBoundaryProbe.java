package boundary;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.search.suggest.tst.TSTLookup;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;

/**
 * Every UTF-16 surrogate value as a TSTLookup split char (public API only). Builds one TSTLookup
 * whose 1024 supplementary keys put each high surrogate D800..DBFF on a node at char index 0 and
 * each low surrogate DC00..DFFF on a node at char index 1, plus BMP keys at the edges of the
 * surrogate block and of the 3-byte UTF-8 range (U+D7FF, U+E000, U+FFFD, U+FFFE, U+FFFF) and two
 * ASCII keys. It stores the lookup into a ByteBuffersDataOutput, loads it into a fresh TSTLookup and
 * compares, for every key, get(key) and lookup(key, false, 2000), and for every first code point,
 * lookup(prefix, true, 5), with the answers before store(). Exit 1 on any difference.
 */
public class TstSurrogateBoundaryProbe {

  public static void main(String[] args) throws Exception {
    List<String> keys = new ArrayList<>();
    for (int i = 0; i < 1024; i++) {
      keys.add(new String(new char[] {(char) (0xD800 + i), (char) (0xDC00 + i), 'z'}));
    }
    for (char c : new char[] {'\uD7FF', '\uE000', '\uFFFD', '\uFFFE', '\uFFFF'}) {
      keys.add(c + "b");
    }
    keys.add("ab");
    keys.add("zz");
    long[] weights = new long[keys.size()];
    for (int i = 0; i < weights.length; i++) {
      weights[i] = i + 1;
    }
    try (Directory tmp = new ByteBuffersDirectory()) {
      TSTLookup lookup = new TSTLookup(tmp, "tst");
      lookup.build(iter(keys, weights));
      List<String> before = answers(lookup, keys);
      ByteBuffersDataOutput out = new ByteBuffersDataOutput();
      boolean stored = lookup.store(out);
      byte[] bytes = out.toArrayCopy();
      TSTLookup loaded = new TSTLookup(tmp, "tst");
      boolean ok = loaded.load(out.toDataInput());
      List<String> after = answers(loaded, keys);
      int diffs = 0;
      int shown = 0;
      for (int i = 0; i < before.size(); i++) {
        if (!before.get(i).equals(after.get(i))) {
          diffs++;
          // the first 6 differences, and every difference for a key without a surrogate pair
          boolean bmpKey = !Character.isSurrogate(keys.get(i / 3).charAt(0));
          if (shown++ < 6 || bmpKey) {
            System.out.println("  DIFF " + esc(before.get(i)) + "  ->  " + esc(after.get(i)));
          }
        }
      }
      int sur = 0;
      int fffd = 0;
      for (int i = 0; i + 3 < bytes.length; i++) {
        int b0 = bytes[i] & 0xFF, b1 = bytes[i + 1] & 0xFF, b2 = bytes[i + 2] & 0xFF;
        int b3 = bytes[i + 3] & 0xFF;
        if (b0 == 3 && b1 == 0xED && b2 >= 0xA0 && b2 <= 0xBF && b3 >= 0x80 && b3 <= 0xBF) {
          sur++;
        }
        if (b0 == 3 && b1 == 0xEF && b2 == 0xBF && b3 == 0xBD) {
          fffd++;
        }
      }
      System.out.println(
          "keys="
              + keys.size()
              + " checks="
              + before.size()
              + " store()="
              + stored
              + " load()="
              + ok
              + " bytes="
              + bytes.length
              + " len3+ED_A0-BF_80-BF="
              + sur
              + " len3+EF_BF_BD="
              + fffd
              + " getCount()="
              + loaded.getCount()
              + " changed_checks="
              + diffs);
      if (diffs != 0) {
        System.exit(1);
      }
    }
  }

  static List<String> answers(TSTLookup l, List<String> keys) throws Exception {
    List<String> a = new ArrayList<>();
    for (String k : keys) {
      a.add("get(" + k + ")=" + l.get(k));
      a.add("lookup(" + k + ",false)=" + render(l.lookup(k, false, 2000)));
      String first = k.substring(0, Character.charCount(k.codePointAt(0)));
      a.add("lookup(" + first + ",true)=" + render(l.lookup(first, true, 5)));
    }
    return a;
  }

  static String render(List<LookupResult> r) {
    return r.toString();
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

  static InputIterator iter(List<String> keys, long[] weights) {
    return new InputIterator() {
      int i = -1;

      @Override
      public BytesRef next() {
        return ++i < keys.size()
            ? new BytesRef(keys.get(i).getBytes(StandardCharsets.UTF_8))
            : null;
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
