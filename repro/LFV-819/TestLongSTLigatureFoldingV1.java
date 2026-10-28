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
package verify.asciifold;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.tests.analysis.MockTokenizer;

/**
 * Independent check (public API only, different package than the filter): U+FB05 LATIN SMALL
 * LIGATURE LONG S T is not folded by ASCIIFoldingFilter, while its sibling U+FB06 LATIN SMALL
 * LIGATURE ST and U+017F LATIN SMALL LETTER LONG S are.
 */
public class TestLongSTLigatureFoldingV1 extends BaseTokenStreamTestCase {

  private static String fold(String s) {
    char[] in = s.toCharArray();
    char[] out = new char[4 * in.length];
    int len = ASCIIFoldingFilter.foldToASCII(in, 0, out, 0, in.length);
    return new String(out, 0, len);
  }

  private static String nfkdWithoutMarks(String s) {
    return Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{Mn}", "");
  }

  /** Unicode facts from the JDK's own character database (independent of Lucene). */
  public void testUnicodeFacts() {
    assertEquals("LATIN SMALL LIGATURE LONG S T", Character.getName(0xFB05));
    assertEquals("LATIN SMALL LIGATURE ST", Character.getName(0xFB06));
    assertEquals(
        Character.UnicodeBlock.ALPHABETIC_PRESENTATION_FORMS, Character.UnicodeBlock.of(0xFB05));
    assertEquals("st", Normalizer.normalize("ﬅ", Normalizer.Form.NFKD));
    assertEquals("st", Normalizer.normalize("ﬆ", Normalizer.Form.NFKD));
    assertEquals("s", Normalizer.normalize("ſ", Normalizer.Form.NFKD));
  }

  /** Controls: the table's own neighbouring entries. These pass. */
  public void testControlsFold() {
    assertEquals("st", fold("ﬆ")); // LATIN SMALL LIGATURE ST
    assertEquals("s", fold("ſ")); // LATIN SMALL LETTER LONG S
    assertEquals("ff", fold("ﬀ"));
    assertEquals("fi", fold("ﬁ"));
    assertEquals("fl", fold("ﬂ"));
    assertEquals("ffi", fold("ﬃ"));
    assertEquals("ffl", fold("ﬄ"));
  }

  /** The claim, on the public static API. */
  public void testStaticFoldLongST() {
    assertEquals("st", fold("ﬅ"));
  }

  /** The claim, through the token filter. */
  public void testFilterLongST() throws Exception {
    MockTokenizer tok = new MockTokenizer(MockTokenizer.WHITESPACE, false);
    tok.setReader(new java.io.StringReader("faﬆ faſt faﬅ"));
    TokenStream ts = new ASCIIFoldingFilter(tok);
    assertTokenStreamContents(ts, new String[] {"fast", "fast", "fast"});
  }

  /** User-visible effect: a document containing "faﬅ" is not found by the query "fast". */
  public void testSearchMiss() throws Exception {
    Analyzer a =
        CustomAnalyzer.builder()
            .withTokenizer("whitespace")
            .addTokenFilter("asciiFolding")
            .build();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
      Document control = new Document();
      control.add(new TextField("f", "faﬆ", Field.Store.NO));
      w.addDocument(control);
      Document target = new Document();
      target.add(new TextField("f", "faﬅ", Field.Store.NO));
      w.addDocument(target);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        // both documents contain the word "fast" written with an st ligature
        assertEquals(2, s.count(new TermQuery(new Term("f", "fast"))));
      }
    }
    a.close();
  }

  /**
   * In the whole Alphabetic Presentation Forms block, every letter whose NFKD (marks removed) is
   * pure ASCII letters is folded to that string, except U+FB05.
   */
  public void testOnlyGapInPresentationFormsBlock() {
    List<String> gaps = new ArrayList<>();
    for (int cp = 0xFB00; cp <= 0xFB4F; cp++) {
      if (!Character.isLetter(cp)) continue;
      String s = new String(Character.toChars(cp));
      String d = nfkdWithoutMarks(s);
      if (d.isEmpty() || !d.chars().allMatch(c -> c < 128 && Character.isLetter(c))) continue;
      String f = fold(s);
      if (!f.equals(d)) {
        gaps.add(String.format("U+%04X %s nfkd=%s fold=%s", cp, Character.getName(cp), d, f));
      }
    }
    assertEquals("gaps: " + gaps, List.of(), gaps);
  }
}
