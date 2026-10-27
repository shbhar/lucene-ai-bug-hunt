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
package maxlen1;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

/**
 * Version-portable main() (core API only, works on 7.x-11.x): StandardTokenizer with setMaxTokenLength(1) on
 * "ab" + U+10400 + " cd". Exits 1 if the tokens after the supplementary character are missing or the final offset
 * is not the input length, 0 otherwise.
 */
public class MaxTokenLengthOneMain {
  public static void main(String[] args) throws Exception {
    String text = "ab 𐐀 cd";
    StandardTokenizer t = new StandardTokenizer();
    t.setMaxTokenLength(1);
    t.setReader(new StringReader(text));
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    OffsetAttribute off = t.addAttribute(OffsetAttribute.class);
    List<String> got = new ArrayList<>();
    t.reset();
    while (t.incrementToken()) {
      got.add(term.toString());
    }
    t.end();
    int finalOffset = off.endOffset();
    t.close();
    boolean ok = got.contains("c") && got.contains("d") && finalOffset == text.length();
    System.out.println("tokens=" + got + " finalOffset=" + finalOffset + " inputLength=" + text.length()
        + (ok ? " OK" : " BUG: text after the supplementary character was dropped"));
    System.exit(ok ? 0 : 1);
  }
}
