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
package org.apache.lucene.analysis.miscellaneous;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.automaton.TooComplexToDeterminizeException;

/**
 * ConcatenateGraphFilter documents maxGraphExpansions as "If the tokenStream graph has more than this many possible
 * paths through, then we'll throw TooComplexToDeterminizeException" (also @throws, and the factory javadoc). The
 * value is used both as a silent cap on the number of emitted paths (LimitedFiniteStringsIterator) and as the
 * determinization work limit, so neither direction of the documented contract holds.
 */
public class TestConcatenateGraphMaxGraphExpansions extends LuceneTestCase {

  private static SynonymMap synonyms(String... pairs) throws Exception {
    SynonymMap.Builder b = new SynonymMap.Builder(true);
    for (int i = 0; i < pairs.length; i += 2) {
      b.add(new CharsRef(pairs[i]), new CharsRef(pairs[i + 1]), true);
    }
    return b.build();
  }

  private static List<String> run(String text, SynonymMap map, int maxGraphExpansions) throws Exception {
    Tokenizer t = new WhitespaceTokenizer();
    t.setReader(new StringReader(text));
    List<String> out = new ArrayList<>();
    try (TokenStream f =
        new ConcatenateGraphFilter(new SynonymGraphFilter(t, map, true), true, true, maxGraphExpansions)) {
      TermToBytesRefAttribute att = f.addAttribute(TermToBytesRefAttribute.class);
      f.reset();
      while (f.incrementToken()) {
        out.add(att.getBytesRef().utf8ToString());
      }
      f.end();
    }
    return out;
  }

  /** 4 paths (a|b) (c|d), limit 3: documented to throw; actually emits 3 paths and silently drops one. */
  public void testMorePathsThanLimitThrows() throws Exception {
    SynonymMap map = synonyms("a", "b", "c", "d");
    assertEquals(4, run("a c", map, 4).size()); // precondition: the graph has 4 paths
    expectThrows(TooComplexToDeterminizeException.class, () -> run("a c", map, 3));
  }

  /** 2 paths (wifi|wireless), limit 3: documented not to throw; actually throws. */
  public void testFewerPathsThanLimitDoesNotThrow() throws Exception {
    SynonymMap map = synonyms("wifi", "wireless");
    assertEquals(2, run("wifi network adapter for laptops", map, 100).size()); // 2 paths
    assertEquals(2, run("wifi network adapter for laptops", map, 3).size());
  }
}
