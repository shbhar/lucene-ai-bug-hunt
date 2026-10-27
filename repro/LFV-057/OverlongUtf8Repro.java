import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.AutomatonQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.ByteRunAutomaton;
import org.apache.lucene.util.automaton.CompiledAutomaton;
import org.apache.lucene.util.automaton.FiniteStringsIterator;
import org.apache.lucene.util.automaton.RegExp;

/**
 * Plain-Java repro (lucene-core only). E0 80 80 is not UTF-8: it is an "overlong" 3-byte spelling
 * of U+0000, whose real encoding is the single byte 00. Real 3-byte UTF-8 after E0 needs a second
 * byte A0..BF. Exits 1 if any overlong form is accepted, else 0.
 */
public class OverlongUtf8Repro {
  static boolean bad = false;

  static byte[] bytes(int... b) {
    byte[] out = new byte[b.length];
    for (int i = 0; i < b.length; i++) out[i] = (byte) b[i];
    return out;
  }

  static void probe(String what, ByteRunAutomaton a, boolean mustAccept, int... b) {
    byte[] in = bytes(b);
    boolean accepted = a.run(in, 0, in.length);
    StringBuilder hex = new StringBuilder();
    for (int x : b) hex.append(String.format("%02X ", x));
    StringBuilder cps = new StringBuilder();
    new BytesRef(in).utf8ToString().codePoints().forEach(c -> cps.append(String.format("U+%04X ", c)));
    boolean ok = accepted == mustAccept;
    if (!ok) bad = true;
    System.out.printf(
        "%-30s %-15s accepted=%-5s (Lucene decodes it to %s) %s%n",
        what, hex.toString().trim(), accepted, cps.toString().trim(), ok ? "ok" : "WRONG");
  }

  static int strings(int lo, int hi) {
    CompiledAutomaton c = new CompiledAutomaton(Automata.makeCharRange(lo, hi));
    FiniteStringsIterator it = new FiniteStringsIterator(c.automaton);
    int n = 0;
    for (IntsRef r = it.next(); r != null; r = it.next()) n++;
    return n;
  }

  public static void main(String[] args) throws Exception {
    ByteRunAutomaton upTo0FFF = new ByteRunAutomaton(Automata.makeCharRange(0x7F, 0x0FFF));
    ByteRunAutomaton upTo1000 = new ByteRunAutomaton(Automata.makeCharRange(0x7F, 0x1000));
    probe("range [U+007F, U+0FFF]", upTo0FFF, false, 0xE0, 0x80, 0x80);
    probe("range [U+007F, U+1000]", upTo1000, false, 0xE0, 0x80, 0x80);
    probe("range [U+007F, U+1000]", upTo1000, true, 0xE0, 0xA0, 0x80); // real U+0800
    ByteRunAutomaton any = new ByteRunAutomaton(Automata.makeAnyChar());
    probe("makeAnyChar()", any, false, 0xE0, 0x80, 0x80);
    probe("makeAnyChar()", any, false, 0xF0, 0x80, 0x80, 0x80);
    probe("makeAnyChar()", any, false, 0xC0, 0x80); // 2-byte overlong: correctly rejected
    probe("regexp .", new ByteRunAutomaton(new RegExp(".").toAutomaton()), false, 0xE0, 0x9F, 0xBF);
    ByteRunAutomaton noSlash = new ByteRunAutomaton(new RegExp("[^/]*").toAutomaton());
    probe("regexp [^/]*", noSlash, false, 'a', '/', 'b');
    probe("regexp [^/]*", noSlash, false, 'a', 0xE0, 0x80, 0xAF, 'b');

    System.out.printf(
        "strings in CompiledAutomaton [U+007F, U+0FFF]: %d (code points: %d)%n",
        strings(0x7F, 0x0FFF), 0x0FFF - 0x7F + 1);
    int n = strings(0x7F, 0x1000);
    if (n != 0x1000 - 0x7F + 1) bad = true;
    System.out.printf(
        "strings in CompiledAutomaton [U+007F, U+1000]: %d (code points: %d)%n",
        n, 0x1000 - 0x7F + 1);

    // End to end: the binary term E0 80 80 next to "a", searched with "one character" queries.
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document d1 = new Document();
        d1.add(new StringField("f", "a", Field.Store.NO));
        w.addDocument(d1);
        Document d2 = new Document();
        d2.add(new StringField("f", new BytesRef(bytes(0xE0, 0x80, 0x80)), Field.Store.NO));
        w.addDocument(d2);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        int dot = s.count(new RegexpQuery(new Term("f", ".")));
        int q = s.count(new WildcardQuery(new Term("f", "?")));
        int notNul =
            s.count(new AutomatonQuery(new Term("f"), Automata.makeCharRange(1, 0x10FFFF)));
        if (dot != 1 || q != 1 || notNul != 1) bad = true;
        System.out.printf(
            "hits (expected 1 each): RegexpQuery(.)=%d WildcardQuery(?)=%d"
                + " AutomatonQuery[U+0001, U+10FFFF]=%d%n",
            dot, q, notNul);
      }
    }
    System.out.println(bad ? "RESULT: overlong UTF-8 accepted (bug)" : "RESULT: ok");
    System.exit(bad ? 1 : 0);
  }
}
