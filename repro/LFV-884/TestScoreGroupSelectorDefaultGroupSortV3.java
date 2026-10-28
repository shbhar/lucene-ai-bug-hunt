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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Verifier probe: the default (relevance) group sort, so the first pass requests scores, but a
 * field within-group sort and includeMaxScore=false, so only the second pass runs without scores.
 * Five documents score 1, 2, 3, 1, 3 for a OR b OR c of constant-score clauses.
 */
public class TestScoreGroupSelectorDefaultGroupSortV3 extends LuceneTestCase {

  private static final String[][] TAGS = {{"a"}, {"a", "b"}, {"a", "b", "c"}, {"a"}, {"a", "b", "c"}};

  private static Query query() {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (String t : new String[] {"a", "b", "c"}) {
      b.add(new ConstantScoreQuery(new TermQuery(new Term("tag", t))), Occur.SHOULD);
    }
    return b.build();
  }

  private String run(boolean includeMaxScore) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < TAGS.length; i++) {
          Document d = new Document();
          for (String t : TAGS[i]) {
            d.add(new StringField("tag", t, Field.Store.NO));
          }
          d.add(new NumericDocValuesField("id", i));
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        GroupingSearch g =
            new GroupingSearch(
                () ->
                    new DoubleRangeGroupSelector(
                        DoubleValuesSource.SCORES, new DoubleRangeFactory(0, 1, 10)));
        // group sort left at its default (relevance)
        g.setSortWithinGroup(new Sort(new SortField("id", SortField.Type.INT)));
        g.setGroupDocsLimit(10);
        g.setIncludeMaxScore(includeMaxScore);
        TopGroups<DoubleRange> tg = g.search(s, query(), 0, 10);
        Map<String, List<Integer>> m = new TreeMap<>();
        for (GroupDocs<DoubleRange> gd : tg.groups) {
          List<Integer> docs = new ArrayList<>();
          for (ScoreDoc sd : gd.scoreDocs()) {
            docs.add(sd.doc);
          }
          Collections.sort(docs);
          m.put(String.valueOf(gd.groupValue()), docs);
        }
        return m.toString();
      }
    }
  }

  private static final String EXPECTED =
      "{DoubleRange(1.0, 2.0)=[0, 3], DoubleRange(2.0, 3.0)=[1], DoubleRange(3.0, 4.0)=[2, 4]}";

  /** Control: includeMaxScore=true makes the second pass request scores. */
  public void testIncludeMaxScoreControl() throws Exception {
    assertEquals(EXPECTED, run(true));
  }

  /** Same search with includeMaxScore=false: only the second pass runs without scores. */
  public void testNoMaxScoreSecondPass() throws Exception {
    assertEquals(EXPECTED, run(false));
  }
}
