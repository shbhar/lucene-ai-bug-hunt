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
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Independent check of the ContextSuggestField constructor contract: "@throws
 * IllegalArgumentException ... if value or contexts contains any reserved characters". The
 * reserved characters of a suggestion are U+001F, U+001E and U+0000 (SuggestField rejects them in
 * the value); ContextSuggestField adds U+001D. Only public API is used.
 *
 * <p>Two kinds of checks: (1) rejection: a context containing U+001E or U+001F is rejected like
 * one containing U+001D; (2) weaker: if such a context is accepted, it must round-trip (an exact
 * context filter returns exactly the documents indexed with that context, and the reported context
 * equals the indexed one).
 */
public class TestContextReservedChars extends LuceneTestCase {

  private static final String FIELD = "suggest_field";

  /**
   * Newest writable CompletionNNN postings format on the classpath (portable across versions; the
   * names are not ordered numerically, e.g. Completion912 is older than Completion101).
   */
  private static PostingsFormat completionFormat() {
    java.util.Set<String> names = PostingsFormat.availablePostingsFormats();
    for (String name :
        new String[] {"Completion104", "Completion101", "Completion912", "Completion99"}) {
      if (names.contains(name)) {
        return PostingsFormat.forName(name);
      }
    }
    throw new AssertionError("no completion postings format found in " + names);
  }

  private static IndexWriterConfig iwc(Analyzer analyzer) {
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    Codec codec = TestUtil.alwaysPostingsFormat(completionFormat());
    iwc.setCodec(codec);
    return iwc;
  }

  private static Analyzer keywordAnalyzer() {
    return new MockAnalyzer(random(), MockTokenizer.KEYWORD, false);
  }

  private static String esc(CharSequence s) {
    if (s == null) {
      return "null";
    }
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < 0x20) {
        b.append(String.format("<U+%04X>", (int) c));
      } else {
        b.append(c);
      }
    }
    return b.toString();
  }

  /** A rejection only counts if it is the reserved-character rejection, not an unrelated IAE. */
  private static void assertReservedRejection(IllegalArgumentException e) {
    if (e.getMessage() == null || e.getMessage().contains("reserved") == false) {
      throw new AssertionError("unrelated IllegalArgumentException", e);
    }
  }

  /** Index one document per (value, context) pair; returns false if indexing was rejected. */
  private static boolean indexAll(Directory dir, Analyzer analyzer, String[][] docs)
      throws Exception {
    try (IndexWriter w = new IndexWriter(dir, iwc(analyzer))) {
      for (String[] d : docs) {
        Document doc = new Document();
        doc.add(new ContextSuggestField(FIELD, d[0], 1, d[1]));
        w.addDocument(doc);
      }
      w.commit();
      return true;
    } catch (IllegalArgumentException e) {
      assertReservedRejection(e);
      return false; // rejected: acceptable outcome for the round-trip checks
    }
  }

  /** Runs a context query; returns sorted "key|context" strings. */
  private static List<String> suggest(Directory dir, Analyzer analyzer, String exactContext)
      throws Exception {
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
      ContextQuery q = new ContextQuery(new PrefixCompletionQuery(analyzer, new Term(FIELD, "s")));
      if (exactContext == null) {
        q.addAllContexts();
      } else {
        q.addContext(exactContext);
      }
      TopSuggestDocs top = searcher.suggest(q, 10, false);
      TreeSet<String> out = new TreeSet<>();
      for (TopSuggestDocs.SuggestScoreDoc sd : top.scoreLookupDocs()) {
        out.add(sd.key + "|" + esc(sd.context));
      }
      return new ArrayList<>(out);
    }
  }

  /** Returns null if rejected (IAE at construction or at addDocument), else a description. */
  private static String tryIndexContext(String context) throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc(keywordAnalyzer()))) {
      try {
        Document doc = new Document();
        doc.add(new ContextSuggestField(FIELD, "sugg", 1, context));
        w.addDocument(doc);
        w.commit();
      } catch (IllegalArgumentException expected) {
        assertReservedRejection(expected);
        return null;
      }
      return "context [" + esc(context) + "] was accepted and indexed";
    }
  }

  /** Control: the documented rejection mechanism works for U+001D (CONTEXT_SEPARATOR). */
  public void testControlContextSeparatorInContextIsRejected() throws Exception {
    assertNull(tryIndexContext("a\u001Db"));
  }

  /** Contract: a context containing the reserved U+001E (hole character) must be rejected. */
  public void testHoleCharacterInContextIsRejected() throws Exception {
    String r = tryIndexContext("a\u001Eb");
    assertNull("expected IllegalArgumentException (reserved character), but " + r, r);
  }

  /** Contract: a context containing the reserved U+001F (separator label) must be rejected. */
  public void testSepLabelInContextIsRejected() throws Exception {
    String r = tryIndexContext("a\u001Fb");
    assertNull("expected IllegalArgumentException (reserved character), but " + r, r);
  }

  /** Weaker property: if a U+001E context is accepted, it must not alias context "ab". */
  public void testHoleCharacterContextRoundTrips() throws Exception {
    Analyzer a = keywordAnalyzer();
    try (Directory dir = newDirectory()) {
      if (indexAll(dir, a, new String[][] {{"splain", "ab"}, {"shole", "a\u001Eb"}}) == false) {
        return; // rejected at indexing: fine
      }
      List<String> all = suggest(dir, a, null);
      List<String> exactAb = suggest(dir, a, "ab");
      List<String> exactHole = suggest(dir, a, "a\u001Eb");
      String diag = "all=" + all + " exact(ab)=" + exactAb + " exact(a<U+001E>b)=" + exactHole;
      assertEquals(diag, List.of("shole|a<U+001E>b", "splain|ab"), all);
      assertEquals(diag, List.of("splain|ab"), exactAb);
      assertEquals(diag, List.of("shole|a<U+001E>b"), exactHole);
    }
  }

  /** Weaker property: if a U+001F context is accepted, it must round-trip (default preserveSep). */
  public void testSepLabelContextRoundTrips() throws Exception {
    Analyzer a = keywordAnalyzer();
    try (Directory dir = newDirectory()) {
      if (indexAll(dir, a, new String[][] {{"splain", "ab"}, {"ssep", "a\u001Fb"}}) == false) {
        return;
      }
      List<String> all = suggest(dir, a, null);
      List<String> exactAb = suggest(dir, a, "ab");
      List<String> exactSep = suggest(dir, a, "a\u001Fb");
      String diag = "all=" + all + " exact(ab)=" + exactAb + " exact(a<U+001F>b)=" + exactSep;
      assertEquals(diag, List.of("splain|ab", "ssep|a<U+001F>b"), all);
      assertEquals(diag, List.of("splain|ab"), exactAb);
      assertEquals(diag, List.of("ssep|a<U+001F>b"), exactSep);
    }
  }

  /** Same with CompletionAnalyzer(preserveSep=false). */
  public void testSepLabelContextRoundTripsNoPreserveSep() throws Exception {
    Analyzer a = new CompletionAnalyzer(keywordAnalyzer(), false, true);
    try (Directory dir = newDirectory()) {
      if (indexAll(dir, a, new String[][] {{"splain", "ab"}, {"ssep", "a\u001Fb"}}) == false) {
        return;
      }
      List<String> all = suggest(dir, a, null);
      List<String> exactAb = suggest(dir, a, "ab");
      List<String> exactSep = suggest(dir, a, "a\u001Fb");
      String diag = "all=" + all + " exact(ab)=" + exactAb + " exact(a<U+001F>b)=" + exactSep;
      assertEquals(diag, List.of("splain|ab", "ssep|a<U+001F>b"), all);
      assertEquals(diag, List.of("splain|ab"), exactAb);
      assertEquals(diag, List.of("ssep|a<U+001F>b"), exactSep);
    }
  }

  /** Control: an ordinary context round-trips with the same harness (non-vacuity). */
  public void testControlOrdinaryContextRoundTrips() throws Exception {
    Analyzer a = keywordAnalyzer();
    try (Directory dir = newDirectory()) {
      assertTrue(indexAll(dir, a, new String[][] {{"splain", "ab"}, {"sother", "axb"}}));
      assertEquals(List.of("sother|axb", "splain|ab"), suggest(dir, a, null));
      assertEquals(List.of("splain|ab"), suggest(dir, a, "ab"));
      assertEquals(List.of("sother|axb"), suggest(dir, a, "axb"));
    }
  }
}
