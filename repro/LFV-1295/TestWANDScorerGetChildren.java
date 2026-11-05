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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestWANDScorerGetChildren extends LuceneTestCase {

  public void testGetChildrenBeforeFirstDoc() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      Document doc = new Document();
      for (String t : new String[] {"a", "b", "c"}) {
        doc.add(new StringField("f", t, Field.Store.NO));
      }
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        BooleanQuery.Builder query = new BooleanQuery.Builder().setMinimumNumberShouldMatch(2);
        for (String t : new String[] {"a", "b", "c"}) {
          query.add(
              new ConstantScoreQuery(new TermQuery(new Term("f", t))), BooleanClause.Occur.SHOULD);
        }
        Weight weight = searcher.createWeight(query.build(), ScoreMode.TOP_SCORES, 1);
        Scorer scorer = weight.scorer(reader.leaves().get(0));
        // This is the state LeafCollector#setScorer sees. Expected: 3 children.
        // Actual with -ea: AssertionError in WANDScorer.ensureConsistent.
        assertEquals(3, scorer.getChildren().size());
      }
    }
  }
}
