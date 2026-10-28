package org.apache.lucene.analysis.hunspell;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Verifier probes: the strip-overlap paths (k>m, lines 113/120) and a reversed "range". */
public class TestDashStripVariants extends LuceneTestCase {
  private static Hunspell load(String aff, String dic) throws Exception {
    try (InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Hunspell(new Dictionary(a, List.of(d), false, SortingStrategy.inMemory()));
    }
  }

  /** Suffix, condition longer than strip (line 120): stem part "a", strip part "[^a-c]". */
  public void testSuffixLongerCondition() throws Exception {
    Hunspell h = load("SET UTF-8\nSFX A Y 1\nSFX A b y a[^a-c]\n", "1\naab/A\n");
    assertTrue("aay", h.spell("aay"));
  }

  /** Prefix, condition longer than strip (line 113): strip part "[^a-c]", stem part "a". */
  public void testPrefixLongerCondition() throws Exception {
    Hunspell h = load("SET UTF-8\nPFX B Y 1\nPFX B b y [^a-c]a\n", "1\nbaa/B\n");
    assertTrue("yaa", h.spell("yaa"));
  }

  /** Reversed pseudo-range: java.util.regex throws, Lucene folds to ALWAYS_FALSE. */
  public void testReversedRangeSuffix() throws Exception {
    Hunspell h = load("SET UTF-8\nSFX A Y 1\nSFX A c y [c-a]\n", "1\nac/A\n");
    assertTrue("ay", h.spell("ay"));
  }

  /** Control without dash: same shapes pass. */
  public void testControlNoDash() throws Exception {
    Hunspell h = load("SET UTF-8\nSFX A Y 1\nSFX A b y a[^ac]\n", "1\naab/A\n");
    assertTrue(h.spell("aay"));
  }
}
