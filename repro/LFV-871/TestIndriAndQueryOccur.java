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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIndriAndQueryOccur extends LuceneTestCase {

  public void testMustAndMustNotClausesAreNotSilentlyIgnored() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String value : new String[] {"a", "b"}) { // doc 0: f=a, doc 1: f=b
          Document doc = new Document();
          doc.add(new StringField("f", value, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        List<BooleanClause> clauses =
            List.of(
                new BooleanClause(new TermQuery(new Term("f", "a")), Occur.MUST),
                new BooleanClause(new TermQuery(new Term("f", "b")), Occur.MUST_NOT));
        IndriAndQuery query;
        try {
          query = new IndriAndQuery(clauses);
        } catch (IllegalArgumentException _) {
          return; // rejecting clauses it cannot honour is fine
        }
        // if accepted, +f:a -f:b must match doc 0 only, and each hit must explain as a match
        List<String> hits = new ArrayList<>();
        for (ScoreDoc sd : searcher.search(query, 10).scoreDocs) {
          hits.add(
              "doc " + sd.doc + " explain isMatch=" + searcher.explain(query, sd.doc).isMatch());
        }
        assertEquals(List.of("doc 0 explain isMatch=true"), hits);
      }
    }
  }
}
