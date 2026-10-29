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

public class TestIBNoNormalizationExplain extends LuceneTestCase {

  public void testDistributionChildMatchesScore() throws Exception {
    IBSimilarity sim =
        new IBSimilarity(new DistributionLL(), new LambdaDF(), new Normalization.NoNormalization());
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setSimilarity(sim))) {
      Document doc = new Document();
      doc.add(new TextField("f", "a a a", Field.Store.NO));
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(sim);
        Explanation ib = searcher.explain(new TermQuery(new Term("f", "a")), 0).getDetails()[0];
        double distribution = ib.getDetails()[2].getValue().doubleValue();
        // expected: distribution child = score (boost 1, tfn = freq = 3); actual: it uses tfn = 1
        assertEquals(ib.toString(), ib.getValue().doubleValue(), distribution, 1e-6);
      }
    }
  }
}
