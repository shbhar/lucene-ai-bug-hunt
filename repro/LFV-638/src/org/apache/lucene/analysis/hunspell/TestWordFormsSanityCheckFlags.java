package org.apache.lucene.analysis.hunspell;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * getAllWordForms returns a word that the spell-checker rejects: the form "foos" is made by a suffix
 * whose continuation class has NEEDAFFIX (it needs another affix), and the generator's sanity check
 * (canStemToOriginal) accepts it because a different suffix class with the same text "s", which the
 * root does not have, also strips "foos" to "foo". Public API only.
 */
public class TestWordFormsSanityCheckFlags extends LuceneTestCase {

  private static Hunspell load(String aff, String dic) throws Exception {
    Dictionary d =
        new Dictionary(
            new ByteBuffersDirectory(),
            "dict",
            new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
            new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8)));
    return new Hunspell(d);
  }

  public void testEveryGeneratedFormIsAccepted() throws Exception {
    String aff =
        """
        SET UTF-8
        NEEDAFFIX X

        SFX A Y 1
        SFX A 0 s/XB .

        SFX B Y 1
        SFX B 0 en .

        SFX C Y 1
        SFX C 0 s .
        """;
    Hunspell h = load(aff, "2\nfoo/A\nbar/C\n");
    assertFalse("foos needs another affix after the pseudo-suffix A", h.spell("foos"));
    assertTrue(h.spell("foosen"));
    assertTrue(h.spell("bars"));

    List<String> misspelled = new ArrayList<>();
    for (AffixedWord w : h.getAllWordForms("foo")) {
      if (!h.spell(w.getWord())) misspelled.add(w.getWord());
    }
    // TestSpellChecking.checkExpansionGeneratesCorrectWords: "Misspelled words generated"
    assertEquals("generated forms that spell() rejects", List.of(), misspelled);
  }
}
