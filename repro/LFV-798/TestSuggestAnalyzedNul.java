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

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilter;
import org.apache.lucene.analysis.charfilter.MappingCharFilter;
import org.apache.lucene.analysis.charfilter.NormalizeCharMap;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Independent verifier repro (public API only; the package is only for symmetry with Lucene's own
 * suggest tests, no package-private member is used).
 *
 * <p>Claim under test: SuggestField rejects U+0000 (the document suggester's internal end byte) in
 * the raw value, but not in the analyzed form. When an analyzer turns an accepted raw value into a
 * form that contains U+0000, other suggestions in the segment can be lost, and the FST inputs can
 * be added out of order at flush.
 */
public class TestSuggestAnalyzedNul extends LuceneTestCase {

  private static final String FIELD = "sugg";

  /** Analyzer: a char filter in front of KeywordTokenizer (the whole value is one token). */
  private static Analyzer htmlStripKeyword() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new KeywordTokenizer());
      }

      @Override
      protected Reader initReader(String fieldName, Reader reader) {
        return new HTMLStripCharFilter(reader);
      }
    };
  }

  /** Analyzer: MappingCharFilter maps '~' to the given replacement, then KeywordTokenizer. */
  private static Analyzer mappingKeyword(String replacement) {
    NormalizeCharMap.Builder b = new NormalizeCharMap.Builder();
    b.add("~", replacement);
    final NormalizeCharMap map = b.build();
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new KeywordTokenizer());
      }

      @Override
      protected Reader initReader(String fieldName, Reader reader) {
        return new MappingCharFilter(map, reader);
      }
    };
  }

  /** Newest completion postings format available in this Lucene version, looked up by SPI name. */
  private static PostingsFormat completionFormat() {
    for (String name :
        new String[] {"Completion104", "Completion101", "Completion912", "Completion99"}) {
      try {
        return PostingsFormat.forName(name);
      } catch (IllegalArgumentException e) {
        // not in this version
      }
    }
    throw new AssertionError("no completion postings format found");
  }

  private static IndexWriterConfig config(Analyzer analyzer) {
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    final PostingsFormat completion = completionFormat();
    Codec codec =
        new FilterCodec(TestUtil.getDefaultCodec().getName(), TestUtil.getDefaultCodec()) {
          @Override
          public PostingsFormat postingsFormat() {
            final PerFieldPostingsFormat defaults = (PerFieldPostingsFormat) delegate.postingsFormat();
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                return FIELD.equals(field) ? completion : defaults.getPostingsFormatForField(field);
              }
            };
          }
        };
    iwc.setCodec(codec);
    return iwc;
  }

  /** The single analyzed token of {@code value}. */
  private static String analyzed(Analyzer a, String value) throws Exception {
    try (TokenStream ts = a.tokenStream(FIELD, value)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      ts.reset();
      assertTrue(ts.incrementToken());
      String s = term.toString();
      assertFalse(ts.incrementToken());
      ts.end();
      return s;
    }
  }

  /** Doc ids returned by a prefix completion query, n large enough for every document. */
  private static Set<Integer> suggestDocs(DirectoryReader r, Analyzer a, String prefix)
      throws Exception {
    SuggestIndexSearcher s = new SuggestIndexSearcher(r);
    TopSuggestDocs top = s.suggest(new PrefixCompletionQuery(a, new Term(FIELD, prefix)), 50, false);
    Set<Integer> docs = new TreeSet<>();
    for (TopSuggestDocs.SuggestScoreDoc d : top.scoreLookupDocs()) {
      docs.add(d.doc);
    }
    return docs;
  }

  /**
   * Indexes values[i] with weights[i] as doc i (one segment, one commit) and returns a list of
   * problems: a failed commit, or a document that a prefix query for "a" (every value starts with
   * 'a') or for its own raw value does not return.
   */
  private List<String> run(Analyzer a, String[] values, int[] weights) throws Exception {
    List<String> problems = new ArrayList<>();
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, config(a));
      Set<Integer> rejected = new TreeSet<>();
      for (int i = 0; i < values.length; i++) {
        Document d = new Document();
        d.add(new SuggestField(FIELD, values[i], weights[i])); // accepted: raw value has no U+0000
        try {
          w.addDocument(d);
        } catch (IllegalArgumentException e) {
          // also acceptable: the reserved analyzed form is rejected up front (non-aborting)
          rejected.add(i);
        }
      }
      // with no rejection, doc ids equal the value index (one segment, no deletes)
      assertTrue("writer must stay open after a rejection", w.isOpen());
      try {
        w.commit();
      } catch (Throwable t) {
        problems.add(
            "commit threw "
                + t
                + (t.getCause() != null ? " cause=" + t.getCause() : "")
                + "; writer open="
                + w.isOpen());
        if (w.isOpen()) {
          w.close();
        }
        return problems;
      }
      w.close();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        if (!rejected.isEmpty()) {
          // rejection path (only with a fix): check the remaining docs by value, not by doc id
          problems.addAll(checkByValue(r, a, values, rejected));
          return problems;
        }
        assertEquals(values.length, r.numDocs());
        Set<Integer> all = suggestDocs(r, a, "a");
        for (int i = 0; i < values.length; i++) {
          if (!all.contains(i)) {
            problems.add("prefix 'a' misses doc " + i + " (" + escape(values[i]) + "); got " + all);
          }
          Set<Integer> own = suggestDocs(r, a, values[i]);
          if (!own.contains(i)) {
            problems.add(
                "own-value prefix '" + escape(values[i]) + "' misses doc " + i + "; got " + own);
          }
        }
      }
    }
    return problems;
  }

  /** After rejections, doc ids shift: compare surface keys instead (accepted values only). */
  private static List<String> checkByValue(
      DirectoryReader r, Analyzer a, String[] values, Set<Integer> rejected) throws Exception {
    List<String> problems = new ArrayList<>();
    SuggestIndexSearcher s = new SuggestIndexSearcher(r);
    TopSuggestDocs top = s.suggest(new PrefixCompletionQuery(a, new Term(FIELD, "a")), 50, false);
    List<String> keys = new ArrayList<>();
    for (TopSuggestDocs.SuggestScoreDoc d : top.scoreLookupDocs()) {
      keys.add(d.key.toString());
    }
    List<String> expected = new ArrayList<>();
    for (int i = 0; i < values.length; i++) {
      if (!rejected.contains(i) && values[i].startsWith("a")) {
        expected.add(values[i]);
      }
    }
    java.util.Collections.sort(keys);
    java.util.Collections.sort(expected);
    if (!keys.equals(expected)) {
      problems.add("after rejecting " + rejected + ": prefix 'a' keys " + keys + " != " + expected);
    }
    return problems;
  }

  private static String escape(String s) {
    StringBuilder b = new StringBuilder();
    for (char c : s.toCharArray()) {
      if (c < 0x20) {
        b.append(String.format("\\u%04X", (int) c));
      } else {
        b.append(c);
      }
    }
    return b.toString();
  }

  /** Non-vacuity: the stock char filter really turns the accepted raw value into a U+0000. */
  public void testPreconditionAnalyzedFormHasNul() throws Exception {
    Analyzer a = htmlStripKeyword();
    assertEquals("a\u0000", analyzed(a, "a&#0;"));
    assertEquals("a\u0001", analyzed(a, "a&#1;"));
    new SuggestField(FIELD, "a&#0;", 1); // accepted by the constructor (no reserved raw char)
    expectThrows(IllegalArgumentException.class, () -> new SuggestField(FIELD, "a\u0000", 1));
    a.close();
  }

  /**
   * Two docs: plain "a" and "a&#0;" (analyzed "a\u0000"). Expected: a prefix query "a" (n=50)
   * returns both. Several weight orders, because which FST arc carries the output depends on them.
   */
  public void testPlainSuggestionLostNextToAnalyzedNul() throws Exception {
    Analyzer a = htmlStripKeyword();
    int[][] weightSets = {{1, 2}, {2, 1}, {5, 5}, {10, 1}, {1, 10}};
    List<String> all = new ArrayList<>();
    for (int[] ws : weightSets) {
      for (String p : run(a, new String[] {"a", "a&#0;"}, ws)) {
        all.add("weights " + ws[0] + "," + ws[1] + ": " + p);
      }
    }
    a.close();
    assertTrue("problems:\n" + String.join("\n", all), all.isEmpty());
  }

  /** Control: identical, but "&#1;" (U+0001, not reserved). Must be clean. */
  public void testControlU0001() throws Exception {
    Analyzer a = htmlStripKeyword();
    int[][] weightSets = {{1, 2}, {2, 1}, {5, 5}, {10, 1}, {1, 10}};
    List<String> all = new ArrayList<>();
    for (int[] ws : weightSets) {
      for (String p : run(a, new String[] {"a", "a&#1;"}, ws)) {
        all.add("weights " + ws[0] + "," + ws[1] + ": " + p);
      }
      for (String p : run(a, new String[] {"a", "a", "a&#1;"}, new int[] {ws[0], ws[1], 3})) {
        all.add("3docs weights " + ws[0] + "," + ws[1] + ": " + p);
      }
    }
    a.close();
    assertTrue("control problems:\n" + String.join("\n", all), all.isEmpty());
  }

  /**
   * Three docs "a", "a", "a&#0;": analyzed 'a' gets two FST inputs (61 00 00, 61 00 01), and the
   * analyzed form 'a\u0000' sorts after 'a' in the terms dictionary but its FST input 61 00 00 00
   * sorts before 61 00 01. Expected: commit succeeds and all three docs are suggested.
   */
  public void testFlushTwoPlainOneNul() throws Exception {
    Analyzer a = htmlStripKeyword();
    List<String> p = run(a, new String[] {"a", "a", "a&#0;"}, new int[] {1, 2, 3});
    a.close();
    assertTrue("problems:\n" + String.join("\n", p), p.isEmpty());
  }

  /** Same as the 2-doc test, but U+0000 comes from a MappingCharFilter rule ('~' to U+0000). */
  public void testMappingCharFilterVariant() throws Exception {
    Analyzer a = mappingKeyword("\u0000");
    assertEquals("a\u0000", analyzed(a, "a~"));
    List<String> all = new ArrayList<>();
    for (String p : run(a, new String[] {"a", "a~"}, new int[] {1, 2})) {
      all.add("2docs: " + p);
    }
    for (String p : run(a, new String[] {"a", "a", "a~"}, new int[] {1, 2, 3})) {
      all.add("3docs: " + p);
    }
    a.close();
    assertTrue("problems:\n" + String.join("\n", all), all.isEmpty());
  }
}
