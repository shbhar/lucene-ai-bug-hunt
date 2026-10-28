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
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.TokenizerFactory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.automaton.RegExp;

/**
 * The class javadoc of SimplePatternSplitTokenizerFactory (line 52) and SimplePatternTokenizerFactory
 * (line 50) shows, "to match tokens delimited by simple whitespace characters", the schema attribute
 * pattern="[ \t\r\n]+" (and "[^ \t\r\n]+"). An XML attribute keeps the backslashes, so the factory
 * receives backslash-t, backslash-r, backslash-n, which RegExp rejects. The RegExp error message
 * also prints the rejected letter as a decimal number.
 */
public class TestSimplePatternJavadocExamples extends BaseTokenStreamTestCase {

  private static Tokenizer build(String spi, String pattern) {
    Map<String, String> args = new HashMap<>();
    args.put("pattern", pattern); // the attribute value exactly as an XML parser returns it
    return TokenizerFactory.forName(spi, args).create();
  }

  public void testSplitExampleSplitsOnWhitespace() throws Exception {
    Tokenizer t = build("simplePatternSplit", "[ \\t\\r\\n]+");
    t.setReader(new StringReader("one\ttwo three\nfour"));
    assertTokenStreamContents(t, new String[] {"one", "two", "three", "four"});
  }

  public void testTokenExampleMatchesNonWhitespace() throws Exception {
    Tokenizer t = build("simplePattern", "[^ \\t\\r\\n]+");
    t.setReader(new StringReader("one\ttwo three\nfour"));
    assertTokenStreamContents(t, new String[] {"one", "two", "three", "four"});
  }

  /** The message should name the rejected escape, e.g. "\q", not its code point ("\113"). */
  public void testInvalidEscapeMessageNamesTheLetter() {
    IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> new RegExp("a\\qb"));
    assertTrue(e.getMessage(), e.getMessage().contains("\\q"));
  }
}
