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

import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.DelegatingAnalyzerWrapper;
import org.apache.lucene.analysis.Tokenizer;
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
 * MultiFieldQueryParser#getFieldQuery(String, String, boolean) treats the i-th clause of every
 * per-field BooleanQuery as "term position i" and, under AND_OPERATOR, makes every such group
 * required. Two legal configurations break that assumption.
 */
public class TestMultiFieldQueryParserPositionGroups extends LuceneTestCase {

  /** Letters-only tokenizer + lowercase + MockSynonymFilter (guinea pig -> cavy, dogs -> dog). */
  private static final class SimpleSynonymAnalyzer extends Analyzer {
    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
      Tokenizer tokenizer = new MockTokenizer(MockTokenizer.SIMPLE, true);
      return new TokenStreamComponents(tokenizer, new MockSynonymFilter(tokenizer));
    }
  }

  private static int count(Directory dir, Query q) throws Exception {
    try (IndexReader r = DirectoryReader.open(dir)) {
      return new IndexSearcher(r).count(q);
    }
  }

  /**
   * With autoGeneratePhraseQueries, a word that the analyzer turns into a token graph becomes a
   * BooleanQuery of SHOULD alternatives ("guinea pig" OR cavy). MultiFieldQueryParser takes the
   * alternatives for term positions and, under AND, requires all of them: even with a single field
   * it then disagrees with QueryParser on the same field.
   */
  public void testGraphAlternativesAreNotTermPositions() throws Exception {
    Analyzer analyzer = new SimpleSynonymAnalyzer();
    Directory dir = newDirectory();
    try (RandomIndexWriter w =
        new RandomIndexWriter(
            random(), dir, new IndexWriterConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true)))) {
      Document d = new Document();
      d.add(new TextField("body", "my guinea pig", Field.Store.NO));
      w.addDocument(d);
    }

    QueryParser single = new QueryParser("body", analyzer);
    single.setSplitOnWhitespace(true);
    single.setAutoGeneratePhraseQueries(true);
    single.setDefaultOperator(QueryParser.Operator.AND);

    MultiFieldQueryParser multi = new MultiFieldQueryParser(new String[] {"body"}, analyzer);
    multi.setSplitOnWhitespace(true);
    multi.setAutoGeneratePhraseQueries(true);
    multi.setDefaultOperator(QueryParser.Operator.AND);

    Query singleQuery = single.parse("guinea-pig");
    Query multiQuery = multi.parse("guinea-pig");
    // QueryParser: body:"guinea pig" body:cavy -> the document matches
    assertEquals(singleQuery.toString(), 1, count(dir, singleQuery));
    // MultiFieldQueryParser over the same single field must match the same documents
    assertEquals(multiQuery.toString(), 1, count(dir, multiQuery));
    dir.close();
  }

  /**
   * With per-field analyzers, the per-field queries do not have the same clause structure: the
   * whitespace-tokenized title field gives the single term title:wi-fi, the letter-tokenized body
   * field gives the two positions body:wi body:fi. Grouping them by clause index gives
   * +(title:wi-fi body:wi) +(body:fi), so a document whose title holds the whole query term no
   * longer matches, although QueryParser on "title" alone matches it and the class documents that
   * "it doesn't matter in what fields they appear".
   */
  public void testPerFieldAnalyzersDoNotShareTermPositions() throws Exception {
    Analyzer whitespace = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    Analyzer letters = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    Analyzer perField =
        new DelegatingAnalyzerWrapper(Analyzer.PER_FIELD_REUSE_STRATEGY) {
          @Override
          protected Analyzer getWrappedAnalyzer(String fieldName) {
            return "title".equals(fieldName) ? whitespace : letters;
          }
        };
    Directory dir = newDirectory();
    try (RandomIndexWriter w =
        new RandomIndexWriter(random(), dir, new IndexWriterConfig(perField))) {
      Document d = new Document();
      d.add(new TextField("title", "wi-fi router", Field.Store.NO));
      d.add(new TextField("body", "cheap and fast", Field.Store.NO));
      w.addDocument(d);
    }

    QueryParser titleOnly = new QueryParser("title", perField);
    titleOnly.setDefaultOperator(QueryParser.Operator.AND);
    Query titleQuery = titleOnly.parse("wi-fi");
    assertEquals(titleQuery.toString(), 1, count(dir, titleQuery));

    MultiFieldQueryParser multi =
        new MultiFieldQueryParser(new String[] {"title", "body"}, perField);
    multi.setDefaultOperator(QueryParser.Operator.AND);
    Query multiQuery = multi.parse("wi-fi");
    // Adding the field "body" to the searched fields must not lose the title match.
    assertEquals(multiQuery.toString(), 1, count(dir, multiQuery));
    dir.close();
  }
}
