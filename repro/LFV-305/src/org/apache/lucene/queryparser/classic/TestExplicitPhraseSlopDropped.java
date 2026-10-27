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
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.boost.DelimitedBoostTokenFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockSynonymFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The explicit proximity slop of a phrase ("..."~N) is applied after analysis by rewriting the
 * query, and only when the query is a top-level PhraseQuery or MultiPhraseQuery. When the phrase
 * analyzes to a token graph (multi-word synonym) or carries token boosts, the slop is silently
 * dropped and the phrase is searched with the parser's default slop (0).
 */
public class TestExplicitPhraseSlopDropped extends LuceneTestCase {

  /** Whitespace + lowercase + MockSynonymFilter (guinea pig -> cavy, dogs -> dog). */
  private static final class SynonymAnalyzer extends Analyzer {
    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
      Tokenizer tokenizer = new MockTokenizer(MockTokenizer.WHITESPACE, true);
      return new TokenStreamComponents(tokenizer, new MockSynonymFilter(tokenizer));
    }
  }

  /** Whitespace + DelimitedBoostTokenFilter ("word|2.0" gives the token "word" a boost of 2). */
  private static final class BoostAnalyzer extends Analyzer {
    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
      Tokenizer tokenizer = new WhitespaceTokenizer();
      return new TokenStreamComponents(tokenizer, new DelimitedBoostTokenFilter(tokenizer, '|'));
    }
  }

  private Directory index(String text) throws Exception {
    Directory dir = newDirectory();
    try (RandomIndexWriter w =
        new RandomIndexWriter(
            random(),
            dir,
            new IndexWriterConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true)))) {
      Document d = new Document();
      d.add(new TextField("body", text, Field.Store.NO));
      w.addDocument(d);
    }
    return dir;
  }

  private static int count(Directory dir, Query q) throws Exception {
    try (IndexReader r = DirectoryReader.open(dir)) {
      return new IndexSearcher(r).count(q);
    }
  }

  /** "guinea pig food"~1 must match "guinea pig dry food", like "pig food"~1 does. */
  public void testSlopKeptWhenPhraseHasMultiWordSynonym() throws Exception {
    try (Directory dir = index("my guinea pig dry food")) {
      QueryParser qp = new QueryParser("body", new SynonymAnalyzer());
      Query control = qp.parse("\"pig food\"~1");
      assertEquals(control.toString(), 1, count(dir, control));
      Query graph = qp.parse("\"guinea pig food\"~1");
      assertEquals(graph.toString(), 1, count(dir, graph));

      MultiFieldQueryParser mfqp =
          new MultiFieldQueryParser(new String[] {"body"}, new SynonymAnalyzer());
      Query multi = mfqp.parse("\"guinea pig food\"~1");
      assertEquals(multi.toString(), 1, count(dir, multi));
    }
  }

  /** "pig|2.0 food"~1 must match "pig dry food", like "pig food"~1 does. */
  public void testSlopKeptWhenPhraseTokensAreBoosted() throws Exception {
    try (Directory dir = index("pig dry food")) {
      QueryParser qp = new QueryParser("body", new BoostAnalyzer());
      Query control = qp.parse("\"pig food\"~1");
      assertEquals(control.toString(), 1, count(dir, control));
      Query boosted = qp.parse("\"pig|2.0 food\"~1");
      assertEquals(boosted.toString(), 1, count(dir, boosted));
    }
  }
}
