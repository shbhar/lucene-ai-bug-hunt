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
package org.apache.lucene.analysis.compound;

import java.io.StringReader;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestCompoundPackageDoc extends BaseTokenStreamTestCase {

  // Expected: the package overview's three examples (verbatim) compile; actual: 5 javac errors.
  public void testExamplesCompile() throws Exception {
    String source =
        """
        import java.io.*;
        import org.apache.lucene.analysis.compound.*;
        import org.apache.lucene.analysis.compound.hyphenation.HyphenationTree;
        import org.apache.lucene.analysis.core.WhitespaceTokenizer;
        import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
        class Example {
          public void testHyphenationCompoundWordsDE() throws Exception {
            String[] dict = { "Rind", "Fleisch", "Draht", "Schere", "Gesetz",
                "Aufgabe", "Überwachung" };

            Reader reader = new FileReader("de_DR.xml");

            HyphenationTree hyphenator = HyphenationCompoundWordTokenFilter
                .getHyphenationTree(reader);

            HyphenationCompoundWordTokenFilter tf = new HyphenationCompoundWordTokenFilter(
                new WhitespaceTokenizer(new StringReader(
                    "Rindfleischüberwachungsgesetz Drahtschere abba")), hyphenator,
                dict, CompoundWordTokenFilterBase.DEFAULT_MIN_WORD_SIZE,
                CompoundWordTokenFilterBase.DEFAULT_MIN_SUBWORD_SIZE,
                CompoundWordTokenFilterBase.DEFAULT_MAX_SUBWORD_SIZE, false);

            CharTermAttribute t = tf.addAttribute(CharTermAttribute.class);
            while (tf.incrementToken()) {
               System.out.println(t);
            }
          }

          public void testHyphenationCompoundWordsWithoutDictionaryDE() throws Exception {
            Reader reader = new FileReader("de_DR.xml");

            HyphenationTree hyphenator = HyphenationCompoundWordTokenFilter
                .getHyphenationTree(reader);

            HyphenationCompoundWordTokenFilter tf = new HyphenationCompoundWordTokenFilter(
                new WhitespaceTokenizer(new StringReader(
                    "Rindfleischüberwachungsgesetz Drahtschere abba")), hyphenator);

            CharTermAttribute t = tf.addAttribute(CharTermAttribute.class);
            while (tf.incrementToken()) {
               System.out.println(t);
            }
          }

          public void testDumbCompoundWordsSE() throws Exception {
            String[] dict = { "Bil", "Dörr", "Motor", "Tak", "Borr", "Slag", "Hammar",
                "Pelar", "Glas", "Ögon", "Fodral", "Bas", "Fiol", "Makare", "Gesäll",
                "Sko", "Vind", "Rute", "Torkare", "Blad" };

            DictionaryCompoundWordTokenFilter tf = new DictionaryCompoundWordTokenFilter(
                new WhitespaceTokenizer(
                    new StringReader(
                        "Bildörr Bilmotor Biltak Slagborr Hammarborr Pelarborr Glasögonfodral Basfiolsfodral Basfiolsfodralmakaregesäll Skomakare Vindrutetorkare Vindrutetorkarblad abba")),
                dict);
            CharTermAttribute t = tf.addAttribute(CharTermAttribute.class);
            while (tf.incrementToken()) {
               System.out.println(t);
            }
          }
        }
        """;
    JavaFileObject unit =
        new SimpleJavaFileObject(URI.create("string:///Example.java"), JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
          }
        };
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    List<String> options =
        List.of(
            "-cp", System.getProperty("java.class.path"), "-d", createTempDir().toString(), "-proc:none");
    boolean ok =
        ToolProvider.getSystemJavaCompiler()
            .getTask(null, null, diagnostics, options, null, List.of(unit))
            .call();
    assertTrue(
        diagnostics.getDiagnostics().stream()
            .map(d -> "line " + d.getLineNumber() + ": " + d.getMessage(Locale.ROOT))
            .collect(Collectors.joining("\n")),
        ok);
  }

  // Expected (page table): Draht 0-5, schere 5-11; actual: both parts get the whole word's 0-11.
  public void testTableOffsets() throws Exception {
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader("Drahtschere"));
    CharArraySet dict = new CharArraySet(List.of("Draht", "schere"), true);
    assertTokenStreamContents(
        new DictionaryCompoundWordTokenFilter(tokenizer, dict),
        new String[] {"Drahtschere", "Draht", "schere"},
        new int[] {0, 0, 5},
        new int[] {11, 5, 11});
  }
}
