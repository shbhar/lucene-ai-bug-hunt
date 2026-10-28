package org.apache.lucene.analysis.hunspell;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.CharsRef;

/**
 * Twofold suffix stripping, using the example of the hunspell(5) manual ("Twofold suffix
 * stripping"): the suffix "able" (class X) has the continuation class Y, whose suffix is "s", so
 * drink/X yields drink, drinkable and drinkables.
 *
 * <p>hunspell(5) defines the second field of an affix class header as "Cross product (permission
 * to combine prefixes and suffixes)". This test varies that field for X and Y. No prefix is
 * involved, so the expected forms are the same for every combination. Uses public API only
 * (Dictionary, Hunspell.spell, Stemmer.stem).
 */
public class TestTwofoldSuffixCrossProductV1 extends LuceneTestCase {

  private static Dictionary dictionary(char crossX, char crossY) throws Exception {
    String aff =
        "SET UTF-8\n"
            + "\n"
            + "SFX Y "
            + crossY
            + " 1\n"
            + "SFX Y 0 s .\n"
            + "\n"
            + "SFX X "
            + crossX
            + " 1\n"
            + "SFX X 0 able/Y .\n";
    String dic = "1\ndrink/X\n";
    try (InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Dictionary(new ByteBuffersDirectory(), "twofold", a, d);
    }
  }

  private static List<String> stems(Dictionary dic, String word) {
    List<String> out = new ArrayList<>();
    for (CharsRef s : new Stemmer(dic).stem(word)) out.add(s.toString());
    return out;
  }

  private static String observe(char crossX, char crossY) throws Exception {
    Dictionary dic = dictionary(crossX, crossY);
    Hunspell h = new Hunspell(dic);
    return "X="
        + crossX
        + " Y="
        + crossY
        + ": spell(drinkable)="
        + h.spell("drinkable")
        + " spell(drinkables)="
        + h.spell("drinkables")
        + " spell(drinks)="
        + h.spell("drinks")
        + " stem(drinkables)="
        + stems(dic, "drinkables");
  }

  private static void check(char crossX, char crossY) throws Exception {
    Dictionary dic = dictionary(crossX, crossY);
    Hunspell h = new Hunspell(dic);
    String obs = observe(crossX, crossY);
    System.out.println("OBS " + obs);
    // single suffix and the negative control behave the same in every variant
    assertTrue(obs, h.spell("drink"));
    assertTrue(obs, h.spell("drinkable"));
    assertFalse(obs, h.spell("drinks")); // "s" is only allowed after "able"
    // twofold suffix: drink + able + s
    assertTrue("twofold suffix not accepted; " + obs, h.spell("drinkables"));
    assertEquals("twofold suffix not stemmed; " + obs, List.of("drink"), stems(dic, "drinkables"));
  }

  /** Control: the manual's own example (both classes Y). Expected to pass. */
  public void testBothCrossProductYes() throws Exception {
    check('Y', 'Y');
  }

  /** Outer (last-applied) suffix class Y has cross product N. */
  public void testOuterSuffixCrossProductNo() throws Exception {
    check('Y', 'N');
  }

  /** Inner (first-applied) suffix class X has cross product N. */
  public void testInnerSuffixCrossProductNo() throws Exception {
    check('N', 'Y');
  }

  /** Both classes have cross product N. */
  public void testBothCrossProductNo() throws Exception {
    check('N', 'N');
  }
}
