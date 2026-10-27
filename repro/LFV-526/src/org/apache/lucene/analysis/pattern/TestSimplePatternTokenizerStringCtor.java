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
package org.apache.lucene.analysis.pattern;

import java.io.StringReader;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;

/**
 * SimplePatternTokenizer's String constructors are documented as "See {@link RegExp} for the
 * accepted syntax", but they pass the (possibly non-deterministic) automaton of the regexp straight
 * to the Automaton constructor, which rejects it, and they ignore determinizeWorkLimit. The sibling
 * SimplePatternSplitTokenizer and both factories determinize first.
 */
public class TestSimplePatternTokenizerStringCtor extends BaseTokenStreamTestCase {

  public void testAlternationWithCommonPrefix() throws Exception {
    // a legal RegExp whose automaton is not deterministic (two branches start with 'a')
    assertFalse(new RegExp("ab|ac").toAutomaton().isDeterministic());

    // the sibling split tokenizer accepts the same regexp through its String constructor
    Tokenizer split = new SimplePatternSplitTokenizer("ab|ac");
    split.setReader(new StringReader("xabyacz"));
    assertTokenStreamContents(split, new String[] {"x", "y", "z"});

    Tokenizer t = new SimplePatternTokenizer("ab|ac");
    t.setReader(new StringReader("ab ac ad"));
    assertTokenStreamContents(t, new String[] {"ab", "ac"}, new int[] {0, 3}, new int[] {2, 5});
  }

  public void testRealisticPattern() throws Exception {
    // words, or words followed by digits ("abc", "abc123")
    Tokenizer t =
        new SimplePatternTokenizer(
            Tokenizer.DEFAULT_TOKEN_ATTRIBUTE_FACTORY,
            "[a-z]+|[a-z]+[0-9]+",
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
    t.setReader(new StringReader("abc abc123"));
    assertTokenStreamContents(t, new String[] {"abc", "abc123"});
  }
}
