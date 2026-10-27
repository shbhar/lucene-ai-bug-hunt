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
package org.apache.lucene.queryparser.complexPhrase;

import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A complex phrase whose contents parse to a single clause that is not a bare term or multi-term
 * query fails at search time:
 *
 * <ul>
 *   <li>a leading stop word before one wildcard/fuzzy/group clause ("the beat*"), or a leading
 *       '+' ("+john"), makes the contents a one-clause BooleanQuery, which rewrite() turns into a
 *       one-clause SpanNearQuery, and searching it throws IllegalArgumentException "Less than 2
 *       subSpans.size():1";
 *   <li>a boosted single clause ("john^2") throws IllegalArgumentException "Unknown query type
 *       BoostQuery", although interior boosts are documented (TestComplexPhraseQuery#testBoosts)
 *       to be ignored.
 * </ul>
 *
 * Each of these phrases should match exactly what the same phrase without the stop word, the
 * '+' or the boost matches.
 */
public class TestComplexPhraseSingleClauseContents extends LuceneTestCase {
  private static final String F = "name";
  private static final String[] DOCS = {"the beatles", "beat it", "john smith", "jonathan peters"};

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;
  private Analyzer analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // whitespace tokenizer, lower-casing, English stop words (position increments preserved)
    analyzer =
        new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true, MockTokenFilter.ENGLISH_STOPSET);
    dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer));
    for (String text : DOCS) {
      Document doc = new Document();
      doc.add(newTextField(F, text, Field.Store.YES));
      w.addDocument(doc);
    }
    w.close();
    reader = DirectoryReader.open(dir);
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private Set<String> hits(String queryString) throws Exception {
    ComplexPhraseQueryParser qp = new ComplexPhraseQueryParser(F, analyzer);
    Query q = qp.parse(queryString);
    Set<String> result = new TreeSet<>();
    try {
      for (ScoreDoc sd : searcher.search(q, 10).scoreDocs) {
        result.add(searcher.storedFields().document(sd.doc).get(F));
      }
    } catch (IllegalArgumentException e) {
      throw new AssertionError(
          queryString + " parsed to " + q + " but searching it threw " + e, e);
    }
    return result;
  }

  /** Controls: the same phrases without the stop word, '+' or boost work. */
  public void testControls() throws Exception {
    assertEquals(Set.of("beat it", "the beatles"), hits("\"beat*\""));
    assertEquals(Set.of("john smith"), hits("\"john\""));
    assertEquals(Set.of("john smith", "jonathan peters"), hits("\"jo*\""));
    // a leading stop word before a plain term is fine (the run is analyzed as one TermQuery)
    assertEquals(Set.of("john smith"), hits("\"the john\""));
  }

  /** Leading stop word before a single prefix clause: default parser settings. */
  public void testLeadingStopWordBeforePrefix() throws Exception {
    assertEquals(hits("\"beat*\""), hits("\"the beat*\""));
  }

  /** Leading stop word before a single fuzzy clause. */
  public void testLeadingStopWordBeforeFuzzy() throws Exception {
    assertEquals(hits("\"jhn~1\""), hits("\"the jhn~1\""));
  }

  /** A required single clause. */
  public void testRequiredSingleClause() throws Exception {
    assertEquals(hits("\"john\""), hits("\"+john\""));
  }

  /** A boosted single clause: interior boosts are ignored inside complex phrases. */
  public void testBoostedSingleClause() throws Exception {
    assertEquals(hits("\"john\""), hits("\"john^2\""));
    assertEquals(hits("\"jo*\""), hits("\"jo*^2\""));
  }
}
