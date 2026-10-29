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
package org.apache.lucene.search.join;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNumericJoinNegativeFloats extends LuceneTestCase {

  public void testFloatJoinOnTwoNegativeValues() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (float v : new float[] {-1f, -2f}) {
        Document doc = new Document();
        doc.add(new FloatDocValuesField("from", v));
        doc.add(new FloatPoint("to", v));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query fromQuery = new MatchAllDocsQuery();
        Query join;
        try {
          join =
              JoinUtil.createJoinQuery(
                  "from", false, "to", Float.class, fromQuery, searcher, ScoreMode.None);
        } catch (IllegalArgumentException e) {
          // expected: a join query matching the 2 docs (to = -1 and -2); actual: it throws
          throw new AssertionError("expected a join on from-values {-1, -2}, but threw " + e, e);
        }
        assertEquals(2, searcher.count(join));
      }
    }
  }
}
