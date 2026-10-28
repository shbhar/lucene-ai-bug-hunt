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
 * Normalization.explain is documented as 'Returns an explanation for the normalized term
 * frequency'. Normalization.NoNormalization.tfn returns tf, but its explain returns the value 1.
 * IBSimilarity passes that explanation's value as tfn to Distribution.explain, so with
 * NoNormalization the Distribution child of 'score(IBSimilarity, freq=...), computed as boost *
 * distribution.score(stats, normalization.tfn(stats, freq, docLen), lambda.lambda(stats)) from:' is
 * distribution.score(stats, 1, lambda) instead of distribution.score(stats, freq, lambda), and
 * disagrees with the node's own value. The same similarity with NormalizationH1 (a control) is
 * consistent.
 */
public class TestNoNormalizationExplain extends LuceneTestCase {

  private static final String[] DOCS = {"a b c a a", "a d e", "f g h", "a a b", "x y z"};

  public void testNormalizationChildAndDistributionChild() throws Exception {
    Distribution[] dists = {new DistributionLL(), new DistributionSPL()};
    Lambda[] lambdas = {new LambdaDF(), new LambdaTTF()};
    List<String> problems = new ArrayList<>();
    for (Distribution dist : dists) {
      for (Lambda lambda : lambdas) {
        for (Normalization norm :
            new Normalization[] {new Normalization.NoNormalization(), new NormalizationH1()}) {
          check(new IBSimilarity(dist, lambda, norm), problems);
        }
      }
    }
    assertTrue(String.join("\n", problems), problems.isEmpty());
  }

  private void check(IBSimilarity sim, List<String> problems) throws Exception {
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
        // doc 0 contains "a" three times
        Explanation root = searcher.explain(new TermQuery(new Term("f", "a")), 0);
        assertTrue(root.isMatch());
        Explanation node = findPrefix(root, "score(IBSimilarity, freq=");
        assertNotNull("no IBSimilarity score node in " + root, node);
        Explanation[] kids = node.getDetails();
        // no boost child at boost 1: [normalization, lambda, distribution]
        assertEquals("children of " + node, 3, kids.length);
        double tfnShown = kids[0].getValue().doubleValue();
        double lambdaShown = kids[1].getValue().doubleValue();
        double distShown = kids[2].getValue().doubleValue();
        double nodeValue = node.getValue().doubleValue();
        double freq = 3;
        if (sim.getNormalization() instanceof Normalization.NoNormalization noNorm
            && tfnShown != noNorm.tfn(null, freq, 0)) { // NoNormalization.tfn returns tf
          double expectedTfn = noNorm.tfn(null, freq, 0);
          problems.add(
              sim
                  + ": normalization child '"
                  + kids[0].getDescription()
                  + "' shows "
                  + tfnShown
                  + " but NoNormalization.tfn(freq="
                  + freq
                  + ") = "
                  + expectedTfn);
        }
        // the formula 'boost * distribution.score(...)' at boost 1 on the shown children
        if (Math.abs(distShown - nodeValue) > 1e-6 * Math.abs(nodeValue)) {
          problems.add(
              sim
                  + ": node shows "
                  + nodeValue
                  + " but its child '"
                  + kids[2].getDescription()
                  + "' (distribution.score) shows "
                  + distShown
                  + " (lambda "
                  + lambdaShown
                  + ")");
        }
      }
    }
  }

  private static Explanation findPrefix(Explanation e, String prefix) {
    if (e.getDescription().startsWith(prefix)) {
      return e;
    }
    for (Explanation d : e.getDetails()) {
      Explanation f = findPrefix(d, prefix);
      if (f != null) {
        return f;
      }
    }
    return null;
  }
}
