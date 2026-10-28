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
package org.apache.lucene.search.suggest.document;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.suggest.document.TopSuggestDocs.SuggestScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.automaton.Operations;

/**
 * Probe (independent of the finder's test): the same suggestion set written in Latin letters and
 * in Cyrillic letters (a one-to-one letter mapping) must get the same fuzzy boosts when the query
 * is unicodeAware, because the boost counts shared letters. Also: byte mode (unicodeAware=false)
 * on the same Cyrillic data, ContextQuery wrapping, and a spurious code point/byte equality.
 */
public class TestFuzzyUnicodeBoostV3 extends LuceneTestCase {

  private static IndexWriterConfig iwc(Analyzer analyzer) {
    IndexWriterConfig iwc = newIndexWriterConfig(random(), analyzer);
    Codec def = TestUtil.getDefaultCodec();
    iwc.setCodec(
        new FilterCodec(def.getName(), def) {
          final PostingsFormat completion = new Completion104PostingsFormat();

          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                return completion;
              }
            };
          }
        });
    return iwc;
  }

  private List<String> run(
      String[] values, int[] weights, String key, boolean unicodeAware, boolean context)
      throws Exception {
    Analyzer analyzer = new WhitespaceAnalyzer();
    List<String> out = new ArrayList<>();
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(analyzer))) {
      for (int i = 0; i < values.length; i++) {
        Document doc = new Document();
        if (context) {
          doc.add(new ContextSuggestField("f", values[i], weights[i], "ctx"));
        } else {
          doc.add(new SuggestField("f", values[i], weights[i]));
        }
        w.addDocument(doc);
      }
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
        CompletionQuery q =
            new FuzzyCompletionQuery(
                analyzer,
                new Term("f", key),
                null,
                1,
                true,
                1,
                3,
                unicodeAware,
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
        if (context) {
          q = new ContextQuery(q);
        }
        for (SuggestScoreDoc hit : searcher.suggest(q, 10, false).scoreLookupDocs()) {
          out.add(hit.key + "/" + hit.score);
        }
      }
    }
    System.out.println(
        "V3 key=" + key + " unicodeAware=" + unicodeAware + " context=" + context + " -> " + out);
    return out;
  }

  // A: Latin control, unicodeAware=true: "kot" exact-prefix "koty" (w3) vs typo "kity" (w4)
  public void testA_latinControl() throws Exception {
    assertEquals(
        List.of("koty/6.0", "kity/4.0"),
        run(new String[] {"koty", "kity"}, new int[] {3, 4}, "kot", true, false));
  }

  // B: the same letters in Cyrillic (k->к, o->о, t->т, y->ы, i->и), unicodeAware=true
  public void testB_cyrillicUnicodeAware() throws Exception {
    assertEquals(
        List.of("коты/6.0", "киты/4.0"),
        run(new String[] {"коты", "киты"}, new int[] {3, 4}, "кот", true, false));
  }

  // C: same Cyrillic data in byte mode (unicodeAware=false): only print, assert the exact prefix
  // ranks first (byte mode compares bytes with bytes)
  public void testC_cyrillicByteMode() throws Exception {
    List<String> r = run(new String[] {"коты", "киты"}, new int[] {3, 4}, "кот", false, false);
    assertFalse(r.isEmpty());
    assertTrue(r.toString(), r.get(0).startsWith("коты/"));
  }

  // D: ContextQuery(FuzzyCompletionQuery unicodeAware) over ContextSuggestField, Cyrillic
  public void testD_cyrillicContextQuery() throws Exception {
    assertEquals(
        List.of("коты/6.0", "киты/4.0"),
        run(new String[] {"коты", "киты"}, new int[] {3, 4}, "кот", true, true));
  }

  // E: Latin control for D
  public void testE_latinContextQuery() throws Exception {
    assertEquals(
        List.of("koty/6.0", "kity/4.0"),
        run(new String[] {"koty", "kity"}, new int[] {3, 4}, "kot", true, true));
  }

  // F: spurious equality: query code point U+00C3 equals the UTF-8 lead byte 0xC3 of U+00E9.
  // "aÃcd" (exact prefix, w3) shares "aÃ" -> 2*3=6; the typo "aécd" (w4) shares only "a" -> 1*4=4
  public void testF_spuriousLeadByteMatch() throws Exception {
    assertEquals(
        List.of("aÃcd/6.0", "aécd/4.0"),
        run(new String[] {"aÃcd", "aécd"}, new int[] {3, 4}, "aÃc", true, false));
  }
}
