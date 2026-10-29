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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Both grammars declare TAB, LF, CR and U+3000 as whitespace and skip it inside ranges, but the
 * range-bound token RANGE_GOOP excludes only U+0020, so these characters become part of a bound.
 */
public class TestRangeBoundWhitespace extends LuceneTestCase {

  private static final String[] DATES = {
    "20191231", "20200101", "20200615", "20201231", "20210101"
  };

  private final Analyzer analyzer = new StandardAnalyzer();

  private Query classic(String q) throws Exception {
    return new QueryParser("body", analyzer).parse(q);
  }

  private Query flexible(String q) throws Exception {
    return new StandardQueryParser(analyzer).parse(q, "body");
  }

  /** Control, passes today: outside a range the four characters separate clauses like a space. */
  public void testControlOutsideRange() throws Exception {
    for (String ws : new String[] {"\t", "\n", "\r", "\u3000"}) {
      assertEquals(classic("body:alpha body:beta"), classic("body:alpha" + ws + "body:beta"));
      assertEquals(flexible("body:alpha body:beta"), flexible("body:alpha" + ws + "body:beta"));
    }
  }

  /** Loud: the TABs turn {@code 20200101\tTO\t20201231} into one bound, so the parse fails. */
  public void testTabAroundToClassic() throws Exception {
    assertEquals(classic("date:[20200101 TO 20201231]"), classic("date:[20200101\tTO\t20201231]"));
  }

  /** Loud, flexible parser: the same input fails there too. */
  public void testTabAroundToFlexible() throws Exception {
    assertEquals(
        flexible("date:[20200101 TO 20201231]"), flexible("date:[20200101\tTO\t20201231]"));
  }

  /** Silent: the line breaks join the bounds ("\n20200101", "20201231\n"); 20191231 matches. */
  public void testLineBreaksInsideBrackets() throws Exception {
    assertHits(3, "date:[\n20200101 TO 20201231\n]");
  }

  /** Silent: the exclusive upper bound becomes "20201231\t", so 20201231 itself matches. */
  public void testTabBeforeExclusiveEnd() throws Exception {
    assertHits(1, "date:{20200101 TO 20201231\t}");
  }

  /** Silent: the lower bound becomes U+3000 + "20200101", which sorts after every date: 0 hits. */
  public void testIdeographicSpaceAfterBracket() throws Exception {
    assertHits(3, "date:[\u300020200101 TO 20201231]");
  }

  private void assertHits(int expected, String q) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        for (String date : DATES) {
          Document doc = new Document();
          doc.add(new StringField("date", date, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        String hits = "classic " + s.count(classic(q)) + ", flexible " + s.count(flexible(q));
        assertEquals(q, "classic " + expected + ", flexible " + expected, hits);
      }
    }
  }
}
