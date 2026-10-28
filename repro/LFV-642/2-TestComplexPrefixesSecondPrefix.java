package org.apache.lucene.analysis.hunspell;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * COMPLEXPREFIXES (twofold prefix stripping): the outer prefix "re" (class A) may only follow an inner
 * prefix rule whose continuation class contains A ("dis/A"), as for twofold suffixes. The Stemmer only
 * checks that some prefix rule anywhere carries A, so "reunfoo" (inner rule "un" without A) is accepted.
 * Public API only.
 */
public class TestComplexPrefixesSecondPrefix extends LuceneTestCase {

  private static Hunspell load(String aff, String dic) throws Exception {
    Dictionary d =
        new Dictionary(
            new ByteBuffersDirectory(),
            "dict",
            new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
            new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8)));
    return new Hunspell(d);
  }

  public void testSecondPrefixNeedsContinuationOfThisRule() throws Exception {
    String aff =
        """
        SET UTF-8
        COMPLEXPREFIXES

        PFX A Y 1
        PFX A 0 re .

        PFX C Y 2
        PFX C 0 un .
        PFX C 0 dis/A .
        """;
    Hunspell h = load(aff, "1\nfoo/C\n");
    assertTrue(h.spell("unfoo"));
    assertTrue(h.spell("disfoo"));
    assertTrue(h.spell("redisfoo")); // dis carries A
    assertFalse(h.spell("refoo")); // A is not on the root
    // the twofold-suffix analogue (a second suffix after a rule without its flag) is rejected
    assertFalse("un does not carry A, so re cannot follow it", h.spell("reunfoo"));
  }
}
