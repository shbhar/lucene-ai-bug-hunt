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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestTermJoinAvgScore extends LuceneTestCase {

  public void testAvgOfThreeEqualChildScores() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document parent = new Document();
      parent.add(new StringField("id", "p", Field.Store.NO));
      w.addDocument(parent);
      Document child = new Document();
      child.add(new SortedDocValuesField("parent", new BytesRef("p")));
      w.addDocument(child);
      w.addDocument(child);
      w.addDocument(child);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query children = new BoostQuery(new FieldExistsQuery("parent"), 1.7f);
        Query join =
            JoinUtil.createJoinQuery("parent", false, "id", children, searcher, ScoreMode.Avg);
        // expected: 1.7, the average of the three child scores (1.7 each); actual: 1.7000002
        assertEquals(1.7f, searcher.search(join, 1).scoreDocs[0].score, 0f);
      }
    }
  }
}
