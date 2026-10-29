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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBlockGroupingIncludeMaxScore extends LuceneTestCase {

  public void testMaxScoreWithFieldSorts() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document(); // a block of one document
        doc.add(new NumericDocValuesField("id", 0));
        doc.add(new StringField("groupEnd", "x", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Sort byId = new Sort(new SortField("id", SortField.Type.INT));
        GroupingSearch grouping = new GroupingSearch(new TermQuery(new Term("groupEnd", "x")));
        grouping.setGroupSort(byId).setSortWithinGroup(byId).setIncludeMaxScore(true);
        TopGroups<?> groups = grouping.search(new IndexSearcher(r), new MatchAllDocsQuery(), 0, 10);
        // expected the matching document's score 1.0 (setIncludeMaxScore(true)), actual NaN
        assertEquals(1f, groups.maxScore, 0f);
      }
    }
  }
}
