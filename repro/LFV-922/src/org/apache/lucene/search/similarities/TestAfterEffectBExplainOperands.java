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
 * AfterEffectB.explain labels two children 'F, total number of occurrences of term across all
 * documents + 1' and 'n, number of documents containing term + 1', and the node says 'computed as
 * (F + 1) / (n * (tfn + 1)) from:'. The node value uses F = totalTermFreq + 1 and n = docFreq + 1,
 * but the two children show totalTermFreq and docFreq without the + 1, so the formula applied to
 * the children the node lists does not give the node's value.
 */
public class TestAfterEffectBExplainOperands extends LuceneTestCase {

  private static final String[] DOCS = {"a b c a a", "a d e", "f g h", "a a b", "x y z"};

  public void testAfterEffectBChildrenMatchTheirLabelsAndFormula() throws Exception {
    Similarity[] sims = {
      new DFRSimilarity(new BasicModelIn(), new AfterEffectB(), new NormalizationH2()),
      new DFRSimilarity(new BasicModelG(), new AfterEffectB(), new NormalizationH1()),
      new DFRSimilarity(new BasicModelIF(), new AfterEffectB(), new NormalizationH3())
    };
    List<String> problems = new ArrayList<>();
    for (Similarity sim : sims) {
      try (Directory dir = newDirectory()) {
        IndexWriterConfig iwc = newIndexWriterConfig(new MockAnalyzer(random())).setSimilarity(sim);
        try (IndexWriter w = new IndexWriter(dir, iwc)) {
          for (String text : DOCS) {
            Document doc = new Document();
            doc.add(newTextField("f", text, Field.Store.NO));
            w.addDocument(doc);
          }
        }
        try (DirectoryReader reader = DirectoryReader.open(dir)) {
          IndexSearcher searcher = newSearcher(reader);
          searcher.setSimilarity(sim);
          Term term = new Term("f", "a");
          long docFreq = reader.docFreq(term); // 3
          long totalTermFreq = reader.totalTermFreq(term); // 6
          Explanation root = searcher.explain(new TermQuery(term), 0);
          assertTrue(root.isMatch());
          Explanation ae = find(root, "AfterEffectB, computed as (F + 1) / (n * (tfn + 1)) from:");
          assertNotNull("no AfterEffectB node in " + root, ae);

          double tfn = child(ae, "tfn, normalized term frequency");
          double F = child(ae, "F, total number of occurrences of term across all documents + 1");
          double n = child(ae, "n, number of documents containing term + 1");
          double node = ae.getValue().doubleValue();
          double formulaOnChildren = (F + 1) / (n * (tfn + 1));

          if (F != totalTermFreq + 1) {
            problems.add(
                sim + ": child 'F, ... + 1' shows " + F + " but totalTermFreq + 1 = " + (totalTermFreq + 1));
          }
          if (n != docFreq + 1) {
            problems.add(sim + ": child 'n, ... + 1' shows " + n + " but docFreq + 1 = " + (docFreq + 1));
          }
          if (Math.abs(formulaOnChildren - node) > 1e-6 * Math.abs(node)) {
            problems.add(
                sim
                    + ": node shows "
                    + node
                    + " but (F + 1) / (n * (tfn + 1)) on its children (F="
                    + F
                    + ", n="
                    + n
                    + ", tfn="
                    + tfn
                    + ") = "
                    + formulaOnChildren);
          }
        }
      }
    }
    assertTrue(String.join("\n", problems), problems.isEmpty());
  }

  private static Explanation find(Explanation e, String description) {
    if (e.getDescription().equals(description)) {
      return e;
    }
    for (Explanation d : e.getDetails()) {
      Explanation f = find(d, description);
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
