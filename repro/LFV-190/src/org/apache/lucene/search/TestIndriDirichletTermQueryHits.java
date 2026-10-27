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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.IndriDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndriDirichletSimilarity returns log-probabilities, which are negative (BaseSimilarityTestCase
 * exempts it from the non-negative check). A plain TermQuery top-k search then returns no hits,
 * although count() and explain() report the matches.
 */
public class TestIndriDirichletTermQueryHits extends LuceneTestCase {

  public void testTermQueryReturnsItsMatches() throws Exception {
    Similarity sim = new IndriDirichletSimilarity();
    Directory dir = newDirectory();
    IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())).setSimilarity(sim));
    for (String body :
        new String[] {"president washington", "george", "washington general", "president"}) {
      Document doc = new Document();
      doc.add(new TextField("body", body, Field.Store.NO));
      w.addDocument(doc);
    }
    w.forceMerge(1);
    DirectoryReader reader = DirectoryReader.open(w);
    w.close();
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(sim);

    Query query = new TermQuery(new Term("body", "washington"));
    int count = searcher.count(query);
    TopDocs top = searcher.search(query, 10);
    Explanation explanation = searcher.explain(query, 0);
    reader.close();
    dir.close();

    assertEquals("count", 2, count);
    assertTrue("explain(doc 0) is a match: " + explanation, explanation.isMatch());
    assertEquals(
        "search(query, 10) must return the 2 matching documents (explain(doc 0) value = "
            + explanation.getValue()
            + ")",
        2,
        top.scoreDocs.length);
  }
}
