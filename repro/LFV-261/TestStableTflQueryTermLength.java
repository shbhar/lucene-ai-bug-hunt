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
package org.apache.lucene.search;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.StableTflSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockSynonymAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * StableTflSimilarity derives term rarity from the length of the query term. SynonymQuery and
 * CombinedFieldQuery hand the similarity a TermStats whose term bytes are a fixed placeholder
 * string, so the similarity sees the placeholder's length instead of the query term's length.
 *
 * <p>Only public API is used. Each StableTfl test has a BM25 control that runs the same check.
 */
public class TestStableTflQueryTermLength extends LuceneTestCase {

  private static String filler(int n) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < n; i++) {
      sb.append(" zz");
    }
    return sb.toString();
  }

  private static float scoreOf(IndexSearcher searcher, Query query, int doc) throws IOException {
    TopDocs td = searcher.search(query, 100);
    for (ScoreDoc sd : td.scoreDocs) {
      if (sd.doc == doc) {
        return sd.score;
      }
    }
    throw new AssertionError("doc " + doc + " not matched by " + query);
  }

  private static Directory index(Similarity sim, String[][] docs) throws IOException {
    Directory dir = newDirectory();
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer).setSimilarity(sim);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String[] fields : docs) {
        Document d = new Document();
        for (int i = 0; i < fields.length; i += 2) {
          d.add(new TextField(fields[i], fields[i + 1], Field.Store.NO));
        }
        w.addDocument(d);
      }
      w.forceMerge(1); // docs keep their insertion order as doc ids
    }
    return dir;
  }

  // ---------------------------------------------------------------------------------------------
  // 1) SynonymQuery {ab, cd}: both synonyms have 2 code points. The doc contains "ab" once.
  //    SynonymQuery javadoc: scores "as if you had indexed them as one term". StableTfl uses no
  //    corpus statistics, so the only input that can differ from TermQuery(ab) is the term length.
  // ---------------------------------------------------------------------------------------------

  private void checkSynonymEqualLength(Similarity sim) throws IOException {
    try (Directory dir =
            index(
                sim,
                new String[][] {
                  {"body", "ab" + filler(39)}, // doc 0: 40 tokens, one "ab"
                  {"body", "cd" + filler(39)} // doc 1: one "cd" (same df, BM25 control)
                });
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      s.setSimilarity(sim);
      Query syn =
          new SynonymQuery.Builder("body")
              .addTerm(new Term("body", "ab"))
              .addTerm(new Term("body", "cd"))
              .build();
      Query tq = new TermQuery(new Term("body", "ab"));
      float synScore = scoreOf(s, syn, 0);
      float termScore = scoreOf(s, tq, 0);
      assertEquals(
          sim
              + ": SynonymQuery{ab,cd} vs TermQuery(ab) on doc 0; explanation:\n"
              + s.explain(syn, 0),
          termScore,
          synScore,
          1e-5f * termScore);
    }
  }

  public void testSynonymOfEqualLengthTermsStableTfl() throws IOException {
    checkSynonymEqualLength(new StableTflSimilarity());
  }

  public void testSynonymOfEqualLengthTermsBM25Control() throws IOException {
    checkSynonymEqualLength(new BM25Similarity());
  }

  // ---------------------------------------------------------------------------------------------
  // 2) CombinedFieldQuery over a single field "a" (weight 1) vs TermQuery on a copy field "b"
  //    with identical content. CombinedFieldQuery javadoc: scores "as if they had been indexed in a
  //    single field"; similarities that keep the default computeNorm are compatible (StableTfl
  //    does). Upstream TestCombinedFieldQuery#testCopyFieldWithSingleField asserts this equality
  //    for BM25/Boolean/Classic/LM similarities.
  // ---------------------------------------------------------------------------------------------

  private void checkCombinedFieldCopyField(Similarity sim) throws IOException {
    int numDocs = 5;
    String[][] docs = new String[numDocs][];
    for (int i = 0; i < numDocs; i++) {
      StringBuilder text = new StringBuilder();
      for (int j = 0; j <= i; j++) {
        text.append(" foo");
      }
      text.append(filler(10 * i + 3));
      docs[i] = new String[] {"a", text.toString(), "b", text.toString()};
    }
    try (Directory dir = index(sim, docs);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      s.setSimilarity(sim);
      Query cfq = new CombinedFieldQuery.Builder("foo").addField("a").build();
      Query tq = new TermQuery(new Term("b", "foo"));
      for (int doc = 0; doc < numDocs; doc++) {
        float cfqScore = scoreOf(s, cfq, doc);
        float termScore = scoreOf(s, tq, doc);
        assertEquals(
            sim
                + ": CombinedFieldQuery(a:foo) vs TermQuery(b:foo) on doc "
                + doc
                + "; explanation of the combined field query:\n"
                + s.explain(cfq, doc),
            termScore,
            cfqScore,
            1e-5f * termScore);
      }
    }
  }

  public void testCombinedFieldSingleFieldStableTfl() throws IOException {
    checkCombinedFieldCopyField(new StableTflSimilarity());
  }

  public void testCombinedFieldSingleFieldBM25Control() throws IOException {
    checkCombinedFieldCopyField(new BM25Similarity());
  }

  // ---------------------------------------------------------------------------------------------
  // 3) Impact through the ordinary query-building path. QueryBuilder with a synonym analyzer
  //    turns "dogs elephant" into (Synonym(body:dog body:dogs) body:elephant). Doc 0 holds "dog"
  //    once, doc 1 holds "elephant" once, both 200 tokens long. StableTfl: rarity increases with
  //    term length, so the 8-letter "elephant" must outrank a match on the 3/4-letter synonym group
  //    whatever length the group is given (3 or 4). This holds for TermQuery(dog) vs
  //    TermQuery(elephant); with the placeholder the synonym group gets length 19.
  // ---------------------------------------------------------------------------------------------

  public void testQueryBuilderSynonymOutranksLongerTermStableTfl() throws IOException {
    Similarity sim = new StableTflSimilarity();
    try (Directory dir =
            index(
                sim,
                new String[][] {
                  {"body", "dog" + filler(199)}, // doc 0
                  {"body", "elephant" + filler(199)} // doc 1
                });
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      s.setSimilarity(sim);
      Query q =
          new QueryBuilder(new MockSynonymAnalyzer()).createBooleanQuery("body", "dogs elephant");
      assertNotNull(q);
      float dogDoc = scoreOf(s, q, 0);
      float elephantDoc = scoreOf(s, q, 1);
      float tqDog = scoreOf(s, new TermQuery(new Term("body", "dog")), 0);
      float tqElephant = scoreOf(s, new TermQuery(new Term("body", "elephant")), 1);
      // control on the TermQuery path: the longer term is rarer
      assertTrue("TermQuery control", tqElephant > tqDog);
      assertTrue(
          "query "
              + q
              + ": score(elephant doc)="
              + elephantDoc
              + " must exceed score(dog doc)="
              + dogDoc
              + " (TermQuery(dog)="
              + tqDog
              + ", TermQuery(elephant)="
              + tqElephant
              + ", synonym-group/TermQuery(dog) ratio="
              + (dogDoc / tqDog)
              + "); explanation for the dog doc:\n"
              + s.explain(q, 0),
          elephantDoc > dogDoc);
    }
  }
}
