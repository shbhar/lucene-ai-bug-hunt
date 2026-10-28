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
package org.apache.lucene.analysis.icu.segmentation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;
import org.apache.lucene.util.ClasspathResourceLoader;
import org.apache.lucene.util.ResourceLoader;

/**
 * A per-script rule file saved as UTF-8 with a byte order mark (BOM) must tokenize like the same
 * file without the BOM. Each test runs the file without a BOM first (passes), then with one.
 */
public class TestICUTokenizerFactoryRuleFileBom extends BaseTokenStreamTestCase {

  /** The rule files next to this test (as used by TestICUTokenizerFactory), maybe behind a BOM. */
  private ResourceLoader classpathLoader(boolean withBom) {
    ResourceLoader classpath = new ClasspathResourceLoader(getClass());
    return new ResourceLoader() {
      @Override
      public InputStream openResource(String resource) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (withBom) {
          bytes.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        }
        try (InputStream in = classpath.openResource(resource)) {
          in.transferTo(bytes);
        }
        return new ByteArrayInputStream(bytes.toByteArray());
      }

      @Override
      public <T> Class<? extends T> findClass(String cname, Class<T> expectedType) {
        return classpath.findClass(cname, expectedType);
      }
    };
  }

  private Tokenizer tokenizer(String rulefiles, ResourceLoader loader, String text)
      throws IOException {
    Map<String, String> args = new HashMap<>();
    args.put(ICUTokenizerFactory.RULEFILES, rulefiles);
    ICUTokenizerFactory factory = new ICUTokenizerFactory(args);
    factory.inform(loader);
    Tokenizer stream = factory.create(newAttributeFactory());
    stream.setReader(new StringReader(text));
    return stream;
  }

  /** A one-line rule file that makes all Latin text one token. */
  public void testOneRuleFileWithBom() throws Exception {
    String rules = ".+ {200};\n";
    // without a BOM: "ab cd" is one token
    assertTokenStreamContents(
        tokenizer("Latn:keyword.rbbi", new StringMockResourceLoader(rules), "ab cd"),
        new String[] {"ab cd"});
    // StringMockResourceLoader encodes as UTF-8, so U+FEFF becomes the bytes EF BB BF
    assertTokenStreamContents(
        tokenizer("Latn:keyword.rbbi", new StringMockResourceLoader("\uFEFF" + rules), "ab cd"),
        new String[] {"ab cd"});
  }

  /** KeywordTokenizer.rbbi: '#' comment lines, then ".+ {200};". */
  public void testKeywordTokenizerRbbiWithBom() throws Exception {
    String rulefiles = "Cyrl:KeywordTokenizer.rbbi,Thai:KeywordTokenizer.rbbi";
    String text = "Some English.  Немного русский.  ข้อความภาษาไทยเล็ก ๆ น้อย ๆ  More English.";
    String[] expected = {
      "Some", "English", "Немного русский.  ", "ข้อความภาษาไทยเล็ก ๆ น้อย ๆ  ", "More", "English"
    };
    assertTokenStreamContents(tokenizer(rulefiles, classpathLoader(false), text), expected);
    assertTokenStreamContents(tokenizer(rulefiles, classpathLoader(true), text), expected);
  }

  /** Latin-break-only-on-whitespace.rbbi: '#' comment lines, then "!!forward;". */
  public void testLatinWhitespaceRbbiWithBom() throws Exception {
    String rulefiles = "Latn:Latin-break-only-on-whitespace.rbbi";
    String text = "Don't,break.at?/(punct)!  here";
    String[] expected = {"Don't,break.at?/(punct)!", "here"};
    assertTokenStreamContents(tokenizer(rulefiles, classpathLoader(false), text), expected);
    assertTokenStreamContents(tokenizer(rulefiles, classpathLoader(true), text), expected);
  }
}
