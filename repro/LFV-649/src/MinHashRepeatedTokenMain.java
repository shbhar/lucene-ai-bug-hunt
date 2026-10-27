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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.minhash.MinHashFilterFactory;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Version-independent main() repro (Lucene 6.2+ API only): one hash, one bucket, keep the 2 lowest
 * values. "b a" and "b a a" have the same token set, so they must give the same min hashes. Exits 1
 * when they differ.
 */
public class MinHashRepeatedTokenMain {
  static List<String> minHashes(String text) throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("hashCount", "1");
    args.put("bucketCount", "1");
    args.put("hashSetSize", "2");
    args.put("withRotation", "false");
    Tokenizer tok = new WhitespaceTokenizer();
    tok.setReader(new StringReader(text));
    TokenStream ts = new MinHashFilterFactory(args).create(tok);
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    List<String> out = new ArrayList<>();
    ts.reset();
    while (ts.incrementToken()) {
      StringBuilder hex = new StringBuilder();
      for (int i = 0; i < term.length(); i++) hex.append(String.format("%04x", (int) term.charAt(i)));
      out.add(hex.toString());
    }
    ts.end();
    ts.close();
    return out;
  }

  public static void main(String[] args) throws Exception {
    List<String> expected = minHashes("b a");
    List<String> actual = minHashes("b a a");
    System.out.println("min hashes of 'b a'   : " + expected);
    System.out.println("min hashes of 'b a a' : " + actual);
    if (!expected.equals(actual)) {
      System.out.println("FAIL: same token set {a, b}, different min-hash output");
      System.exit(1);
    }
    System.out.println("OK");
  }
}
