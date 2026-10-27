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
import org.apache.lucene.analysis.icu.segmentation.DefaultICUTokenizerConfig;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/** main() version for old releases: exits 1 if U+3005 is dropped with cjkAsWords=false. */
public class IterationMarkMain {
  public static void main(String[] args) throws Exception {
    String in = "\u4EBA\u3005";
    Tokenizer t = new ICUTokenizer(new DefaultICUTokenizerConfig(false, true));
    t.setReader(new StringReader(in));
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    t.reset();
    List<String> out = new ArrayList<>();
    while (t.incrementToken()) out.add(term.toString());
    t.end();
    t.close();
    boolean dropped = String.join("", out).indexOf('\u3005') < 0;
    StringBuilder sb = new StringBuilder();
    for (String s : out) sb.append(String.format("[%s]", s.codePoints().mapToObj(c -> String.format("U+%04X", c)).reduce((a, b) -> a + " " + b).orElse("")));
    System.out.println((dropped ? "DROPPED " : "ok      ") + "U+4EBA U+3005 -> " + sb);
    System.exit(dropped ? 1 : 0);
  }
}
