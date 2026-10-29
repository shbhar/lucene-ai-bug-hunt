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
package org.apache.lucene.analysis.opennlp;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ClasspathResourceLoader;

public class TestChunkerAfterRemovedFirstSentence extends LuceneTestCase {

  public void testFirstSentenceRemoved() throws Exception {
    List<String> terms = new ArrayList<>();
    try (Analyzer a =
            CustomAnalyzer.builder(new ClasspathResourceLoader(getClass()))
                .withTokenizer(
                    "opennlp",
                    "tokenizerModel",
                    "en-test-tokenizer.bin",
                    "sentenceModel",
                    "en-test-sent.bin")
                .addTokenFilter("opennlpPOS", "posTaggerModel", "en-test-pos-maxent.bin")
                .addTokenFilter("length", "min", "3", "max", "100")
                .addTokenFilter("opennlpChunker", "chunkerModel", "en-test-chunker.bin")
                .build();
        TokenStream ts = a.tokenStream("f", "No. They sent him running.")) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) {
        terms.add(term.toString());
      }
      ts.end();
    }
    // LengthFilter removed all of sentence 0; expected the tokens of sentence 1, actual: none
    assertEquals(List.of("They", "sent", "him", "running"), terms);
  }
}
