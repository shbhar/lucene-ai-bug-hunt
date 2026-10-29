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

import java.util.Arrays;
import java.util.List;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFunctionScoreNegativeZero extends LuceneTestCase {

  public void testTopOneIsHeadOfTopTwo() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(List.of(new FloatDocValuesField("f", -0f)));
        w.addDocument(List.of(new FloatDocValuesField("f", 0f)));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query q = new FunctionScoreQuery(new MatchAllDocsQuery(), DoubleValuesSource.fromFloatField("f"));
        TopDocs top1 = searcher.search(q, 1);
        TopDocs top2 = searcher.search(q, 2);
        // expected: both docs score 0 (negative values clamp to 0), so top-1 is the head of top-2; actual: doc 0 scores -0.0 and the two disagree
        assertEquals("top-2: " + Arrays.toString(top2.scoreDocs), top2.scoreDocs[0].doc, top1.scoreDocs[0].doc);
      }
    }
  }
}
