package org.example.verify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check (public API only) of the claim that ASCIIFoldingFilter folds a few letters to
 * the ASCII letter of the opposite case.
 */
public class TestFoldCaseV1 extends LuceneTestCase {

  /** Folds a single BMP char with the public static helper. */
  private static String fold(char c) {
    char[] in = {c};
    char[] out = new char[16];
    int n = ASCIIFoldingFilter.foldToASCII(in, 0, out, 0, 1);
    return new String(out, 0, n);
  }

  private static String hex(int c) {
    return String.format("U+%04X", c);
  }

  /** Direct table check: each letter, its Unicode case, and its expected ASCII letter. */
  public void testDirectFoldKeepsCase() {
    // {char, expected}; controls first (their case partners), then the claimed outliers.
    String[][] cases = {
      // controls, expected to pass
      {"Ǥ", "G"}, // CAPITAL G WITH STROKE
      {"Ǧ", "G"}, // CAPITAL G WITH CARON
      {"ğ", "g"}, // small g with breve
      {"ɐ", "a"}, // small turned a
      {"ꜿ", "c"}, // small reversed c with dot
      {"Ꝼ", "F"}, // capital insular F
      {"ꝼ", "f"}, // small insular f
      {"ƿ", "w"}, // LATIN LETTER WYNN (Ll, caseless-style name) -> lowercase
      // claimed outliers
      {"ǥ", "g"}, // LATIN SMALL LETTER G WITH STROKE
      {"ǧ", "g"}, // LATIN SMALL LETTER G WITH CARON
      {"ꞅ", "s"}, // LATIN SMALL LETTER INSULAR S
      {"Ꞅ", "S"}, // LATIN CAPITAL LETTER INSULAR S
      {"ʗ", "c"}, // LATIN LETTER STRETCHED C (general category Ll)
      {"Ɐ", "A"}, // LATIN CAPITAL LETTER TURNED A
      {"Ꜿ", "C"}, // LATIN CAPITAL LETTER REVERSED C WITH DOT
    };
    List<String> bad = new ArrayList<>();
    for (String[] tc : cases) {
      char c = tc[0].charAt(0);
      String got = fold(c);
      String unicodeCase =
          Character.isLowerCase(c) ? "Ll" : Character.isUpperCase(c) ? "Lu" : "other";
      if (!got.equals(tc[1])) {
        bad.add(
            hex(c)
                + " "
                + Character.getName(c)
                + " ("
                + unicodeCase
                + "): expected '"
                + tc[1]
                + "' got '"
                + got
                + "'");
      }
    }
    assertTrue("wrong-case folds:\n  " + String.join("\n  ", bad), bad.isEmpty());
  }

  /**
   * Exhaustive BMP scan with the JDK's Unicode data: a char that is Ll (or Lu) and is not named
   * SMALL CAPITAL and folds to exactly one ASCII letter should fold to a lowercase (or uppercase)
   * ASCII letter. Prints every violation so the set can be compared with the claim.
   */
  public void testExhaustiveCaseConsistency() {
    List<String> bad = new ArrayList<>();
    int mappedLetters = 0;
    for (int c = 0x80; c <= 0xFFFF; c++) {
      if (Character.isSurrogate((char) c)) continue;
      String f = fold((char) c);
      if (f.length() != 1 || f.charAt(0) == (char) c) continue;
      char o = f.charAt(0);
      if (!((o >= 'a' && o <= 'z') || (o >= 'A' && o <= 'Z'))) continue;
      boolean lower = Character.isLowerCase(c);
      boolean upper = Character.isUpperCase(c);
      if (!lower && !upper) continue;
      String name = Character.getName(c);
      if (name != null && name.contains("SMALL CAPITAL")) continue;
      mappedLetters++;
      boolean outLower = o >= 'a' && o <= 'z';
      if (lower != outLower) {
        bad.add(hex(c) + " " + name + " -> '" + o + "'");
      }
    }
    assertTrue(
        bad.size()
            + " of "
            + mappedLetters
            + " single-letter folds change case:\n  "
            + String.join("\n  ", bad),
        bad.isEmpty());
  }

  private static Analyzer lowercaseThenFold() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new StandardTokenizer();
        TokenStream ts = new LowerCaseFilter(t);
        ts = new ASCIIFoldingFilter(ts);
        return new TokenStreamComponents(t, ts);
      }
    };
  }

  private static Analyzer foldOnly() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new WhitespaceTokenizer();
        return new TokenStreamComponents(t, new ASCIIFoldingFilter(t));
      }
    };
  }

  private static List<String> terms(Analyzer a, String text) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", text)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) out.add(term.toString());
      ts.end();
    }
    return out;
  }

  /**
   * End-to-end: one document per text, same analyzer at index and query time; each ASCII query must
   * find its document.
   */
  private void checkSearch(Analyzer a, String[][] docsAndQueries) throws IOException {
    List<String> misses = new ArrayList<>();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        for (int i = 0; i < docsAndQueries.length; i++) {
          Document d = new Document();
          d.add(new TextField("id", "d" + i, Field.Store.YES));
          d.add(new TextField("f", docsAndQueries[i][0], Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        for (int i = 0; i < docsAndQueries.length; i++) {
          String text = docsAndQueries[i][0];
          String query = docsAndQueries[i][1];
          BooleanQuery.Builder b = new BooleanQuery.Builder();
          for (String qt : terms(a, query)) {
            b.add(new TermQuery(new Term("f", qt)), BooleanClause.Occur.MUST);
          }
          b.add(new TermQuery(new Term("id", "d" + i)), BooleanClause.Occur.FILTER);
          int hits = s.count(b.build());
          if (hits != 1) {
            misses.add(
                "'"
                    + text
                    + "' indexed as "
                    + terms(a, text)
                    + "; query '"
                    + query
                    + "' -> "
                    + terms(a, query)
                    + ", hits="
                    + hits);
          }
        }
      }
    }
    assertTrue(
        misses.size() + " document(s) not found:\n  " + String.join("\n  ", misses),
        misses.isEmpty());
  }

  public void testLowercaseThenFoldSearch() throws IOException {
    Analyzer a = lowercaseThenFold();
    checkSearch(
        a,
        new String[][] {
          {"Naǧīb Maḥfūẓ", "Nagib Mahfuz"}, // DIN 31635 romanisation
          {"NAǦĪB", "nagib"}, // capital caron G, lower-cased to U+01E7 first
          {"čååǥǥam", "caaggam"}, // Skolt Sami, small g with stroke
          {"ꞅeo", "seo"}, // small insular s
          {"ʗak", "cak"}, // stretched c
          // controls
          {"Gökçe Ğ", "gokce g"},
          {"ṡeo", "seo"},
        });
    a.close();
  }

  public void testCaseKeepingFoldSearch() throws IOException {
    Analyzer a = foldOnly();
    checkSearch(
        a,
        new String[][] {
          {"Ɐbc", "Abc"}, // capital turned A
          {"Ꜿbc", "Cbc"}, // capital reversed C with dot
          {"Ꞅbc", "Sbc"}, // capital insular S
          {"ǧbc", "gbc"},
          // controls
          {"ɐbc", "abc"},
          {"Ǧbc", "Gbc"},
          {"Ꝼbc", "Fbc"},
        });
    a.close();
  }
}
