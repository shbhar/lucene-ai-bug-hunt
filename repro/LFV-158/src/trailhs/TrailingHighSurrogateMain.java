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
package trailhs;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

/**
 * Version-portable main() (works on 5.x-11.x): StandardTokenizer with default settings on "hello world" + U+D83D
 * (an unpaired high surrogate at the end). Exits 1 if "hello"/"world" are missing or the final offset is not the
 * input length.
 */
public class TrailingHighSurrogateMain {
  public static void main(String[] args) throws Exception {
    String text = "hello world\uD83D";
    StandardTokenizer t = new StandardTokenizer();
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
    boolean ok = got.contains("hello") && got.contains("world") && finalOffset == text.length();
    System.out.println("tokens=" + got + " finalOffset=" + finalOffset + " inputLength=" + text.length()
        + (ok ? " OK" : " BUG: tokens lost before an unpaired trailing high surrogate"));
    System.exit(ok ? 0 : 1);
  }
}
