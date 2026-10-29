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

import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestAxiomaticF1EXPTfExplain extends LuceneTestCase {

  public void testTfDescriptionStatesTheFormulaOfItsValue() throws Exception {
    Similarity sim = new AxiomaticF1EXP();
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setSimilarity(sim))) {
        Document doc = new Document();
        doc.add(new TextField("f", "a", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(sim);
        Explanation score = searcher.explain(new TermQuery(new Term("f", "a")), 0).getDetails()[0];
        Explanation tf =
            Arrays.stream(score.getDetails())
                .filter(e -> e.getDescription().startsWith("tf,"))
                .findFirst()
                .orElseThrow();
        // freq = 1: expected the formula of the value 1.5266 = 1 + log(1 + log(freq + 1)); actual: 1 + log(1 + log(freq)), which is 1.0
        assertEquals(
            "tf value " + tf.getValue(),
            "tf, term frequency computed as 1 + log(1 + log(freq + 1)) from:",
            tf.getDescription());
      }
    }
  }
}
