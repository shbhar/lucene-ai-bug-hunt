package org.apache.lucene.analysis.hunspell;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTwofoldSuffixCrossProduct extends LuceneTestCase {

  public void testTwofoldSuffixWhenOuterClassHasCrossProductN() throws Exception {
    String aff = "SFX X Y 1\nSFX X 0 able/Y .\nSFX Y N 1\nSFX Y 0 s .\n";
    Dictionary dictionary =
        new Dictionary(
            new ByteBuffersDirectory(),
            "dict",
            new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
            new ByteArrayInputStream("1\ndrink/X\n".getBytes(StandardCharsets.UTF_8)));
    // cross product only governs prefix+suffix: expected drink+able+s valid, actual rejected
    assertTrue(
        "expected spell(\"drinkables\") to be true, was false",
        new Hunspell(dictionary).spell("drinkables"));
  }
}
