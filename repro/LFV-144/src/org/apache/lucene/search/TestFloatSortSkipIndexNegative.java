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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestFloatSortSkipIndexNegative extends LuceneTestCase {

  public void testAscendingFloatSortWithNegativeValues() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      for (int i = 0; i < 10_000; i++) {
        Document doc = new Document();
        doc.add(new StringField("all", "x", Field.Store.NO));
        doc.add(NumericDocValuesField.indexedField("f", Float.floatToRawIntBits(-i)));
        w.addDocument(doc);
      }
      w.forceMerge(1);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        Sort sort = new Sort(new SortField("f", SortField.Type.FLOAT));
        List<Object> top3 = new ArrayList<>();
        for (ScoreDoc hit : searcher.search(new TermQuery(new Term("all", "x")), 3, sort).scoreDocs) {
          top3.add(((FieldDoc) hit).fields[0]);
        }
        // expected the three smallest values [-9999, -9998, -9997]; actual [-8191, -8190, -8189]
        assertEquals(List.of(-9999f, -9998f, -9997f), top3);
      }
    }
  }
}
