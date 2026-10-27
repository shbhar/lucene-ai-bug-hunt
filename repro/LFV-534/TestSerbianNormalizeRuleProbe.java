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
package org.apache.lucene.analysis.sr;

import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Security-angle probe: a restriction written as a parsed multi-term query on a SerbianAnalyzer
 * field. Deny rules (MUST_NOT) whose prefix/wildcard/regexp term contains a Cyrillic Serbian letter
 * or a Latin letter with a diacritic exclude nothing; some allow rules (range bound, fuzzy term with
 * an expanding letter, negated character class) admit terms that the correctly normalized rule does
 * not. Each check compares the parsed rule with the same rule spelled in the indexed ("bald" Latin)
 * representation, which is what Analyzer#normalize promises to produce.
 */
public class TestSerbianNormalizeRuleProbe extends LuceneTestCase {

  // id -> text; the comment is the single term SerbianAnalyzer indexes
  private static final String[][] DOCS = {
    {"0", "поверљиво"}, // poverljiv
    {"1", "šifra"}, // sifr
    {"2", "zak"}, // zak
    {"3", "djak"}, // djak
    {"4", "tajna"}, // tajn
    {"5", "zora"}, // zor
  };

  private Analyzer analyzer;
  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new SerbianAnalyzer();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      for (String[] d : DOCS) {
        Document doc = new Document();
        doc.add(new StringField("id", d[0], Field.Store.YES));
        doc.add(new TextField("body", d[1], Field.Store.NO));
        w.addDocument(doc);
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    analyzer.close();
    super.tearDown();
  }

  private Set<String> ids(Query q) throws Exception {
    Set<String> out = new TreeSet<>();
    StoredFields sf = searcher.storedFields();
    for (ScoreDoc sd : searcher.search(q, 100).scoreDocs) {
      out.add(sf.document(sd.doc).get("id"));
    }
    return out;
  }

  private void assertSameDocs(String rule, Query parsed, Query reference) throws Exception {
    Set<String> got = ids(parsed);
    Set<String> want = ids(reference);
    System.out.println(
        "PROBE rule=" + rule + " parsed=" + parsed + " docs=" + got + " | reference=" + reference + " docs=" + want);
    assertEquals("rule " + rule + " parsed as " + parsed, want, got);
  }

  /** Deny rule with a Cyrillic prefix: the document it names must be hidden. */
  public void testDenyRuleCyrillicPrefixClassic() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertSameDocs("*:* -повер*", qp.parse("*:* -повер*"), qp.parse("*:* -pover*"));
  }

  public void testDenyRuleCyrillicPrefixFlexible() throws Exception {
    StandardQueryParser qp = new StandardQueryParser(analyzer);
    assertSameDocs(
        "*:* -повер* (flexible)", qp.parse("*:* -повер*", "body"), qp.parse("*:* -pover*", "body"));
  }

  /** Deny rule with a Latin letter with a diacritic. */
  public void testDenyRuleDiacriticPrefixClassic() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertSameDocs("*:* -šifr*", qp.parse("*:* -šifr*"), qp.parse("*:* -sifr*"));
  }

  public void testDenyRuleCyrillicWildcardClassic() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertSameDocs("*:* -пов?рљ*", qp.parse("*:* -пов?рљ*"), qp.parse("*:* -pov?rlj*"));
  }

  public void testDenyRuleCyrillicRegexpClassic() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertSameDocs("*:* -/повер.*/", qp.parse("*:* -/повер.*/"), qp.parse("*:* -/pover.*/"));
  }

  /** Allow rule: range bound with a diacritic letter (Serbian Latin order ... s š t ...). */
  public void testAllowRuleRangeDiacriticBound() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertSameDocs(
        "[a TO š]", qp.parse("[a TO š]"), new TermRangeQuery("body", newBytesRef("a"), newBytesRef("s"), true, true));
  }

  /** Allow rule: fuzzy term with a letter that expands to two (đ -> dj). */
  public void testAllowRuleFuzzyExpandingLetter() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertSameDocs("đak~1", qp.parse("đak~1"), new FuzzyQuery(new Term("body", "djak"), 1));
  }

  /** Allow rule: negated character class with a Cyrillic letter. */
  public void testAllowRuleRegexpNegatedClass() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertSameDocs("/[^ж]ak/", qp.parse("/[^ж]ak/"), new RegexpQuery(new Term("body", "[^z]ak")));
  }

  /** Controls: rules already in the indexed representation behave as written. */
  public void testControlsLatinWithoutDiacritics() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertEquals(Set.of("1", "2", "3", "4", "5"), ids(qp.parse("*:* -pover*")));
    assertEquals(Set.of("0", "2", "3", "4", "5"), ids(qp.parse("*:* -sifr*")));
    assertEquals(Set.of("3"), ids(qp.parse("djak~1")));
    assertEquals(Set.of("3", "0"), ids(qp.parse("[a TO s]")));
  }
}
