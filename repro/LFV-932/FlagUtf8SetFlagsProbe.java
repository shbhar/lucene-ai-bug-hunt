package org.apache.lucene.analysis.hunspell;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.IntsRef;

/**
 * Loads the repro dictionary (FLAG UTF-8; SFX á adds "a", SFX â adds "e"; kot/á, las/â) under each
 * SET value and prints, as code points, the flag stored for each suffix rule and the flags stored
 * for each word, then the spell() and stem() verdicts. main() exits 0; it only prints.
 */
public class FlagUtf8SetFlagsProbe {
  static final String[] SETS = {
    "(none)", "UTF-8", "ISO8859-1", "ISO8859-2", "ISO8859-15", "ISO8859-5", "KOI8-R",
    "microsoft-cp1251", "ISO8859-14", "ISO8859-3", "ISO8859-6", "ISO8859-8"
  };

  static String cps(char[] cs) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < cs.length; i++) {
      if (i > 0) sb.append(' ');
      sb.append(String.format("U+%04X", (int) cs[i]));
    }
    return sb.append(']').toString();
  }

  static Dictionary load(String set) throws Exception {
    ByteArrayOutputStream aff = new ByteArrayOutputStream();
    if (!set.equals("(none)")) aff.writeBytes(("SET " + set + "\n").getBytes(US_ASCII));
    aff.writeBytes("FLAG UTF-8\n".getBytes(US_ASCII));
    aff.writeBytes("SFX á Y 1\nSFX á 0 a .\nSFX â Y 1\nSFX â 0 e .\n".getBytes(UTF_8));
    byte[] dic = "2\nkot/á\nlas/â\n".getBytes(UTF_8);
    return new Dictionary(
        new ByteBuffersDirectory(),
        "probe",
        new ByteArrayInputStream(aff.toByteArray()),
        new ByteArrayInputStream(dic));
  }

  static char suffixFlag(Dictionary d, String suffix) {
    IntsRef ids = d.lookupSuffix(suffix.toCharArray());
    return d.affixData(ids.ints[ids.offset], Dictionary.AFFIX_FLAG);
  }

  static char[] wordFlags(Dictionary d, String word) {
    IntsRef ords = d.lookupWord(word.toCharArray(), 0, word.length());
    return d.flagLookup.getFlags(ords.ints[ords.offset]);
  }

  public static void main(String[] args) throws Exception {
    for (String set : SETS) {
      Dictionary d;
      try {
        d = load(set);
      } catch (Exception e) {
        System.out.println("SET " + set + ": LOAD FAILED " + e);
        continue;
      }
      Hunspell h = new Hunspell(d);
      Stemmer s = new Stemmer(d);
      StringBuilder sb = new StringBuilder("SET " + set + ":");
      sb.append(" rule(-a)=").append(cps(new char[] {suffixFlag(d, "a")}));
      sb.append(" rule(-e)=").append(cps(new char[] {suffixFlag(d, "e")}));
      sb.append(" kot=").append(cps(wordFlags(d, "kot")));
      sb.append(" las=").append(cps(wordFlags(d, "las")));
      for (String w : new String[] {"kota", "kote", "lase", "lasa"}) {
        sb.append(' ').append(w).append('=').append(h.spell(w) ? "ok" : "--");
      }
      sb.append(" stem(kote)=").append(s.stem("kote"));
      System.out.println(sb);
    }
  }
}
