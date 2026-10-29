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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FeatureField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestWANDInfiniteMinCompetitiveScore extends LuceneTestCase {

  public void testInfiniteScoresSortedByScoreThenField() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      for (int i = 0; i < 10; i++) {
        Document doc = new Document();
        doc.add(new FeatureField("f", "a", 2e38f));
        doc.add(new FeatureField("f", "b", 2e38f));
        doc.add(new FeatureField("f", "c", 2e38f));
        doc.add(new NumericDocValuesField("rank", 10 - i));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        Query query =
            new BooleanQuery.Builder()
                .add(FeatureField.newLinearQuery("f", "a", 1f), BooleanClause.Occur.SHOULD)
                .add(FeatureField.newLinearQuery("f", "b", 1f), BooleanClause.Occur.SHOULD)
                .add(FeatureField.newLinearQuery("f", "c", 1f), BooleanClause.Occur.SHOULD)
                .setMinimumNumberShouldMatch(2)
                .build();
        Sort sort = new Sort(SortField.FIELD_SCORE, new SortField("rank", SortField.Type.INT));
        TopDocs top =
            new IndexSearcher(reader).search(query, new TopFieldCollectorManager(sort, 3, null, 3));
        // each doc scores 3 x 2e38 = +Infinity, so rank breaks the tie: expected docs 9, 8, 7
        assertEquals(
            "[9, 8, 7]",
            Arrays.toString(Arrays.stream(top.scoreDocs).mapToInt(sd -> sd.doc).toArray()));
      }
    }
  }
}
