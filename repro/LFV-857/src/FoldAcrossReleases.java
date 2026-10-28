import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.util.VersionInfo;
import java.util.Locale;
import org.apache.lucene.analysis.icu.ICUFoldingFilter;

/** Prints ICUFoldingFilter.NORMALIZER output for probe strings (same source on every release). */
public class FoldAcrossReleases {
  public static void main(String[] args) {
    String[] probes = {
      "a´b", "a¨b", "a¯b", "a¸b", "a˘b", "a˜b", // spacing accents (NFKC: space + mark)
      "a`b", "a^b", // spacing accents without an NFKC mapping (control)
      "aʰb", "2ᵉ", "Mᵐᵉ", // modifier letters (NFKC: base letter)
      "a‗b", "a﹍b", // underline folding entries
      "aˀb", "ҷ", "ӌ" // superscript glottal stop; Cyrillic che entries
    };
    StringBuilder sb = new StringBuilder();
    sb.append("icu4j=").append(VersionInfo.ICU_VERSION).append(" unicode=").append(UCharacter.getUnicodeVersion());
    for (String p : probes) {
      String f = ICUFoldingFilter.NORMALIZER.normalize(p);
      sb.append("\n  ").append(hex(p)).append(" -> ").append(hex(f)).append("  '").append(f).append("'");
    }
    System.out.println(sb);
  }

  static String hex(String s) {
    StringBuilder sb = new StringBuilder("[");
    s.codePoints().forEach(c -> sb.append(sb.length() > 1 ? " " : "").append(String.format(Locale.ROOT, "%04X", c)));
    return sb.append("]").toString();
  }
}
