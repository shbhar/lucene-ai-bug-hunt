package org.apache.lucene.analysis.hunspell;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A suffix that a prefix allows through its continuation class ("PFX U 0 kilo/S") is accepted by the
 * spell-checker and analysed as a form of the root, but getAllWordForms never generates it.
 * Public API only.
 */
public class TestWordFormsPrefixEnabledSuffix extends LuceneTestCase {

  private static Hunspell load(String aff, String dic) throws Exception {
    Dictionary d =
        new Dictionary(
            new ByteBuffersDirectory(),
            "dict",
            new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
            new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8)));
    return new Hunspell(d);
  }

  public void testSuffixEnabledByPrefixContinuationIsGenerated() throws Exception {
    // A unit-prefix class: the empty prefix and "kilo", both allowing the plural suffix S.
    String aff =
        """
        SET UTF-8

        PFX U Y 2
        PFX U 0 0/S .
        PFX U 0 kilo/S .

        SFX S Y 1
        SFX S 0 s .
        """;
    Hunspell h = load(aff, "1\nampere/U\n");

    // The spell-checker accepts the plural forms and analyses them as forms of ampere/U.
    for (String w : new String[] {"ampere", "amperes", "kiloampere", "kiloamperes"}) {
      assertTrue(w, h.spell(w));
      assertTrue(
          w + " analysed as a form of ampere/U",
          h.analyzeSimpleWord(w).stream().anyMatch(a -> a.getDictEntry().getStem().equals("ampere")));
    }

    Set<String> forms = new TreeSet<>();
    for (AffixedWord w : h.getAllWordForms("ampere")) forms.add(w.getWord());
    // "Generate all word forms for all dictionary entries with the given root word."
    assertEquals(Set.of("ampere", "amperes", "kiloampere", "kiloamperes"), forms);
  }
}
