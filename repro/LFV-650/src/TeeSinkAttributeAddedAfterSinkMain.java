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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.sinks.TeeSinkTokenFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/** Version-independent main() repro (Lucene 6.0+ API): a sink created before a stemmer is put on its tee. Exits 1 on failure. */
public class TeeSinkAttributeAddedAfterSinkMain {
  static List<String> terms(TokenStream ts) throws Exception {
    CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
    List<String> out = new ArrayList<>();
    ts.reset();
    while (ts.incrementToken()) out.add(t.toString());
    ts.end();
    ts.close();
    return out;
  }

  public static void main(String[] args) throws Exception {
    Tokenizer tok = new WhitespaceTokenizer();
    tok.setReader(new StringReader("quick foxes jumped"));
    TeeSinkTokenFilter tee = new TeeSinkTokenFilter(tok);
    TokenStream sink = tee.newSinkTokenStream();
    TokenStream stemmed = new PorterStemFilter(tee);
    System.out.println("tee chain (stemmed): " + terms(stemmed));
    try {
      System.out.println("sink:                " + terms(sink));
    } catch (IllegalArgumentException e) {
      System.out.println("FAIL: sink threw " + e);
      System.exit(1);
    }
    System.out.println("OK");
  }
}
