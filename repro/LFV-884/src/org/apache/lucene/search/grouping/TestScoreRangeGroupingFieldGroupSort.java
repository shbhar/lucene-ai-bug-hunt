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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestScoreRangeGroupingFieldGroupSort extends LuceneTestCase {

  public void testGroupByScoreRangeSortedByField() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc0 = new Document();
        doc0.add(new StringField("tag", "a", Field.Store.NO));
        doc0.add(new NumericDocValuesField("id", 0));
        w.addDocument(doc0);
        Document doc1 = new Document();
        doc1.add(new StringField("tag", "a", Field.Store.NO));
        doc1.add(new StringField("tag", "b", Field.Store.NO));
        doc1.add(new NumericDocValuesField("id", 1));
        w.addDocument(doc1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query q =
            new BooleanQuery.Builder()
                .add(new ConstantScoreQuery(new TermQuery(new Term("tag", "a"))), Occur.SHOULD)
                .add(new ConstantScoreQuery(new TermQuery(new Term("tag", "b"))), Occur.SHOULD)
                .build();
        GroupingSearch grouping =
            new GroupingSearch(
                () ->
                    new DoubleRangeGroupSelector(
                        DoubleValuesSource.SCORES, new DoubleRangeFactory(0, 1, 10)));
        grouping.setGroupSort(new Sort(new SortField("id", SortField.Type.INT)));
        TopGroups<DoubleRange> groups = grouping.search(new IndexSearcher(reader), q, 0, 10);
        List<DoubleRange> ranges = new ArrayList<>();
        for (GroupDocs<DoubleRange> group : groups.groups) {
          ranges.add(group.groupValue());
        }
        // doc0 scores 1 and doc1 scores 2: expected [1,2) and [2,3); actual one group [0,1)
        assertEquals(List.of(new DoubleRange(1, 2), new DoubleRange(2, 3)), ranges);
      }
    }
  }
}
