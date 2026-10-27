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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.function.valuesource.DoubleFieldSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Since LUCENE-7996 scorers must not produce negative scores (MIGRATE.md), and FunctionQuery /
 * FunctionScoreQuery map negative function values to 0. FunctionRangeQuery scores a matching
 * document with the raw function value instead. A plain IndexSearcher is used so that the score
 * is observed as an application sees it (the test framework's asserting searcher rejects it).
 */
public class TestFunctionRangeQueryNegativeScore extends LuceneTestCase {
  public void testNegativeValueScore() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new DoubleDocValuesField("d", -5.0));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        ValueSource vs = new DoubleFieldSource("d");
        TopDocs fq = s.search(new FunctionQuery(vs), 1);
        assertEquals(0f, fq.scoreDocs[0].score, 0f); // the LUCENE-7996 clamp
        TopDocs frq = s.search(new FunctionRangeQuery(vs, -10.0, 10.0, true, true), 1);
        assertEquals(1, frq.totalHits.value());
        assertTrue("FunctionRangeQuery score " + frq.scoreDocs[0].score + " must not be negative",
            frq.scoreDocs[0].score >= 0);
      }
    }
  }
}
