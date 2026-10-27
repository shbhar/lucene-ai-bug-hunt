package org.apache.lucene.analysis.hunspell;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Twofold suffixes (a suffix whose continuation class allows a second suffix) are rejected when one of
 * the two suffix classes has cross product N, although cross product is documented as the
 * "permission to combine prefixes and suffixes" and no prefix is involved. Public API only.
 */
public class TestTwofoldSuffixCrossProductSpellOnly extends LuceneTestCase {

  private static Hunspell load(String aff, String dic) throws Exception {
    Dictionary d =
        new Dictionary(
            new ByteBuffersDirectory(),
            "dict",
            new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
            new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8)));
    return new Hunspell(d);
  }

  private static String aff(char crossProductOfEndings) {
    // superlative suffix "st" whose continuation class E allows the adjective endings
    return """
        SET UTF-8

        SFX S Y 1
        SFX S 0 st/E .

        SFX E %s 2
        SFX E 0 en .
        SFX E 0 e .
        """
        .formatted(crossProductOfEndings);
  }

  public void testTwofoldSuffixWithoutCrossProduct() throws Exception {
    // Control: with cross product Y on both classes, the twofold forms are accepted.
    Hunspell y = load(aff('Y'), "1\nraich/SE\n");
    assertTrue(y.spell("raichsten"));
    assertTrue(y.spell("raichste"));

    // The same dictionary with cross product N on the endings class: no prefix exists at all.
    Hunspell n = load(aff('N'), "1\nraich/SE\n");
    assertTrue(n.spell("raich"));
    assertTrue(n.spell("raichst"));
    assertTrue(n.spell("raichen"));
    assertTrue("twofold suffix raich+st+en", n.spell("raichsten"));
    assertTrue("twofold suffix raich+st+e", n.spell("raichste"));

  }
}
