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
package org.apache.lucene.search.similarities;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * AxiomaticF1EXP, AxiomaticF1LOG, AxiomaticF3EXP and AxiomaticF3LOG explain their term-frequency
 * component as 'tf, term frequency computed as 1 + log(1 + log(freq)) from:' with the single child
 * 'freq, number of occurrences of term in the document'. The value is tf(), which adds 1 to freq
 * first ('freq += 1; // otherwise gives negative scores for freqs < 1'), so the node shows
 * 1 + log(1 + log(freq + 1)), not the formula it states on the freq it lists.
 */
public class TestAxiomaticTfExplainFormula extends LuceneTestCase {

  private static final String TF_NODE_PREFIX = "tf, term frequency computed as ";
  private static final String FREQ_CHILD = "freq, number of occurrences of term in the document";

  /** Evaluates the tf formula the node states, on the freq child it lists. */
  private static double statedFormula(String description, double freq) {
    String formula = description.substring(TF_NODE_PREFIX.length(), description.length() - " from:".length());
    return switch (formula) {
      case "1 + log(1 + log(freq))" -> 1 + Math.log(1 + Math.log(freq));
      case "1 + log(1 + log(freq + 1))" -> 1 + Math.log(1 + Math.log(freq + 1));
      default -> throw new AssertionError("unrecognized tf formula: " + description);
    };
  }

  public void testTfNodeMatchesItsFormulaOnItsFreqChild() throws Exception {
    Similarity[] sims = {
      new AxiomaticF1EXP(), new AxiomaticF1LOG(), new AxiomaticF3EXP(0.25f, 3), new AxiomaticF3LOG(0.25f, 3)
    };
    // doc 0 contains "a" once, doc 1 three times
    String[] docs = {"a b c", "a a a b", "b c d"};
    List<String> problems = new ArrayList<>();
    for (Similarity sim : sims) {
      try (Directory dir = newDirectory()) {
        IndexWriterConfig iwc = newIndexWriterConfig(new MockAnalyzer(random())).setSimilarity(sim);
        try (IndexWriter w = new IndexWriter(dir, iwc)) {
          for (String text : docs) {
            Document doc = new Document();
            doc.add(newTextField("f", text, Field.Store.NO));
            w.addDocument(doc);
          }
        }
        try (DirectoryReader reader = DirectoryReader.open(dir)) {
          IndexSearcher searcher = newSearcher(reader);
          searcher.setSimilarity(sim);
          for (int docId = 0; docId < 2; docId++) {
            Explanation root = searcher.explain(new TermQuery(new Term("f", "a")), docId);
            assertTrue(root.isMatch());
            Explanation tf = find(root, TF_NODE_PREFIX);
            assertNotNull("no tf node in " + root, tf);
            double freq = child(tf, FREQ_CHILD);
            double node = tf.getValue().doubleValue();
            double formulaOnChild = statedFormula(tf.getDescription(), freq);
            if (Math.abs(formulaOnChild - node) > 1e-6 * Math.abs(node)) {
              problems.add(
                  sim
                      + " doc "
                      + docId
                      + ": tf node shows "
                      + node
                      + " but '"
                      + tf.getDescription()
                      + "' with its child freq="
                      + freq
                      + " is "
                      + formulaOnChild);
            }
          }
        }
      }
    }
    assertTrue(String.join("\n", problems), problems.isEmpty());
  }

  private static Explanation find(Explanation e, String prefix) {
    if (e.getDescription().startsWith(prefix)) {
      return e;
    }
    for (Explanation d : e.getDetails()) {
      Explanation f = find(d, prefix);
      if (f != null) {
        return f;
      }
    }
    return null;
  }

  private static double child(Explanation parent, String description) {
    for (Explanation d : parent.getDetails()) {
      if (d.getDescription().equals(description)) {
        return d.getValue().doubleValue();
      }
    }
    throw new AssertionError("no child '" + description + "' under " + parent);
  }
}
