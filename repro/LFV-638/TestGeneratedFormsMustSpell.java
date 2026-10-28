package verify.hunspell;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.analysis.hunspell.AffixedWord;
import org.apache.lucene.analysis.hunspell.Dictionary;
import org.apache.lucene.analysis.hunspell.Hunspell;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check (public API only, outside the hunspell package).
 *
 * <p>Suffix class P adds "er" and gives the result the NEEDAFFIX flag Z plus continuation class
 * Q, so "kater" alone is not a word but "katern" is. An unrelated class R (used by another root)
 * also adds "er". Claim: getAllWordForms("kat") returns "kater", which spell() rejects.
 */
public class TestGeneratedFormsMustSpell extends LuceneTestCase {

  private static final String AFF_BASE =
      "SET UTF-8\n"
          + "NEEDAFFIX Z\n"
          + "\n"
          + "SFX P Y 1\n"
          + "SFX P 0 er/QZ .\n"
          + "\n"
          + "SFX Q Y 1\n"
          + "SFX Q 0 n .\n";

  private static final String AFF_UNRELATED_SAME_TEXT = "\nSFX R Y 1\nSFX R 0 er .\n";

  private Hunspell load(String aff, String dic) throws Exception {
    try (Directory dir = newDirectory();
        InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Hunspell(new Dictionary(dir, "dict", a, d));
    }
  }

  private static List<String> words(List<AffixedWord> forms) {
    List<String> out = new ArrayList<>();
    for (AffixedWord w : forms) out.add(w.getWord());
    return out;
  }

  private static TreeSet<String> rejected(Hunspell h, List<AffixedWord> forms) {
    TreeSet<String> bad = new TreeSet<>();
    for (AffixedWord w : forms) {
      // same oracle as upstream TestSpellChecking.checkExpansionGeneratesCorrectWords
      if (!h.spell(w.getWord()) || h.analyzeSimpleWord(w.getWord()).isEmpty()) {
        bad.add(w.getWord());
      }
    }
    return bad;
  }

  /** Control: without the unrelated class R, no rejected form is generated. */
  public void testControlWithoutUnrelatedSameTextClass() throws Exception {
    Hunspell h = load(AFF_BASE, "1\nkat/P\n");
    List<AffixedWord> forms = h.getAllWordForms("kat");
    System.out.println("control getAllWordForms(kat) = " + words(forms));
    assertFalse(h.spell("kater"));
    assertTrue(h.spell("katern"));
    assertEquals(new TreeSet<String>(), rejected(h, forms));
  }

  /** The claim: adding class R (same affix text "er", not on "kat") makes "kater" appear. */
  public void testUnrelatedSameTextClassLeaksPseudoForm() throws Exception {
    Hunspell h = load(AFF_BASE + AFF_UNRELATED_SAME_TEXT, "2\nkat/P\nhund/R\n");
    List<AffixedWord> forms = h.getAllWordForms("kat");
    System.out.println("buggy getAllWordForms(kat) = " + words(forms));
    for (AffixedWord w : forms) {
      System.out.println(
          "  " + w.getWord() + " prefixes=" + w.getPrefixes() + " suffixes=" + w.getSuffixes()
              + " spell=" + h.spell(w.getWord())
              + " analyze=" + h.analyzeSimpleWord(w.getWord()));
    }
    // sanity: dictionary semantics are as intended
    assertFalse("kater must be rejected (NEEDAFFIX continuation)", h.spell("kater"));
    assertTrue(h.spell("katern"));
    assertTrue(h.spell("hunder"));
    assertEquals(
        "getAllWordForms(kat) generated forms that spell() rejects",
        new TreeSet<String>(),
        rejected(h, forms));
  }

  /**
   * Upstream fixture needaffix5.aff (pseudo-suffix "pseudosuf" and pseudo-prefix "pseudopre" carry
   * NEEDAFFIX X; needaffix5.wrong lists "foopseudosuf" and "pseudoprefoo" as wrong), plus two
   * unrelated classes D and E with the same affix texts, used only by another root.
   */
  public void testUpstreamNeedaffix5PlusUnrelatedSameTextClasses() throws Exception {
    String aff =
        "NEEDAFFIX X\n\n"
            + "SFX A Y 2\nSFX A 0 suf/B .\nSFX A 0 pseudosuf/XB .\n\n"
            + "SFX B Y 1\nSFX B 0 bar .\n\n"
            + "PFX C Y 2\nPFX C 0 pre .\nPFX C 0 pseudopre/X .\n\n"
            + "SFX D Y 1\nSFX D 0 pseudosuf .\n\n"
            + "PFX E Y 1\nPFX E 0 pseudopre .\n";
    Hunspell h = load(aff, "2\nfoo/AC\nbaz/DE\n");
    List<AffixedWord> forms = h.getAllWordForms("foo");
    System.out.println("needaffix5+DE getAllWordForms(foo) = " + words(forms));
    assertFalse(h.spell("foopseudosuf"));
    assertFalse(h.spell("pseudoprefoo"));
    assertTrue(h.spell("bazpseudosuf"));
    assertTrue(h.spell("pseudoprebaz"));
    assertEquals(
        "getAllWordForms(foo) generated forms that spell() rejects",
        new TreeSet<String>(),
        rejected(h, forms));
  }

  /** Control: upstream needaffix5 as shipped (no D/E classes) generates no rejected form. */
  public void testControlUpstreamNeedaffix5() throws Exception {
    String aff =
        "NEEDAFFIX X\n\n"
            + "SFX A Y 2\nSFX A 0 suf/B .\nSFX A 0 pseudosuf/XB .\n\n"
            + "SFX B Y 1\nSFX B 0 bar .\n\n"
            + "PFX C Y 2\nPFX C 0 pre .\nPFX C 0 pseudopre/X .\n";
    Hunspell h = load(aff, "1\nfoo/AC\n");
    List<AffixedWord> forms = h.getAllWordForms("foo");
    System.out.println("needaffix5 control getAllWordForms(foo) = " + words(forms));
    assertEquals(new TreeSet<String>(), rejected(h, forms));
  }
}
