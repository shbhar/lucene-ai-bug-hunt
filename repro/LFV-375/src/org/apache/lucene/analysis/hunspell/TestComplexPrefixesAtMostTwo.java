package org.apache.lucene.analysis.hunspell;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestComplexPrefixesAtMostTwo extends LuceneTestCase {

  private static Hunspell hunspell(String aff) throws Exception {
    return new Hunspell(
        new Dictionary(
            new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
            List.of(new ByteArrayInputStream("1\nx/A\n".getBytes(StandardCharsets.UTF_8))),
            false,
            SortingStrategy.inMemory()));
  }

  public void testThirdPrefixRejected() throws Exception {
    Hunspell h =
        hunspell(
            "COMPLEXPREFIXES\nPFX A Y 1\nPFX A 0 a/B .\nPFX B Y 1\nPFX B 0 b/C .\n"
                + "PFX C Y 1\nPFX C 0 c .\n");
    // COMPLEXPREFIXES allows two prefixes, so cbax (c + b + a + x) must be rejected
    assertFalse("expected spell(\"cbax\") == false, got true", h.spell("cbax"));
  }

  public void testZeroLengthPrefixTerminates() throws Exception {
    Hunspell h = hunspell("COMPLEXPREFIXES\nPFX A Y 1\nPFX A 0 0/B .\nPFX B Y 1\nPFX B 0 b .\n");
    try {
      assertFalse("expected spell(\"bbx\") == false, got true", h.spell("bbx"));
    } catch (StackOverflowError e) {
      throw new AssertionError("expected spell(\"bbx\") == false, got StackOverflowError", e);
    }
  }
}
