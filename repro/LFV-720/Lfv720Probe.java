package demo;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.lang.UScript;
import com.ibm.icu.text.RuleBasedBreakIterator;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.icu.segmentation.DefaultICUTokenizerConfig;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizerConfig;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizerFactory;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.analysis.util.FilesystemResourceLoader;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.QueryBuilder;

/**
 * LFV-720 probe (main(), dump only). Token dumps for ICUTokenizer in the default mode
 * (myanmarAsWords=true), in syllable mode (myanmarAsWords=false) with the shipped
 * MyanmarSyllable.brk, with rule files compiled at runtime (the stock rules as a sanity check and
 * the two candidate fixes), with compiled .brk files given on the command line, and through the
 * factory option rulefiles="Mymr:..." (a workaround that needs no Lucene change); then a small
 * search: documents "KA+U+A9E6" and "KA", queries built by QueryBuilder with the same analyzer.
 *
 * <p>args: stock.rbbi fixA.rbbi fixB.rbbi [label=path.brk ...]
 */
public class Lfv720Probe {

  static final String KA = "က";
  static final String SHAN = "ꧦ"; // MYANMAR MODIFIER LETTER SHAN REDUPLICATION
  static final String KHAMTI = "ꩰ"; // MYANMAR MODIFIER LETTER KHAMTI REDUPLICATION
  static final String KAUNG = "ကောင်း"; // "good"

  static String hex(String s) {
    StringBuilder b = new StringBuilder();
    s.codePoints()
        .forEach(
            cp -> {
              if (b.length() > 0) b.append(' ');
              b.append(String.format(Locale.ROOT, "U+%04X", cp));
            });
    return b.toString();
  }

  static String props(int cp) {
    return String.format(
        Locale.ROOT,
        "U+%04X %s gc=%s sc=%s ISC=%s WB=%s",
        cp,
        UCharacter.getName(cp),
        UCharacter.getPropertyValueName(
            UProperty.GENERAL_CATEGORY, UCharacter.getType(cp), UProperty.NameChoice.SHORT),
        UScript.getShortName(UScript.getScript(cp)),
        UCharacter.getPropertyValueName(
            UProperty.INDIC_SYLLABIC_CATEGORY,
            UCharacter.getIntPropertyValue(cp, UProperty.INDIC_SYLLABIC_CATEGORY),
            UProperty.NameChoice.LONG),
        UCharacter.getPropertyValueName(
            UProperty.WORD_BREAK,
            UCharacter.getIntPropertyValue(cp, UProperty.WORD_BREAK),
            UProperty.NameChoice.SHORT));
  }

  static ICUTokenizerConfig withMyanmar(RuleBasedBreakIterator rbbi) {
    return new DefaultICUTokenizerConfig(true, false) {
      @Override
      public RuleBasedBreakIterator getBreakIterator(int script) {
        if (script == UScript.MYANMAR) {
          return (RuleBasedBreakIterator) rbbi.clone();
        }
        return super.getBreakIterator(script);
      }
    };
  }

  /** The rule text the factory would compile (it drops lines that start with '#'). */
  static RuleBasedBreakIterator compile(Path rbbi) throws Exception {
    StringBuilder rules = new StringBuilder();
    for (String line : Files.readAllLines(rbbi, StandardCharsets.UTF_8)) {
      if (!line.startsWith("#")) rules.append(line);
      rules.append('\n');
    }
    return new RuleBasedBreakIterator(rules.toString());
  }

  static RuleBasedBreakIterator load(Path brk) throws Exception {
    try (InputStream in = Files.newInputStream(brk)) {
      return RuleBasedBreakIterator.getInstanceFromCompiledRules(in);
    }
  }

  interface TokFactory {
    Tokenizer create() throws Exception;
  }

  static String dump(Tokenizer t, String text) throws Exception {
    t.setReader(new StringReader(text));
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    OffsetAttribute off = t.addAttribute(OffsetAttribute.class);
    TypeAttribute type = t.addAttribute(TypeAttribute.class);
    boolean[] covered = new boolean[text.length()];
    StringBuilder b = new StringBuilder("[");
    t.reset();
    while (t.incrementToken()) {
      if (b.length() > 1) b.append(", ");
      b.append(term).append(' ').append(type.type()).append(' ');
      b.append(off.startOffset()).append('-').append(off.endOffset());
      for (int i = off.startOffset(); i < off.endOffset(); i++) covered[i] = true;
    }
    t.end();
    t.close();
    b.append(']');
    List<String> lost = new ArrayList<>();
    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i);
      if (Character.isLetterOrDigit(cp) && !covered[i]) {
        lost.add(String.format(Locale.ROOT, "U+%04X", cp));
      }
      i += Character.charCount(cp);
    }
    b.append(lost.isEmpty() ? "  all letters kept" : "  LOST " + lost);
    return b.toString();
  }

  static Analyzer analyzer(TokFactory f) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        try {
          return new TokenStreamComponents(f.create());
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  static void search(String label, TokFactory f) throws Exception {
    String[] docs = {KA + SHAN, KA};
    try (Analyzer a = analyzer(f);
        ByteBuffersDirectory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        for (String d : docs) {
          Document doc = new Document();
          doc.add(new TextField("body", d, Field.Store.YES));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        QueryBuilder qb = new QueryBuilder(a);
        for (String q : new String[] {KA + SHAN, KA, SHAN}) {
          Query bq = qb.createBooleanQuery("body", q);
          Query pq = qb.createPhraseQuery("body", q);
          System.out.printf(
              Locale.ROOT,
              "  %-22s query [%s]: boolean=%s -> %s of 2 | phrase=%s -> %s of 2%n",
              label,
              hex(q),
              bq == null ? "null" : hexQuery(bq.toString()),
              bq == null ? 0 : s.count(bq),
              pq == null ? "null" : hexQuery(pq.toString()),
              pq == null ? 0 : s.count(pq));
        }
      }
    }
  }

  static String hexQuery(String q) {
    StringBuilder b = new StringBuilder();
    q.codePoints()
        .forEach(
            cp -> {
              if (cp < 0x80) b.appendCodePoint(cp);
              else b.append(String.format(Locale.ROOT, "<%04X>", cp));
            });
    return b.toString();
  }

  public static void main(String[] args) throws Exception {
    Path stockRbbi = Path.of(args[0]);
    Path fixARbbi = Path.of(args[1]);
    Path fixBRbbi = Path.of(args[2]);

    System.out.println("== properties (ICU4J " + com.ibm.icu.util.VersionInfo.ICU_VERSION + ")");
    for (int cp : new int[] {0x1000, 0x1075, 0xAA60, 0xA9E6, 0xAA70, 0x00B5}) {
      System.out.println("  " + props(cp));
    }

    Map<String, TokFactory> configs = new LinkedHashMap<>();
    configs.put("StandardTokenizer", StandardTokenizer::new);
    configs.put("default (words)", () -> new ICUTokenizer(new DefaultICUTokenizerConfig(true, true)));
    configs.put(
        "syllable (stock .brk)", () -> new ICUTokenizer(new DefaultICUTokenizerConfig(true, false)));
    RuleBasedBreakIterator stock = compile(stockRbbi);
    RuleBasedBreakIterator fixA = compile(fixARbbi);
    RuleBasedBreakIterator fixB = compile(fixBRbbi);
    configs.put("syllable stock rules*", () -> new ICUTokenizer(withMyanmar(stock)));
    configs.put("syllable fix A rules*", () -> new ICUTokenizer(withMyanmar(fixA)));
    configs.put("syllable fix B rules*", () -> new ICUTokenizer(withMyanmar(fixB)));
    for (int i = 3; i < args.length; i++) {
      String[] kv = args[i].split("=", 2);
      RuleBasedBreakIterator brk = load(Path.of(kv[1]));
      configs.put("syllable " + kv[0], () -> new ICUTokenizer(withMyanmar(brk)));
    }
    // the factory with myanmarAsWords=false, and the rulefiles workaround (fix A and fix B files)
    configs.put("factory false", () -> factory(Map.of("myanmarAsWords", "false"), null));
    configs.put(
        "factory rulefiles A",
        () ->
            factory(
                Map.of("myanmarAsWords", "false", "rulefiles", "Mymr:" + fixARbbi.getFileName()),
                fixARbbi.getParent()));
    configs.put(
        "factory rulefiles B",
        () ->
            factory(
                Map.of("myanmarAsWords", "false", "rulefiles", "Mymr:" + fixBRbbi.getFileName()),
                fixBRbbi.getParent()));

    String[] inputs = {
      KA + SHAN, // KA + Shan reduplication
      KA + KHAMTI, // KA + Khamti reduplication
      "\u1075" + SHAN, // SHAN KA + mark
      "\uAA60" + KHAMTI, // KHAMTI GA + mark
      SHAN, // the mark alone
      KA + SHAN + " " + KA + KHAMTI,
      KAUNG + " µm", // "good" + space + MICRO SIGN + m
      KAUNG + " ab", // control: Latin after Myanmar
      KAUNG + " µµ", // two Common letters in the Myanmar run
      KA + SHAN + SHAN, // mark twice
      "သက်ဝင်လှုပ်ရှားစေပြီး", // upstream testBasics
      KA + "-" + KA, // side observation (not claimed): hyphen is ISC Consonant_Placeholder
    };
    System.out.println("== tokens (term type start-end); * = rules compiled at runtime");
    for (String in : inputs) {
      System.out.println(hex(in) + "   " + in);
      for (Map.Entry<String, TokFactory> e : configs.entrySet()) {
        System.out.printf(Locale.ROOT, "  %-22s %s%n", e.getKey(), dump(e.getValue().create(), in));
      }
    }

    System.out.println("== search: documents [" + hex(KA + SHAN) + "] and [" + hex(KA) + "]");
    for (String label :
        new String[] {"default (words)", "syllable (stock .brk)", "syllable fix A rules*", "syllable fix B rules*"}) {
      search(label, configs.get(label));
    }
  }

  static Tokenizer factory(Map<String, String> args, Path dir) throws Exception {
    ICUTokenizerFactory f = new ICUTokenizerFactory(new HashMap<>(args));
    Path base = dir == null ? Path.of(".") : dir;
    f.inform(new FilesystemResourceLoader(base, Lfv720Probe.class.getClassLoader()));
    return f.create();
  }
}
