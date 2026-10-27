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
package demo;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/** main() version (no test framework) for old releases: exits 1 if a Hangul syllable is dropped. */
public class HangulExtendFormatMain {
  static List<String> terms(String text) throws Exception {
    Tokenizer t = new ICUTokenizer();
    t.setReader(new StringReader(text));
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    t.reset();
    List<String> out = new ArrayList<>();
    while (t.incrementToken()) out.add(term.toString());
    t.end();
    t.close();
    return out;
  }

  public static void main(String[] args) throws Exception {
    String[] inputs = {"\uD55C\uAD6D\u00AD\uC5B4", "\uD55C\u0336\uAD6D\u0336\uC5B4\u0336", "\uD55C\uAD6D\uC5B4\uFEFF"};
    boolean bad = false;
    for (String in : inputs) {
      List<String> toks = terms(in);
      String all = String.join("", toks);
      boolean dropped = false;
      for (char c : in.toCharArray()) if (c >= '\uAC00' && c <= '\uD7A3' && all.indexOf(c) < 0) dropped = true;
      System.out.println((dropped ? "DROPPED " : "ok      ") + esc(in) + " -> " + esc(toks.toString()));
      bad |= dropped;
    }
    System.exit(bad ? 1 : 0);
  }

  static String esc(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) sb.append(c < 0x80 ? String.valueOf(c) : String.format("\\u%04X", (int) c));
    return sb.toString();
  }
}
