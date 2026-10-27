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
package org.apache.lucene.analysis.uk;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
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
import org.apache.lucene.queryparser.simple.SimpleQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Security-angle probe: a restriction written as a parsed multi-term query on a field analyzed by
 * the stock UkrainianMorfologikAnalyzer. Each asserted check compares the parsed rule with the same
 * rule spelled in the indexed representation (lower case, ASCII apostrophe, no stress mark, г for
 * ґ), which is what Analyzer#normalize promises to produce. Deny rules (MUST_NOT) whose term has an
 * upper-case letter, a typographic apostrophe, a stress mark or ґ exclude nothing; some allow rules
 * (a range bound with an upper-case letter, a negated character class) admit terms that the
 * correctly normalized rule does not. The tests named measure* only print (capitalized dictionary
 * lemmas, fuzzy terms) and never fail.
 */
public class TestUkrainianNormalizeRuleProbe extends LuceneTestCase {

  // id -> text
  private static final String[][] DOCS = {
    {"0", "м’ясо"}, // typographic apostrophe U+2019, as Ukrainian text usually spells it
    {"1", "Lucene"},
    {"2", "ґанок"}, // ґанок (ґ is mapped to г by initReader)
    {"3", "секрет"},
    {"4", "apple"},
    {"5", "zebra"},
    {"6", "Київ"}, // proper noun: the dictionary lemma is capitalized
  };

  private Analyzer analyzer;
  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new UkrainianMorfologikAnalyzer();
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

  private static String esc(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      // keep ASCII and Cyrillic readable; escape everything else (U+2019, U+0301, U+00AD, ...)
      if (c < 0x20 || (c >= 0x7f && c < 0x400) || c > 0x4ff) {
        sb.append(String.format("\\u%04X", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private void assertSameDocs(String rule, Query parsed, Query reference) throws Exception {
    Set<String> got = ids(parsed);
    Set<String> want = ids(reference);
    System.out.println(
        "PROBE rule="
            + esc(rule)
            + " parsed="
            + esc(parsed.toString())
            + " docs="
            + got
            + " | reference="
            + esc(reference.toString())
            + " docs="
            + want);
    assertEquals("rule " + esc(rule) + " parsed as " + esc(parsed.toString()), want, got);
  }

  private void measure(String label, Query q) throws Exception {
    System.out.println("MEASURE " + esc(label) + " parsed=" + esc(q.toString()) + " docs=" + ids(q));
  }

  /** Not a check: records the single indexed term of each document. */
  public void testDumpIndexedTerms() throws Exception {
    for (String[] d : DOCS) {
      List<String> toks = new ArrayList<>();
      try (TokenStream ts = analyzer.tokenStream("body", d[1])) {
        CharTermAttribute t = ts.addAttribute(CharTermAttribute.class);
        ts.reset();
        while (ts.incrementToken()) {
          toks.add(t.toString());
        }
        ts.end();
      }
      System.out.println(
          "INDEXED id="
              + d[0]
              + " text="
              + esc(d[1])
              + " terms="
              + esc(toks.toString())
              + " normalize="
              + esc(analyzer.normalize("body", d[1]).utf8ToString()));
    }
  }

  private static Query deny(Query rule) {
    return new BooleanQuery.Builder()
        .add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST)
        .add(rule, BooleanClause.Occur.MUST_NOT)
        .build();
  }

  // ---- deny rules (MUST_NOT): the document the rule names must be hidden ----

  public void testDenyUpperCasePrefixClassic() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertSameDocs("*:* -Luc*", qp.parse("*:* -Luc*"), qp.parse("*:* -luc*"));
  }

  public void testDenySentenceCaseCyrillicPrefixClassic() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertSameDocs("*:* -Секрет*", qp.parse("*:* -Секрет*"), qp.parse("*:* -секрет*"));
  }

  public void testDenyTypographicApostrophePrefixClassic() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertSameDocs("*:* -м’яс*", qp.parse("*:* -м’яс*"), qp.parse("*:* -м'яс*"));
  }

  public void testDenyGheUpturnPrefixClassic() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertSameDocs("*:* -ґанок*", qp.parse("*:* -ґанок*"), qp.parse("*:* -ганок*"));
  }

  public void testDenyStressMarkPrefixClassic() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertSameDocs("*:* -секре́т*", qp.parse("*:* -секре́т*"), qp.parse("*:* -секрет*"));
  }

  public void testDenyUpperCaseWildcardClassic() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertSameDocs("*:* -Luce?e", qp.parse("*:* -Luce?e"), qp.parse("*:* -luce?e"));
  }

  public void testDenyUpperCaseRegexpClassic() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertSameDocs("*:* -/Luc.*/", qp.parse("*:* -/Luc.*/"), qp.parse("*:* -/luc.*/"));
  }

  public void testDenyUpperCasePrefixFlexible() throws Exception {
    StandardQueryParser qp = new StandardQueryParser(analyzer);
    assertSameDocs(
        "*:* -Luc* (flexible)", qp.parse("*:* -Luc*", "body"), qp.parse("*:* -luc*", "body"));
  }

  public void testDenyTypographicApostrophePrefixFlexible() throws Exception {
    StandardQueryParser qp = new StandardQueryParser(analyzer);
    assertSameDocs(
        "*:* -м’яс* (flexible)",
        qp.parse("*:* -м’яс*", "body"),
        qp.parse("*:* -м'яс*", "body"));
  }

  /** The application composes the deny clause itself; the rule text is in simple query syntax. */
  public void testDenyUpperCasePrefixSimpleParser() throws Exception {
    SimpleQueryParser qp = new SimpleQueryParser(analyzer, "body");
    assertSameDocs("MUST_NOT simple(Luc*)", deny(qp.parse("Luc*")), deny(qp.parse("luc*")));
  }

  // ---- allow rules that admit more than the correctly normalized rule ----

  public void testAllowRangeUpperCaseLatinBound() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertSameDocs(
        "[Lucene TO zzz]",
        qp.parse("[Lucene TO zzz]"),
        new TermRangeQuery("body", newBytesRef("lucene"), newBytesRef("zzz"), true, true));
  }

  public void testAllowRangeUpperCaseCyrillicBound() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertSameDocs(
        "[С TO я]",
        qp.parse("[С TO я]"),
        new TermRangeQuery("body", newBytesRef("с"), newBytesRef("я"), true, true));
  }

  public void testAllowRegexpNegatedUpperCaseClass() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertSameDocs(
        "/[^L]ucene/", qp.parse("/[^L]ucene/"), new RegexpQuery(new Term("body", "[^l]ucene")));
  }

  // ---- controls: rules already in the indexed representation behave as written ----

  public void testControlsIndexedForm() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    assertEquals(Set.of("0", "2", "3", "4", "5", "6"), ids(qp.parse("*:* -luc*")));
    assertEquals(Set.of("1", "2", "3", "4", "5", "6"), ids(qp.parse("*:* -м'яс*")));
    assertEquals(Set.of("0", "1", "3", "4", "5", "6"), ids(qp.parse("*:* -ганок*")));
    assertEquals(Set.of("0", "1", "2", "4", "5", "6"), ids(qp.parse("*:* -секрет*")));
  }

  // ---- measurements only (never fail) ----

  /** Capitalized dictionary lemma: which spelling of a deny rule hides the document "Київ"? */
  public void testMeasureCapitalizedLemmaDenyRules() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    measure("*:* -Київ*", qp.parse("*:* -Київ*"));
    measure("*:* -київ*", qp.parse("*:* -київ*"));
    measure("*:* -Київ", qp.parse("*:* -Київ"));
    measure("*:* -київ", qp.parse("*:* -київ"));
  }

  public void testMeasureFuzzy() throws Exception {
    QueryParser qp = new QueryParser("body", analyzer);
    measure("Lucene~1", qp.parse("Lucene~1"));
    measure("lucene~1", qp.parse("lucene~1"));
    measure("LUCENE~2", qp.parse("LUCENE~2"));
    measure("lucene~2", qp.parse("lucene~2"));
    measure("Київ~1", qp.parse("Київ~1"));
    measure("київ~1", qp.parse("київ~1"));
  }
}
