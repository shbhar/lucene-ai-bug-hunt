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
package org.apache.lucene.queryparser.classic;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: with the default splitOnWhitespace=false, does a stopword placed directly
 * before AND/OR change which documents the classic QueryParser matches?
 *
 * <p>Only public API is used (QueryParser, StandardAnalyzer with an explicit stop set, IndexSearcher).
 * The tests assert that the query with the stopword matches exactly the same documents as (1) the
 * same query without the stopword and (2) the same query parsed with splitOnWhitespace=true.
 */
public class TestStopwordConjunctionBinding extends LuceneTestCase {

  private static final String F = "body";
  private static final String[] DOCS = {
    "apple cherry", // d0: no banana
    "apple banana cherry", // d1
    "banana cherry", // d2: no apple
    "cherry", // d3: only cherry
    "apple banana" // d4: no cherry
  };

  private static Analyzer stopAnalyzer() {
    return new StandardAnalyzer(new CharArraySet(Arrays.asList("the"), true));
  }

  private static Set<String> hits(Directory dir, Query q) throws Exception {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      Set<String> out = new TreeSet<>();
      for (ScoreDoc sd : s.search(q, 100).scoreDocs) {
        out.add(s.storedFields().document(sd.doc).get("id"));
      }
      return out;
    }
  }

  private static Directory index(Analyzer a) throws Exception {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
      for (int i = 0; i < DOCS.length; i++) {
        Document d = new Document();
        d.add(new StoredField("id", "d" + i));
        d.add(new TextField(F, DOCS[i], Field.Store.NO));
        w.addDocument(d);
      }
    }
    return dir;
  }

  private static Query parse(String text, QueryParser.Operator op, boolean sow) throws Exception {
    QueryParser qp = new QueryParser(F, stopAnalyzer());
    qp.setDefaultOperator(op);
    qp.setSplitOnWhitespace(sow);
    return qp.parse(text);
  }

  private void check(String withStop, String withoutStop, QueryParser.Operator op)
      throws Exception {
    // Default must be splitOnWhitespace=false (documented DEFAULT_SPLIT_ON_WHITESPACE).
    assertFalse(new QueryParser(F, stopAnalyzer()).getSplitOnWhitespace());
    try (Directory dir = index(stopAnalyzer())) {
      Query withStopDefault = parse(withStop, op, false);
      Query withoutStopDefault = parse(withoutStop, op, false);
      Query withStopSow = parse(withStop, op, true);
      Set<String> hWithStopDefault = hits(dir, withStopDefault);
      Set<String> hWithoutStopDefault = hits(dir, withoutStopDefault);
      Set<String> hWithStopSow = hits(dir, withStopSow);
      String msg =
          "op="
              + op
              + " ['"
              + withStop
              + "' sow=false -> "
              + withStopDefault
              + " hits "
              + hWithStopDefault
              + "] ['"
              + withoutStop
              + "' sow=false -> "
              + withoutStopDefault
              + " hits "
              + hWithoutStopDefault
              + "] ['"
              + withStop
              + "' sow=true -> "
              + withStopSow
              + " hits "
              + hWithStopSow
              + "]";
      System.out.println(msg);
      // Sanity: with sow=true the stopword does not change the matches.
      assertEquals("sow=true control: " + msg, hWithoutStopDefault, hWithStopSow);
      // Claim under test: with the default sow=false the stopword must not change the matches.
      assertEquals("sow=false (default): " + msg, hWithoutStopDefault, hWithStopDefault);
    }
  }

  /** Default operator OR: 'a b the AND c' vs 'a b AND c'. */
  public void testAndAfterStopwordOrOperator() throws Exception {
    check("apple banana the AND cherry", "apple banana AND cherry", QueryParser.Operator.OR);
  }

  /** Default operator AND: 'a b the OR c' vs 'a b OR c'. */
  public void testOrAfterStopwordAndOperator() throws Exception {
    check("apple banana the OR cherry", "apple banana OR cherry", QueryParser.Operator.AND);
  }

  /** Symbolic operator form '&&' with default OR. */
  public void testAmpAmpAfterStopwordOrOperator() throws Exception {
    check("apple banana the && cherry", "apple banana && cherry", QueryParser.Operator.OR);
  }

  /** Control: a stopword NOT adjacent to the conjunction; must pass on every version. */
  public void testControlStopwordNotBeforeConjunction() throws Exception {
    check("the apple banana AND cherry", "apple banana AND cherry", QueryParser.Operator.OR);
  }

  /** Control: a single term before the stopword (no multi-term run); must pass everywhere. */
  public void testControlSingleTermBeforeStopword() throws Exception {
    check("apple the AND cherry", "apple AND cherry", QueryParser.Operator.OR);
  }
}
