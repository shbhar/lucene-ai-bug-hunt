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
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.StableTflSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockSynonymAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * StableTflSimilarity derives each query term's rarity from the length of {@code
 * TermStats#term()}. SynonymQuery and CombinedFieldQuery call the similarity with placeholder
 * terms ("synonym pseudo-term", 19 code points, and "pseudo_term", 11 code points), so with this
 * similarity every synonym group and every combined-field term is scored with the rarity of the
 * placeholder string instead of the rarity of the real query term.
 */
public class TestStableTflPseudoTermLength extends LuceneTestCase {

  private static Analyzer whitespace() {
    return new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
  }

  /**
   * Mirrors TestCombinedFieldQuery#testCopyFieldWithSingleField (which runs only with BM25,
   * Boolean, Classic and LM similarities): a CombinedFieldQuery over one field with weight w must
   * score like a TermQuery over a copy field that holds every token w times. StableTflSimilarity
   * keeps the default computeNorm, which the CombinedFieldQuery javadoc names as the compatibility
   * requirement.
   */
  public void testCombinedFieldQuerySingleFieldMatchesCopyField() throws IOException {
    StableTflSimilarity sim = new StableTflSimilarity();
    int boost = 2;
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(whitespace());
      iwc.setSimilarity(sim);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int freqA = 1; freqA <= 3; freqA++) {
          Document doc = new Document();
          for (int j = 0; j < freqA; j++) {
            doc.add(new TextField("a", "foo", Store.NO));
          }
          for (int j = 0; j < freqA * boost; j++) {
            doc.add(new TextField("b", "foo", Store.NO));
          }
          w.addDocument(doc);
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(sim);
        Query combined = new CombinedFieldQuery.Builder("foo").addField("a", boost).build();
        Query copyField = new TermQuery(new Term("b", "foo"));
        for (int doc = 0; doc < 3; doc++) {
          float c = searcher.explain(combined, doc).getValue().floatValue();
          float t = searcher.explain(copyField, doc).getValue().floatValue();
          assertEquals(
              "doc "
                  + doc
                  + ": CombinedFieldQuery score must equal the copy-field TermQuery score; "
                  + "CombinedFieldQuery explanation:\n"
                  + searcher.explain(combined, doc),
              t,
              c,
              1e-6f);
        }
      }
    }
  }

  /**
   * StableTfl ignores corpus statistics. A document that contains only "ab" (once) matches the
   * synonym group {ab, cd} with summed freq 1; both synonyms have 2 code points, so "scoring the
   * terms as if you had indexed them as one term" (SynonymQuery javadoc) must give the TermQuery
   * score of "ab".
   */
  public void testSynonymQueryOfEqualLengthTermsScoresLikeTermQuery() throws IOException {
    StableTflSimilarity sim = new StableTflSimilarity();
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(whitespace());
      iwc.setSimilarity(sim);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        doc.add(new TextField("body", "ab xx yy", Store.NO));
        w.addDocument(doc);
        doc = new Document();
        doc.add(new TextField("body", "cd xx yy", Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(sim);
        Query term = new TermQuery(new Term("body", "ab"));
        Query synonyms =
            new SynonymQuery.Builder("body")
                .addTerm(new Term("body", "ab"))
                .addTerm(new Term("body", "cd"))
                .build();
        float t = searcher.explain(term, 0).getValue().floatValue();
        float s = searcher.explain(synonyms, 0).getValue().floatValue();
        assertEquals(
            "SynonymQuery{ab,cd} on a doc containing only 'ab' once; explanation:\n"
                + searcher.explain(synonyms, 0),
            t,
            s,
            1e-6f);
      }
    }
  }

  /**
   * End to end through QueryBuilder (the query parsers' synonym path): "dogs" expands to the
   * synonym group {dogs, dog}. StableTfl's rarity grows with term length, so for equal tf and doc
   * length a document matching the 8-letter "elephant" must outscore one matching the 3-letter
   * "dog". With the placeholder term the synonym group is scored as a 19-letter term and wins.
   */
  public void testSynonymExpansionGetsPlaceholderRarity() throws IOException {
    StableTflSimilarity sim = new StableTflSimilarity();
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(whitespace());
      iwc.setSimilarity(sim);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        // two documents of 200 tokens each: one "dog" or one "elephant" plus 199 filler tokens
        String filler = " xx".repeat(199);
        Document doc = new Document();
        doc.add(new TextField("body", "dog" + filler, Store.NO)); // doc 0
        w.addDocument(doc);
        doc = new Document();
        doc.add(new TextField("body", "elephant" + filler, Store.NO)); // doc 1
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(sim);
        Query q = new QueryBuilder(new MockSynonymAnalyzer()).createBooleanQuery("body", "dogs elephant");
        assertTrue(q.toString(), q.toString().contains("Synonym("));
        float dog = searcher.explain(q, 0).getValue().floatValue();
        float elephant = searcher.explain(q, 1).getValue().floatValue();
        // control: the same comparison with plain terms ranks the longer term higher
        float dogTerm = searcher.explain(new TermQuery(new Term("body", "dog")), 0).getValue().floatValue();
        float elephantTerm =
            searcher.explain(new TermQuery(new Term("body", "elephant")), 1).getValue().floatValue();
        assertTrue("control: elephant=" + elephantTerm + " dog=" + dogTerm, elephantTerm > dogTerm);
        System.out.println(
            "TermQuery(dog)="
                + dogTerm
                + " TermQuery(elephant)="
                + elephantTerm
                + " synonym group {dog,dogs} on the dog doc="
                + dog
                + " ("
                + (dog / dogTerm)
                + "x the TermQuery(dog) score)");
        assertTrue(
            "query "
                + q
                + ": score(doc 'elephant')="
                + elephant
                + " must exceed score(doc 'dog')="
                + dog
                + "; explanation for the 'dog' doc:\n"
                + searcher.explain(q, 0),
            elephant > dog);
      }
    }
  }
}
