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

import java.util.Arrays;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.similarities.IndriDirichletSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndriQuery does not override rewrite(), so IndriAndQuery reports itself as primitive (rewrite
 * returns this) but builds weights for its clauses without rewriting them. Any clause that needs a
 * rewrite before it can create a Weight (PrefixQuery, WildcardQuery, FuzzyQuery, ...) makes the
 * search fail with UnsupportedOperationException.
 */
public class TestIndriAndQueryRewritesClauses extends LuceneTestCase {

  public void testMultiTermClause() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        for (String s : new String[] {"apple banana", "apple", "banana", "cherry"}) {
          Document d = new Document();
          d.add(new TextField("f", s, Field.Store.NO));
          w.addDocument(d);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new IndriDirichletSimilarity());
        searcher.setQueryCache(null);

        Query prefix = new PrefixQuery(new Term("f", "ban"));
        Query withPrefix =
            new IndriAndQuery(
                List.of(
                    new BooleanClause(new TermQuery(new Term("f", "apple")), Occur.SHOULD),
                    new BooleanClause(prefix, Occur.SHOULD)));
        // The same query with the clause already rewritten by the searcher.
        Query withRewrittenPrefix =
            new IndriAndQuery(
                List.of(
                    new BooleanClause(new TermQuery(new Term("f", "apple")), Occur.SHOULD),
                    new BooleanClause(searcher.rewrite(prefix), Occur.SHOULD)));

        TopDocs expected = searcher.search(withRewrittenPrefix, 10);
        TopDocs actual = searcher.search(withPrefix, 10); // throws on main
        assertEquals(Arrays.toString(expected.scoreDocs), Arrays.toString(actual.scoreDocs));
      }
    }
  }
}
