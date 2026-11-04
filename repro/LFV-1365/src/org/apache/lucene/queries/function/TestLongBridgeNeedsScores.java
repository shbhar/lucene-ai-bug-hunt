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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.function.valuesource.LinearFloatFunction;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLongBridgeNeedsScores extends LuceneTestCase {

  public void testSortOnLongBridgeOfScore() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(new Document());
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        ValueSource score =
            new LinearFloatFunction(
                ValueSource.fromDoubleValuesSource(DoubleValuesSource.SCORES), 1f, 0f);
        Sort sort = new Sort(score.asLongValuesSource().getSortField(false));
        FieldDoc hit =
            (FieldDoc)
                new IndexSearcher(reader)
                    .search(new BoostQuery(new ConstantScoreQuery(new MatchAllDocsQuery()), 3f), 1, sort)
                    .scoreDocs[0];
        // expected: the sort value is the hit's score (3); actual on main: 0, scores are never computed
        assertEquals(3L, hit.fields[0]);
      }
    }
  }
}
