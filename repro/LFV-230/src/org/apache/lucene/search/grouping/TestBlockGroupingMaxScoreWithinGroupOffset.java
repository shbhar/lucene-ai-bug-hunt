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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Block grouping (GroupingSearch(Query groupEndDocs) / BlockGroupingCollector) reports the score
 * of the first document AFTER the within-group offset as the group's maxScore when documents in
 * the group are sorted by relevance (the default). GroupDocs.maxScore is documented as "Max score
 * in this group", and two-pass term grouping over the same documents reports the true maximum.
 *
 * <p>Index: two blocks. Block "a" has scores 5, 3, 1; block "b" has scores 4, 2. The score of
 * each document is the value of its "score" doc-values field.
 */
public class TestBlockGroupingMaxScoreWithinGroupOffset extends LuceneTestCase {

  private static Document doc(String group, int score, boolean last) {
    Document d = new Document();
    d.add(new SortedDocValuesField("group", new BytesRef(group)));
    d.add(new NumericDocValuesField("score", score));
    if (last) {
      d.add(new StringField("groupEnd", "x", Field.Store.NO));
    }
    return d;
  }

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;
  private Query query;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocuments(List.of(doc("a", 5, false), doc("a", 3, false), doc("a", 1, true)));
      w.addDocuments(List.of(doc("b", 4, false), doc("b", 2, true)));
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader); // sequential search, no executor
    query =
        new FunctionScoreQuery(new MatchAllDocsQuery(), DoubleValuesSource.fromIntField("score"));
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private TopGroups<?> blockSearch(int groupDocsOffset) throws Exception {
    GroupingSearch gs = new GroupingSearch(new TermQuery(new Term("groupEnd", "x")));
    // group sort and within-group sort are left at their default, Sort.RELEVANCE
    gs.setGroupDocsOffset(groupDocsOffset).setGroupDocsLimit(1).setIncludeMaxScore(true);
    return gs.search(searcher, query, 0, 10);
  }

  private TopGroups<?> termSearch(int groupDocsOffset) throws Exception {
    GroupingSearch gs = new GroupingSearch("group");
    gs.setGroupDocsOffset(groupDocsOffset).setGroupDocsLimit(1).setIncludeMaxScore(true);
    return gs.search(searcher, query, 0, 10);
  }

  /** Control: without a within-group offset both grouping implementations agree. */
  public void testNoOffsetControl() throws Exception {
    TopGroups<?> block = blockSearch(0);
    assertEquals(2, block.groups.length);
    assertEquals(5f, block.groups[0].maxScore(), 0f);
    assertEquals(4f, block.groups[1].maxScore(), 0f);
    assertEquals(5f, block.maxScore, 0f);
  }

  /** Paging inside groups (second document of each group): maxScore must not change. */
  public void testGroupMaxScoreWithWithinGroupOffset() throws Exception {
    TopGroups<?> term = termSearch(1);
    assertEquals("term grouping: group a maxScore", 5f, term.groups[0].maxScore(), 0f);
    assertEquals("term grouping: group b maxScore", 4f, term.groups[1].maxScore(), 0f);

    TopGroups<?> block = blockSearch(1);
    assertEquals(2, block.groups.length);
    // the page itself is right: the second-best document of each group
    assertEquals(1, block.groups[0].scoreDocs()[0].doc);
    assertEquals(4, block.groups[1].scoreDocs()[0].doc);
    assertEquals("block grouping: group a maxScore", 5f, block.groups[0].maxScore(), 0f);
    assertEquals("block grouping: group b maxScore", 4f, block.groups[1].maxScore(), 0f);
    assertEquals("block grouping: TopGroups.maxScore", 5f, block.maxScore, 0f);
  }

  /** Offset past the end of a group: maxScore becomes -Infinity although the group has hits. */
  public void testGroupMaxScoreWhenOffsetPastGroupEnd() throws Exception {
    TopGroups<?> block = blockSearch(2);
    assertEquals(2, block.groups.length);
    assertEquals(2L, block.groups[1].totalHits().value());
    assertEquals(0, block.groups[1].scoreDocs().length);
    assertEquals("block grouping: group b maxScore", 4f, block.groups[1].maxScore(), 0f);
  }
}
