package org.apache.lucene.search.suggest.tst;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.BytesRef;

/** Prints the TSTLookup tree (split chars as U+XXXX) before store() and after load(), and a few lookups. */
public class TstTreeDumpProbe {
  public static void main(String[] args) throws Exception {
    dump(new String[] {"𠮷野家", "😀 smile", "apple"}, new long[] {5, 4, 3},
        new String[] {"𠮷", "😀", "�", "ap"});
    dump(new String[] {"😀 smile", "Ａb", "ab"}, new long[] {5, 4, 3},
        new String[] {"Ａ", "�", "a", "😀"});
  }

  static void dump(String[] keys, long[] w, String[] prefixes) throws Exception {
    TSTLookup lookup = new TSTLookup(new ByteBuffersDirectory(), "tst");
    lookup.build(iter(keys, w));
    System.out.println("#### dictionary " + esc(String.join(" | ", keys)));
    System.out.println("-- tree before store()");
    print(lookup.root, "root", 0);
    answers(lookup, prefixes);
    ByteBuffersDataOutput out = new ByteBuffersDataOutput();
    lookup.store(out);
    TSTLookup loaded = new TSTLookup(new ByteBuffersDirectory(), "tst");
    loaded.load(out.toDataInput());
    System.out.println("-- tree after load()");
    print(loaded.root, "root", 0);
    answers(loaded, prefixes);
  }

  static void answers(TSTLookup l, String[] prefixes) throws Exception {
    for (String p : prefixes) {
      System.out.println("   lookup(" + esc(p) + ") = " + esc(l.lookup(p, true, 5).toString()));
    }
  }

  static void print(TernaryTreeNode n, String edge, int depth) {
    if (n == null) return;
    System.out.println("   " + "  ".repeat(depth) + edge + ": " + String.format("U+%04X", (int) n.splitchar)
        + (n.token != null ? "  token=" + esc(n.token) + " val=" + n.val : ""));
    print(n.loKid, "lo", depth + 1);
    print(n.eqKid, "eq", depth + 1);
    print(n.hiKid, "hi", depth + 1);
  }

  static String esc(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      if (c >= 0x20 && c < 0x7F) sb.append(c); else sb.append(String.format("<%04X>", (int) c));
    }
    return sb.toString();
  }

  static InputIterator iter(String[] keys, long[] weights) {
    return new InputIterator() {
      int i = -1;
      @Override public BytesRef next() { return ++i < keys.length ? new BytesRef(keys[i].getBytes(StandardCharsets.UTF_8)) : null; }
      @Override public long weight() { return weights[i]; }
      @Override public BytesRef payload() { return null; }
      @Override public boolean hasPayloads() { return false; }
      @Override public Set<BytesRef> contexts() { return null; }
      @Override public boolean hasContexts() { return false; }
    };
  }
}
