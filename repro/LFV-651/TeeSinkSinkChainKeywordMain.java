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
import org.apache.lucene.analysis.miscellaneous.KeywordMarkerFilter;
import org.apache.lucene.analysis.sinks.TeeSinkTokenFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Version-independent main() repro (Lucene 5.0+ API): a keyword marker plus stemmer on a sink. The
 * marker is the stock KeywordMarkerFilter base class (as SetKeywordMarkerFilter), protecting
 * "quick". Exits 1 on failure.
 */
public class TeeSinkSinkChainKeywordMain {
  static final class ProtectQuick extends KeywordMarkerFilter {
    private final CharTermAttribute term = addAttribute(CharTermAttribute.class);

    ProtectQuick(TokenStream in) {
      super(in);
    }

    @Override
    protected boolean isKeyword() {
      return term.toString().equals("quick");
    }
  }

  static List<String> terms(TokenStream ts) throws Exception {
    CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
    List<String> out = new ArrayList<>();
    ts.reset();
    while (ts.incrementToken()) out.add(t.toString());
    ts.end();
    ts.close();
    return out;
  }

  static Tokenizer tokenizer() {
    Tokenizer tok = new WhitespaceTokenizer();
    tok.setReader(new StringReader("the quick foxes jumped"));
    return tok;
  }

  public static void main(String[] args) throws Exception {
    List<String> direct = terms(new PorterStemFilter(new ProtectQuick(tokenizer())));
    TeeSinkTokenFilter tee = new TeeSinkTokenFilter(tokenizer());
    TokenStream sinkChain = new PorterStemFilter(new ProtectQuick(tee.newSinkTokenStream()));
    List<String> plain = terms(tee);
    List<String> viaSink = terms(sinkChain);
    System.out.println("same chain on the tokenizer: " + direct);
    System.out.println("tee (unstemmed field):       " + plain);
    System.out.println("same chain on the sink:      " + viaSink);
    if (!viaSink.equals(direct)) {
      System.out.println("FAIL: the sink chain differs (tokens after the keyword stay keywords)");
      System.exit(1);
    }
    System.out.println("OK");
  }
}
