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
package org.apache.lucene.queries.function;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.valuesource.DoubleFieldSource;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestFunctionRangeQueryNegativeScoreHitLoss extends LuceneTestCase {

  public void testMinShouldMatchKeepsNegativeInfinityDoc() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        Document doc0 = new Document();
        doc0.add(new DoubleDocValuesField("d", Double.NEGATIVE_INFINITY));
        doc0.add(new TextField("body", "a", Field.Store.NO));
        w.addDocument(doc0);
        Document doc1 = new Document();
        doc1.add(new DoubleDocValuesField("d", 1.0));
        doc1.add(new TextField("body", "b", Field.Store.NO));
        w.addDocument(doc1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query query =
            new BooleanQuery.Builder()
                .add(
                    new FunctionRangeQuery(
                        new DoubleFieldSource("d"), (Number) null, (Number) null, true, true),
                    BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("body", "a")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("body", "b")), BooleanClause.Occur.SHOULD)
                .setMinimumNumberShouldMatch(2)
                .build();
        // expected: all count(q) = 2 matches; actual: 1, doc 0 (FunctionRangeQuery score < 0) is lost
        assertEquals(
            "hits of search(q, 10)",
            searcher.count(query),
            searcher.search(query, 10).scoreDocs.length);
      }
    }
  }
}
