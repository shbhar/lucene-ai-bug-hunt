package demo;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.lang.UScript;
import com.ibm.icu.text.RuleBasedBreakIterator;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.VersionInfo;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.cjk.CJKBigramFilter;
import org.apache.lucene.analysis.icu.segmentation.DefaultICUTokenizerConfig;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizerConfig;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

/**
 * Token dump for the brief: stock cjkAsWords=false versus three candidate rule fixes, compiled at
 * runtime from main's Default.rbbi (the path is the first argument). Public API only: a
 * DefaultICUTokenizerConfig subclass that returns the compiled rules, as ICUTokenizerFactory does
 * for its rulefiles option.
 */
public class Lfv653Probe {
  static final String STOCK_LINE = "$Ideographic $ExFm* {400};";

  public static void main(String[] args) throws Exception {
    String rbbi = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
    if (!rbbi.contains(STOCK_LINE)) throw new AssertionError("status rule not found");
    Map<String, String> variants = new LinkedHashMap<>();
    variants.put("main-rules", rbbi);
    variants.put("fix-A-han-400", rbbi.replace(STOCK_LINE, "[$Ideographic $Han] $ExFm* {400};"));
    variants.put(
        "fix-N-hanletter-400",
        rbbi.replace(STOCK_LINE, "[$Ideographic [$Han & $ALetter]] $ExFm* {400};"));
    variants.put(
        "fix-B-hanletter-200",
        rbbi.replace(STOCK_LINE, STOCK_LINE + "\n[$Han & $ALetter] $ExFm* {200};"));

    System.out.println("ICU4J " + VersionInfo.ICU_VERSION + ", Unicode " + UCharacter.getUnicodeVersion());
    for (int cp : new int[] {0x3005, 0x303B, 0x16FE3, 0x4EBA}) {
      System.out.printf(
          "U+%04X gc=%s sc=%s Ideographic=%s Word_Break=%s%n",
          cp,
          UCharacter.getPropertyValueName(
              UProperty.GENERAL_CATEGORY, UCharacter.getType(cp), UProperty.NameChoice.SHORT),
          UScript.getShortName(UScript.getScript(cp)),
          UCharacter.hasBinaryProperty(cp, UProperty.IDEOGRAPHIC),
          UCharacter.getPropertyValueName(
              UProperty.WORD_BREAK,
              UCharacter.getIntPropertyValue(cp, UProperty.WORD_BREAK),
              UProperty.NameChoice.LONG));
    }

    String[] inputs = {"人々", "時々", "様々な人々", "佐々木さん", "人〻", "人𖿣", "人人", "々"};
    System.out.println();
    System.out.println("== reference tokenizers (stock jars)");
    for (String in : inputs) {
      System.out.println(in);
      System.out.println("  StandardTokenizer        " + dump(new StandardTokenizer(), in));
      System.out.println("  ICUTokenizer() default   " + dump(new ICUTokenizer(), in));
      System.out.println(
          "  cjkAsWords=false (stock) "
              + dump(new ICUTokenizer(new DefaultICUTokenizerConfig(false, true)), in));
      Tokenizer t = new ICUTokenizer(new DefaultICUTokenizerConfig(false, true));
      System.out.println("  stock + CJKBigramFilter  " + dump(t, new CJKBigramFilter(t), in));
    }

    Map<String, RuleBasedBreakIterator> compiled = new LinkedHashMap<>();
    for (Map.Entry<String, String> v : variants.entrySet()) {
      compiled.put(v.getKey(), new RuleBasedBreakIterator(v.getValue()));
    }
    // optional further arguments: compiled .brk files (for example the one regenerateRbbi wrote)
    for (int i = 1; i < args.length; i++) {
      try (var in = Files.newInputStream(Path.of(args[i]))) {
        compiled.put(
            "compiled file " + Path.of(args[i]).getFileName(),
            RuleBasedBreakIterator.getInstanceFromCompiledRules(in));
      }
    }
    UnicodeSet hanNotIdeo = new UnicodeSet("[[:Han:]-[:Ideographic:]]").freeze();
    for (Map.Entry<String, RuleBasedBreakIterator> v : compiled.entrySet()) {
      RuleBasedBreakIterator rules = v.getValue();
      System.out.println();
      System.out.println("== cjkAsWords=false with " + v.getKey());
      for (String in : inputs) {
        Tokenizer t = new ICUTokenizer(config(rules));
        Tokenizer t2 = new ICUTokenizer(config(rules));
        System.out.printf(
            "  %-6s %-50s bigrams %s%n", in, dump(t, in), dump(t2, new CJKBigramFilter(t2), in));
      }
      Map<String, int[]> byWb = new TreeMap<>();
      for (int cp : hanNotIdeo.codePoints()) {
        String wb =
            UCharacter.getPropertyValueName(
                UProperty.WORD_BREAK,
                UCharacter.getIntPropertyValue(cp, UProperty.WORD_BREAK),
                UProperty.NameChoice.LONG);
        String text = "人" + new String(Character.toChars(cp));
        String all = String.join("", terms(new ICUTokenizer(config(rules)), text));
        int[] c = byWb.computeIfAbsent(wb, k -> new int[2]);
        c[1]++;
        if (all.contains(new String(Character.toChars(cp)))) c[0]++;
      }
      StringBuilder sb = new StringBuilder();
      byWb.forEach((k, c) -> sb.append(' ').append(k).append(' ').append(c[0]).append('/').append(c[1]));
      System.out.println(
          "  [[:Han:]-[:Ideographic:]] (" + hanNotIdeo.size() + " code points) kept after 人, by Word_Break:" + sb);
    }
  }

  static ICUTokenizerConfig config(RuleBasedBreakIterator rules) {
    return new DefaultICUTokenizerConfig(false, true) {
      @Override
      public RuleBasedBreakIterator getBreakIterator(int script) {
        if (script == UScript.MYANMAR) {
          return super.getBreakIterator(script);
        }
        return (RuleBasedBreakIterator) rules.clone();
      }
    };
  }

  static List<String> terms(Tokenizer t, String text) throws Exception {
    List<String> out = new ArrayList<>();
    t.setReader(new StringReader(text));
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    t.reset();
    while (t.incrementToken()) out.add(term.toString());
    t.end();
    t.close();
    return out;
  }

  static String dump(Tokenizer t, String text) throws Exception {
    return dump(t, t, text);
  }

  static String dump(Tokenizer source, TokenStream ts, String text) throws Exception {
    source.setReader(new StringReader(text));
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    TypeAttribute type = ts.addAttribute(TypeAttribute.class);
    OffsetAttribute off = ts.addAttribute(OffsetAttribute.class);
    StringBuilder sb = new StringBuilder("[");
    ts.reset();
    while (ts.incrementToken()) {
      if (sb.length() > 1) sb.append(", ");
      sb.append(term).append(' ').append(type.type()).append(' ').append(off.startOffset()).append('-').append(off.endOffset());
    }
    ts.end();
    ts.close();
    return sb.append(']').toString();
  }
}
