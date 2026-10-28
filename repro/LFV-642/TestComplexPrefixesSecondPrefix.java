/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.analysis.hunspell;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * COMPLEXPREFIXES (twofold prefix stripping): the outer prefix "re" (flag A) may only precede an
 * inner prefix rule whose continuation class contains A. "dis/A" allows it and "un" does not, so
 * "redisfoo" is a word and "reunfoo" is not (native Hunspell and Lucene 9.4.2 agree).
 */
public class TestComplexPrefixesSecondPrefix extends BaseTokenStreamTestCase {

  private static Dictionary dictionary() throws Exception {
    String aff =
        "SET UTF-8\n"
            + "COMPLEXPREFIXES\n"
            + "\n"
            + "PFX A Y 1\n"
            + "PFX A 0 re .\n"
            + "\n"
            + "PFX C Y 2\n"
            + "PFX C 0 un .\n"
            + "PFX C 0 dis/A .\n";
    return new Dictionary(
        new ByteBuffersDirectory(),
        "dict",
        new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream("1\nfoo/C\n".getBytes(StandardCharsets.UTF_8)));
  }

  public void testSecondPrefixNeedsContinuationOfInnerRule() throws Exception {
    Hunspell h = new Hunspell(dictionary());
    // controls: one prefix, the licensed chain, and "re" directly on the root
    assertTrue(h.spell("unfoo"));
    assertTrue(h.spell("disfoo"));
    assertTrue(h.spell("redisfoo")); // dis carries A
    assertFalse(h.spell("refoo")); // foo does not carry A
    // un does not carry A, so re cannot precede it
    assertEquals(
        "spell=false roots=[]", "spell=" + h.spell("reunfoo") + " roots=" + h.getRoots("reunfoo"));
  }

  public void testStemFilterKeepsReunfooUnstemmed() throws Exception {
    HunspellStemFilter filter =
        new HunspellStemFilter(whitespaceMockTokenizer("redisfoo reunfoo"), dictionary());
    assertTokenStreamContents(filter, new String[] {"foo", "reunfoo"});
  }
}
