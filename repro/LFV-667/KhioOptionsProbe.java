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
package org.apache.lucene.analysis.bn;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Prints, for a few words, the BengaliNormalizationFilter output and the BengaliAnalyzer terms, so
 * the stock code and each fix option can be compared (run it with and without a patched
 * BengaliNormalizer on the classpath). Public API only.
 */
public class KhioOptionsProbe {
  static final String[] WORDS = {
    "ক্ষমা", "খমা", "পরীক্ষা", "শিক্ষা", "বক্ষ", "বক্স", "ক্িমা"
  };

  static String hex(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      if (i > 0) b.append(' ');
      b.append(String.format("%04X", (int) s.charAt(i)));
    }
    return b.toString();
  }

  static String filter(String in) throws IOException {
    Tokenizer t = new KeywordTokenizer();
    t.setReader(new StringReader(in));
    try (TokenStream ts = new BengaliNormalizationFilter(t)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      ts.incrementToken();
      String s = term.toString();
      ts.end();
      return s;
    }
  }

  static List<String> terms(Analyzer a, String in) throws IOException {
    List<String> out = new ArrayList<>();
    try (TokenStream ts = a.tokenStream("f", in)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) out.add(term.toString());
      ts.end();
    }
    return out;
  }

  public static void main(String[] args) throws IOException {
    String label = args.length > 0 ? args[0] : "run";
    try (Analyzer a = new BengaliAnalyzer()) {
      for (String w : WORDS) {
        String f = filter(w);
        List<String> t = terms(a, w);
        System.out.println(
            label + " " + w + " [" + hex(w) + "] filter=" + f + " [" + hex(f) + "] analyzer=" + t);
      }
    }
  }
}
