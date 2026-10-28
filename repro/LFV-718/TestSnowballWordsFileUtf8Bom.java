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
package org.apache.lucene.analysis.v1verify;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.commongrams.CommonGramsFilterFactory;
import org.apache.lucene.analysis.core.StopFilterFactory;
import org.apache.lucene.analysis.miscellaneous.KeepWordFilterFactory;
import org.apache.lucene.search.suggest.analyzing.SuggestStopFilterFactory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.ResourceLoader;

/**
 * A words file that starts with a UTF-8 byte order mark (EF BB BF), loaded through the public
 * analysis factories with format=wordset (default) and format=snowball. Only public API is used.
 */
public class TestSnowballWordsFileUtf8Bom extends BaseTokenStreamTestCase {

  private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  /** Serves raw bytes, like a file system or class path loader would. */
  private static final class BytesLoader implements ResourceLoader {
    private final Map<String, byte[]> files = new HashMap<>();

    BytesLoader put(String name, byte[] bytes) {
      files.put(name, bytes);
      return this;
    }

    @Override
    public InputStream openResource(String resource) throws IOException {
      byte[] b = files.get(resource);
      if (b == null) {
        throw new IOException("no such resource: " + resource);
      }
      return new ByteArrayInputStream(b);
    }

    @Override
    public <T> Class<? extends T> findClass(String cname, Class<T> expectedType) {
      throw new UnsupportedOperationException();
    }
  }

  private static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] withBom(String s) {
    byte[] body = utf8(s);
    byte[] out = new byte[BOM.length + body.length];
    System.arraycopy(BOM, 0, out, 0, BOM.length);
    System.arraycopy(body, 0, out, BOM.length, body.length);
    return out;
  }

  // One word per line: a valid file for both formats.
  private static final String WORDS = "the\nand\n";

  private static BytesLoader loader() {
    return new BytesLoader()
        .put("bom.txt", withBom(WORDS))
        .put("nobom.txt", utf8(WORDS))
        .put("bom-comment.txt", withBom("| a comment line\nthe\nand\n"));
  }

  private static Map<String, String> args(String words, String format) {
    Map<String, String> a = new HashMap<>();
    a.put("words", words);
    if (format != null) {
      a.put("format", format);
    }
    return a;
  }

  private static StopFilterFactory stop(String words, String format) throws IOException {
    StopFilterFactory f = new StopFilterFactory(args(words, format));
    f.inform(loader());
    return f;
  }

  private static String describe(CharArraySet set) {
    StringBuilder sb = new StringBuilder("[");
    for (Object o : set) {
      String s = new String((char[]) o);
      if (sb.length() > 1) sb.append(", ");
      sb.append(s.replace("\uFEFF", "\\uFEFF"));
    }
    return sb.append("]").toString();
  }

  // ---------------------------------------------------------------- controls (expected to pass)

  public void testControlWordsetFormatSkipsBom() throws Exception {
    StopFilterFactory f = stop("bom.txt", null);
    assertEquals("wordset", f.getFormat());
    assertTrue(describe(f.getStopWords()), f.getStopWords().contains("the"));
    assertEquals(2, f.getStopWords().size());
    TokenStream ts = f.create(whitespaceMockTokenizer("the cat and dog"));
    assertTokenStreamContents(ts, new String[] {"cat", "dog"});
  }

  public void testControlSnowballFormatWithoutBom() throws Exception {
    StopFilterFactory f = stop("nobom.txt", "snowball");
    assertTrue(describe(f.getStopWords()), f.getStopWords().contains("the"));
    assertEquals(2, f.getStopWords().size());
    TokenStream ts = f.create(whitespaceMockTokenizer("the cat and dog"));
    assertTokenStreamContents(ts, new String[] {"cat", "dog"});
  }

  // ---------------------------------------------------------------- claim (fails on affected code)

  public void testSnowballStopFactoryBomFirstWordIsAStopWord() throws Exception {
    StopFilterFactory f = stop("bom.txt", "snowball");
    assertTrue(
        "snowball stop set " + describe(f.getStopWords()) + " must contain 'the'",
        f.getStopWords().contains("the"));
  }

  public void testSnowballStopFactoryBomFirstWordIsRemoved() throws Exception {
    StopFilterFactory f = stop("bom.txt", "snowball");
    TokenStream ts = f.create(whitespaceMockTokenizer("the cat and dog"));
    assertTokenStreamContents(ts, new String[] {"cat", "dog"});
  }

  public void testSameFileSameSetInBothFormats() throws Exception {
    String wordset = describe(stop("bom.txt", "wordset").getStopWords());
    String snowball = describe(stop("bom.txt", "snowball").getStopWords());
    assertEquals("wordset vs snowball set of the same one-word-per-line file", wordset, snowball);
  }

  public void testSnowballBomBeforeCommentLineAddsNoEntry() throws Exception {
    StopFilterFactory f = stop("bom-comment.txt", "snowball");
    assertEquals(describe(f.getStopWords()), 2, f.getStopWords().size());
    assertTrue(describe(f.getStopWords()), f.getStopWords().contains("the"));
  }

  public void testSnowballKeepWordFactoryBom() throws Exception {
    KeepWordFilterFactory f = new KeepWordFilterFactory(args("bom.txt", "snowball"));
    f.inform(loader());
    TokenStream ts = f.create(whitespaceMockTokenizer("the cat and dog"));
    assertTokenStreamContents(ts, new String[] {"the", "and"});
  }

  public void testSnowballCommonGramsFactoryBom() throws Exception {
    CommonGramsFilterFactory f = new CommonGramsFilterFactory(args("bom.txt", "snowball"));
    f.inform(loader());
    assertTrue(
        "common words " + describe(f.getCommonWords()) + " must contain 'the'",
        f.getCommonWords().contains("the"));
  }

  public void testSnowballSuggestStopFactoryBom() throws Exception {
    SuggestStopFilterFactory f = new SuggestStopFilterFactory(args("bom.txt", "snowball"));
    f.inform(loader());
    assertTrue(
        "suggest stop set " + describe(f.getStopWords()) + " must contain 'the'",
        f.getStopWords().contains("the"));
  }
}
