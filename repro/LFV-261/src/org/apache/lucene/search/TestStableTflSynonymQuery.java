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

import java.util.List;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.StableTflSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestStableTflSynonymQuery extends LuceneTestCase {

  public void testSynonymGroupScoresLikeTermQuery() throws Exception {
    StableTflSimilarity sim = new StableTflSimilarity();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setSimilarity(sim))) {
        w.addDocument(List.of(new TextField("f", "ab", Field.Store.NO)));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(sim);
        Query term = new TermQuery(new Term("f", "ab"));
        Query synonyms = new SynonymQuery.Builder("f").addTerm(new Term("f", "ab")).addTerm(new Term("f", "cd")).build();
        // expected: the TermQuery(ab) score (both synonyms are 2 code points); actual: the score of the 19-code-point "synonym pseudo-term"
        assertEquals(searcher.search(term, 1).scoreDocs[0].score, searcher.search(synonyms, 1).scoreDocs[0].score, 1e-6f);
      }
    }
  }
}
