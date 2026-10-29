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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestRescoreTopNQueryNegativeValues extends LuceneTestCase {

  public void testDisjunctionReturnsAllMatches() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("v", -2));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query q =
            new BooleanQuery.Builder()
                .add(
                    new RescoreTopNQuery(
                        MatchAllDocsQuery.INSTANCE, DoubleValuesSource.fromLongField("v"), 1),
                    BooleanClause.Occur.SHOULD)
                .add(MatchAllDocsQuery.INSTANCE, BooleanClause.Occur.SHOULD)
                .build();
        // expected: the doc matches the MatchAllDocsQuery clause, so 1 hit; actual: 0 hits
        assertEquals(1, new IndexSearcher(reader).search(q, 10).scoreDocs.length);
      }
    }
  }
}
