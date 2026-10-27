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

import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.icu.segmentation.DefaultICUTokenizerConfig;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizerConfig;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

/**
 * Version-independent main() repro (compiles and runs unchanged against Lucene 4.6.1 .. main).
 *
 * <p>Tokenizes Japanese words containing U+3005 (IDEOGRAPHIC ITERATION MARK) with the UAX#29 (non
 * dictionary) ICUTokenizer configuration and exits 1 if U+3005 is missing from every token.
 *
 * <p>Configuration used: {@code DefaultICUTokenizerConfig(false, true)} where it exists, else
 * {@code DefaultICUTokenizerConfig(false)}, else (releases before the cjkAsWords option existed) the
 * only configuration there is, {@code DefaultICUTokenizerConfig()}, which is the UAX#29 configuration
 * that cjkAsWords=false was introduced to keep. The configuration actually used is printed.
 */
public class IterationMarkAnyVersion {

  private static final String[] INPUTS = {
    "人々", // people
    "時々", // sometimes
    "様々な人々", // various people
  };

  public static void main(String[] args) throws Exception {
    String[] used = new String[1];
    boolean anyDropped = false;
    for (String in : INPUTS) {
      ICUTokenizerConfig config = uax29Config(used);
      Tokenizer t = newTokenizer(config, new StringReader(in));
      CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
      TypeAttribute type = t.addAttribute(TypeAttribute.class);
      t.reset();
      StringBuilder all = new StringBuilder();
      StringBuilder shown = new StringBuilder();
      while (t.incrementToken()) {
        all.append(term);
        shown.append('[').append(hex(term.toString())).append(' ').append(type.type()).append(']');
      }
      t.end();
      t.close();
      boolean dropped = all.indexOf("々") < 0;
      anyDropped |= dropped;
      System.out.println((dropped ? "DROPPED " : "kept    ") + hex(in) + " -> " + shown);
    }
    System.out.println("config: " + used[0]);
    System.out.println(anyDropped ? "RESULT: U+3005 DROPPED" : "RESULT: U+3005 kept");
    System.exit(anyDropped ? 1 : 0);
  }

  private static ICUTokenizerConfig uax29Config(String[] used) throws Exception {
    Class<DefaultICUTokenizerConfig> c = DefaultICUTokenizerConfig.class;
    try {
      Constructor<DefaultICUTokenizerConfig> k = c.getConstructor(boolean.class, boolean.class);
      used[0] = "DefaultICUTokenizerConfig(cjkAsWords=false, myanmarAsWords=true)";
      return k.newInstance(false, true);
    } catch (NoSuchMethodException e) {
      // fall through
    }
    try {
      Constructor<DefaultICUTokenizerConfig> k = c.getConstructor(boolean.class);
      used[0] = "DefaultICUTokenizerConfig(cjkAsWords=false)";
      return k.newInstance(false);
    } catch (NoSuchMethodException e) {
      // fall through
    }
    used[0] = "DefaultICUTokenizerConfig() (no cjkAsWords option in this version)";
    return c.getConstructor().newInstance();
  }

  private static Tokenizer newTokenizer(ICUTokenizerConfig config, Reader reader) throws Exception {
    try {
      // Lucene 5.0 and later: ICUTokenizer(ICUTokenizerConfig) + setReader(Reader)
      Constructor<ICUTokenizer> k = ICUTokenizer.class.getConstructor(ICUTokenizerConfig.class);
      Tokenizer t = k.newInstance(config);
      Method setReader = Tokenizer.class.getMethod("setReader", Reader.class);
      setReader.invoke(t, reader);
      return t;
    } catch (NoSuchMethodException e) {
      // Lucene 4.x: ICUTokenizer(Reader, ICUTokenizerConfig)
      Constructor<ICUTokenizer> k =
          ICUTokenizer.class.getConstructor(Reader.class, ICUTokenizerConfig.class);
      return k.newInstance(reader, config);
    }
  }

  private static String hex(String s) {
    StringBuilder sb = new StringBuilder();
    s.codePoints()
        .forEach(cp -> sb.append(sb.length() == 0 ? "" : " ").append(String.format("U+%04X", cp)));
    return sb.toString();
  }
}
