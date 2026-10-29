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
package org.apache.lucene.search.grouping;

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBlockGroupingMaxScoreOffset extends LuceneTestCase {

  public void testMaxScoreWithGroupDocsOffset() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document top = new Document();
      top.add(new StringField("id", "top", Field.Store.NO));
      Document end = new Document();
      end.add(new StringField("groupEnd", "x", Field.Store.NO));
      w.addDocuments(List.of(top, end));
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        Query query =
            new BooleanQuery.Builder()
                .add(MatchAllDocsQuery.INSTANCE, Occur.SHOULD)
                .add(new ConstantScoreQuery(new TermQuery(new Term("id", "top"))), Occur.SHOULD)
                .build();
        GroupingSearch grouping = new GroupingSearch(new TermQuery(new Term("groupEnd", "x")));
        grouping.setGroupDocsOffset(1);
        TopGroups<?> result = grouping.search(new IndexSearcher(reader), query, 0, 10);
        // expected 2.0, the best score in the group; actual 1.0, the first score on the page
        assertEquals("group maxScore", 2f, result.groups[0].maxScore(), 0f);
      }
    }
  }
}
