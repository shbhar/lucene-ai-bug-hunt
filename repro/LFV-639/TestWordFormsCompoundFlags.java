package org.apache.lucene.analysis.hunspell;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * An entry with a COMPOUNDBEGIN/COMPOUNDMIDDLE/COMPOUNDEND flag, or an affix whose continuation class
 * has one, is a normal standalone word form too (only ONLYINCOMPOUND restricts a form to compounds),
 * and the spell-checker accepts it, but getAllWordForms returns nothing for such an entry and skips
 * such affixes. Public API only.
 */
public class TestWordFormsCompoundFlags extends LuceneTestCase {

  private static Hunspell load(String aff, String dic) throws Exception {
    Dictionary d =
        new Dictionary(
            new ByteBuffersDirectory(),
            "dict",
            new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
            new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8)));
    return new Hunspell(d);
  }

  private static Set<String> forms(Hunspell h, String root) {
    Set<String> forms = new TreeSet<>();
    for (AffixedWord w : h.getAllWordForms(root)) forms.add(w.getWord());
    return forms;
  }

  public void testEntryWithCompoundBeginFlag() throws Exception {
    String aff =
        """
        SET UTF-8
        COMPOUNDBEGIN B
        COMPOUNDEND E

        SFX A Y 1
        SFX A 0 s .
        """;
    Hunspell h = load(aff, "2\nfoo/AB\nbar/E\n");
    assertTrue(h.spell("foo"));
    assertTrue(h.spell("foos"));
    assertTrue(h.spell("foobar")); // the compound the flag is for
    assertEquals(Set.of("foo", "foos"), forms(h, "foo"));
  }

  public void testSuffixWithCompoundEndContinuation() throws Exception {
    // Shape of the upstream germancompounding fixture: "SFX C 0 n/WD ." with W = COMPOUNDEND;
    // "Computern" is in that fixture's .good list.
    String aff =
        """
        SET UTF-8
        COMPOUNDBEGIN U
        COMPOUNDEND W

        SFX C Y 1
        SFX C 0 n/W .
        """;
    Hunspell h = load(aff, "1\nComputer/C\n");
    assertTrue(h.spell("Computer"));
    assertTrue(h.spell("Computern"));
    assertEquals(Set.of("Computer", "Computern"), forms(h, "Computer"));
  }
}
