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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestFunctionScoreQueryBooleanTopHit extends LuceneTestCase {

  public void testTopHitWithSkipIndexedValueField() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      for (int i = 0; i < 4097; i++) {
        Document doc = new Document();
        doc.add(new StringField("body", "a", Field.Store.NO));
        if (i == 0) {
          doc.add(new StringField("body", "b", Field.Store.NO));
        }
        doc.add(NumericDocValuesField.indexedField("popularity", i == 4096 ? 100 : 1));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        Query query =
            new BooleanQuery.Builder()
                .add(
                    new FunctionScoreQuery(
                        new TermQuery(new Term("body", "a")),
                        DoubleValuesSource.fromLongField("popularity")),
                    Occur.SHOULD)
                .add(new TermQuery(new Term("body", "b")), Occur.SHOULD)
                .build();
        ScoreDoc top = new IndexSearcher(reader).search(query, 1).scoreDocs[0];
        // expected doc 4096 (score 100 = its popularity); main returns doc 0 (score 1 + BM25 of b)
        assertEquals("top hit (returned score " + top.score + ")", 4096, top.doc);
      }
    }
  }
}
