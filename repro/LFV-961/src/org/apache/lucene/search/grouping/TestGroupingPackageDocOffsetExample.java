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
import java.util.Collection;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * The grouping package documentation defines the inputs as "topNGroups: how many top groups to keep" and
 * "groupOffset: ... For example, 3 means you'll get 7 groups back (assuming topNGroups is 10)", and names
 * FirstPassGroupingCollectorManager as the first pass (and shows BlockGroupingCollectorManager with the same
 * groupOffset/topNGroups arguments). Both managers add the offset to topNGroups, so they return 10 groups.
 */
public class TestGroupingPackageDocOffsetExample extends LuceneTestCase {
  private static final Sort SORT = new Sort(new SortField("p", SortField.Type.LONG));
  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int g = 0; g < 15; g++) { // 15 groups of one document each, indexed as one-document blocks
        Document d = new Document();
        d.add(new SortedDocValuesField("g", new BytesRef("g" + g)));
        d.add(new NumericDocValuesField("p", g));
        d.add(new StringField("end", "y", Field.Store.NO));
        w.addDocuments(List.of(d));
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /** Control: the collector the paragraph named before 10.6 behaves as documented. */
  public void testFirstPassCollectorMatchesPackageExample() throws Exception {
    FirstPassGroupingCollector<BytesRef> c = new FirstPassGroupingCollector<>(new TermGroupSelector("g"), SORT, 10);
    for (LeafReaderContext ctx : reader.leaves()) {
      LeafCollector lc = c.getLeafCollector(ctx);
      for (int doc = 0; doc < ctx.reader().maxDoc(); doc++) {
        lc.collect(doc);
      }
    }
    assertEquals(7, c.getTopGroups(3).size());
  }

  public void testFirstPassManagerMatchesPackageExample() throws Exception {
    Collection<SearchGroup<BytesRef>> groups =
        searcher.search(
            MatchAllDocsQuery.INSTANCE,
            new FirstPassGroupingCollectorManager<>(() -> new TermGroupSelector("g"), SORT, 3, 10));
    assertEquals("groupOffset=3, topNGroups=10: 'you'll get 7 groups back'", 7, groups.size());
  }

  public void testBlockManagerMatchesPackageExample() throws Exception {
    Weight lastDocPerGroup =
        searcher.createWeight(
            searcher.rewrite(new TermQuery(new Term("end", "y"))), ScoreMode.COMPLETE_NO_SCORES, 1);
    TopGroups<?> tg =
        searcher.search(
            MatchAllDocsQuery.INSTANCE,
            new BlockGroupingCollectorManager<>(SORT, 3, 10, false, lastDocPerGroup, SORT, 0, 1));
    assertEquals("groupOffset=3, topNGroups=10: 'you'll get 7 groups back'", 7, tg.groups.length);
  }
}
