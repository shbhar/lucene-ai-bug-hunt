package demo;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.email.UAX29URLEmailTokenizer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Prints the tokens of StandardTokenizer and UAX29URLEmailTokenizer for the brief's inputs, a
 * search count with the default StandardAnalyzer, and a rough tokenization timing (not a
 * benchmark). Public API only. Run once on the stock jars and once with the fixed scanners.
 */
public class HebrewQuoteBriefProbe {

  static String name(char c) {
    return switch (c) {
      case 'א' -> "ALEF";
      case 'ב' -> "BET";
      case 'ג' -> "GIMEL";
      case 'ה' -> "HE";
      case 'ו' -> "VAV";
      case 'ט' -> "TET";
      case 'י' -> "YOD";
      case 'כ' -> "KAF";
      case 'ל' -> "LAMED";
      case 'פ' -> "PE";
      case 'צ' -> "TSADI";
      case 'ר' -> "RESH";
      case 'ש' -> "SHIN";
      case 'ת' -> "TAV";
      case '׳' -> "<GERESH>";
      case '״' -> "<GERSHAYIM>";
      default -> null;
    };
  }

  /** Spells Hebrew letters by name; consecutive letters are separated by one space. */
  static String show(String s) {
    StringBuilder b = new StringBuilder();
    boolean prevLetter = false;
    for (char c : s.toCharArray()) {
      String n = name(c);
      if (n != null) {
        if (prevLetter) b.append(' ');
        b.append(n);
        prevLetter = true;
      } else {
        b.append(c);
        prevLetter = false;
      }
    }
    return b.toString();
  }

  static List<String> tokens(Tokenizer t, String text) throws IOException {
    List<String> out = new ArrayList<>();
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    TypeAttribute type = t.addAttribute(TypeAttribute.class);
    t.setReader(new StringReader(text));
    t.reset();
    while (t.incrementToken()) {
      out.add(term.toString() + "|" + type.type());
    }
    t.end();
    t.close();
    return out;
  }

  static int count(Tokenizer t, String text) throws IOException {
    int n = 0;
    t.setReader(new StringReader(text));
    t.reset();
    while (t.incrementToken()) n++;
    t.end();
    t.close();
    return n;
  }

  public static void main(String[] args) throws Exception {
    String[][] cases = {
      {"shape 1", "ב'2"},
      {"shape 1 (sentence)", "כיתה ט'3"},
      {"shape 1 (underscore)", "א'_a"},
      {"shape 2", "ה.ש'"},
      {"shape 3", "א.צ\"ל"},
      {"shape 3", "א\"ב\"ג"},
      {"shape 4", "ת\"א.ב"},
      {"control", "א'"},
      {"control", "תש\"פ"},
      {"control", "ג'ורג'"},
      {"control", "abc'3"},
      {"control (U+05F3)", "ב׳2"},
      {"control (U+05F4)", "ת״א.ב"},
    };
    for (String[] c : cases) {
      List<String> std = tokens(new StandardTokenizer(), c[1]);
      List<String> uax = tokens(new UAX29URLEmailTokenizer(), c[1]);
      System.out.println(
          c[0] + "  input=" + show(c[1]) + "  std=" + show(std.toString())
              + (std.equals(uax) ? "  uax=same" : "  uax=" + show(uax.toString())));
    }

    try (Directory dir = new ByteBuffersDirectory();
        StandardAnalyzer analyzer = new StandardAnalyzer()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new TextField("body", "כיתה ב'2", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        System.out.println(
            "search: doc 'class BET'2', StandardAnalyzer: body:2 -> "
                + s.count(new TermQuery(new Term("body", "2")))
                + " hit(s); body:BET' -> "
                + s.count(new TermQuery(new Term("body", "ב'")))
                + " hit(s); body:BET'2 -> "
                + s.count(new TermQuery(new Term("body", "ב'2")))
                + " hit(s)");
      }
    }

    // Rough timing: about 24 MB of mixed Latin and Hebrew words, acronyms, geresh and numbers.
    String[] words = {
      "the", "index", "search", "2026", "3.14", "שלום", "ת\"א",
      "צה\"ל", "ג'ורג'", "כיתה",
      "ט'3", "ה.ש'", "a.b", "user_name", "it's", "e-mail"
    };
    Random rnd = new Random(42);
    StringBuilder big = new StringBuilder();
    while (big.length() < 24_000_000) {
      big.append(words[rnd.nextInt(words.length)]).append(' ');
    }
    String text = big.toString();
    for (String name : new String[] {"StandardTokenizer", "UAX29URLEmailTokenizer"}) {
      long best = Long.MAX_VALUE;
      int n = 0;
      for (int i = 0; i < 7; i++) {
        Tokenizer t =
            name.equals("StandardTokenizer") ? new StandardTokenizer() : new UAX29URLEmailTokenizer();
        long t0 = System.nanoTime();
        n = count(t, text);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (i >= 2) best = Math.min(best, ms);
      }
      System.out.println(
          "timing " + name + ": chars=" + text.length() + " tokens=" + n + " best-of-5 ms=" + best);
    }
  }
}
