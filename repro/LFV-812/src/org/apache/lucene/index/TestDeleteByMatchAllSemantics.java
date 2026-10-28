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
package org.apache.lucene.index;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * The javadoc of {@link IndexWriter#deleteAll()} says it "is much faster than using
 * deleteDocuments(MatchAllDocsQuery.INSTANCE)" and that, unlike {@link
 * IndexWriter#deleteDocuments(Query...)}, it clears doc values types ("a delete query only marks
 * documents as deleted"). deleteDocuments(Query...) however routes an exact MatchAllDocsQuery to
 * deleteAll(), so deleting by MatchAllDocsQuery also clears the field schema.
 */
public class TestDeleteByMatchAllSemantics extends LuceneTestCase {

  private static void checkDeleteQueryKeepsDocValuesType(Query deleteQuery) throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
      Document numeric = new Document();
      numeric.add(new NumericDocValuesField("f", 1));
      w.addDocument(numeric);
      w.commit();

      // per the deleteAll() javadoc, a delete query only marks documents as deleted and keeps
      // the doc values type of "f" (NUMERIC)
      w.deleteDocuments(deleteQuery);

      Document sorted = new Document();
      sorted.add(new SortedDocValuesField("f", new BytesRef("x")));
      IllegalArgumentException e =
          expectThrows(IllegalArgumentException.class, () -> w.addDocument(sorted));
      assertTrue(e.getMessage(), e.getMessage().contains("doc values type"));
    }
  }

  /** Control: a query that matches every document but is not a MatchAllDocsQuery. */
  public void testDeleteByWrappedMatchAllKeepsDocValuesType() throws IOException {
    checkDeleteQueryKeepsDocValuesType(new ConstantScoreQuery(MatchAllDocsQuery.INSTANCE));
  }

  /** Fails on main: deleting by MatchAllDocsQuery silently clears the doc values type. */
  public void testDeleteByMatchAllKeepsDocValuesType() throws IOException {
    checkDeleteQueryKeepsDocValuesType(MatchAllDocsQuery.INSTANCE);
  }
}
