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
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * GroupingSearch with groupLimit or groupDocsLimit = Integer.MAX_VALUE ("all remaining groups / documents") should
 * return the same result as a limit of maxDoc, the way IndexSearcher.search(query, Integer.MAX_VALUE) caps n at
 * maxDoc. Instead the offset + limit sums wrap to a negative int and the search throws.
 */
public class TestGroupingSearchNoLimit extends LuceneTestCase {
  private static final int MAX = Integer.MAX_VALUE;
  private static final Sort GROUP_SORT = new Sort(new SortField("p", SortField.Type.LONG));
  private static final Sort DOC_SORT = new Sort(new SortField("p", SortField.Type.LONG));

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;
  private final Query query = new TermQuery(new Term("q", "x"));

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      // 5 groups of 4 documents, each group indexed as one block whose last document is marked
      for (int g = 0; g < 5; g++) {
        List<Document> block = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
          Document doc = new Document();
          doc.add(new SortedDocValuesField("g", new BytesRef("g" + g)));
          doc.add(new NumericDocValuesField("p", 10L * g + i));
          doc.add(new StringField("q", "x", Field.Store.NO));
          if (i == 3) {
            doc.add(new StringField("end", "y", Field.Store.NO));
          }
          block.add(doc);
        }
        w.addDocuments(block);
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

  private GroupingSearch termGrouping() {
    return new GroupingSearch("g").setGroupSort(GROUP_SORT).setSortWithinGroup(DOC_SORT);
  }

  private GroupingSearch blockGrouping() {
    return new GroupingSearch(new TermQuery(new Term("end", "y")))
        .setGroupSort(GROUP_SORT)
        .setSortWithinGroup(DOC_SORT);
  }

  private static String render(TopGroups<?> tg) {
    StringBuilder sb = new StringBuilder("totalHitCount=" + tg.totalHitCount + " groups=[");
    for (GroupDocs<?> g : tg.groups) {
      sb.append('{');
      for (ScoreDoc sd : g.scoreDocs()) {
        sb.append(sd.doc).append(' ');
      }
      sb.append('}');
    }
    return sb.append(']').toString();
  }

  /** Runs the grouped search; any exception becomes an assertion failure that names the arguments. */
  private String search(GroupingSearch gs, int groupOffset, int groupLimit, String what) {
    try {
      return render(gs.search(searcher, query, groupOffset, groupLimit));
    } catch (Throwable t) {
      throw new AssertionError(what + " threw " + t, t);
    }
  }

  private void checkGroupLimit(boolean block, int groupOffset) {
    String mode = block ? "block" : "term";
    String expected = search(block ? blockGrouping() : termGrouping(), groupOffset, reader.maxDoc(), "groupLimit=maxDoc");
    String actual =
        search(
            block ? blockGrouping() : termGrouping(),
            groupOffset,
            MAX,
            mode + " grouping, groupOffset=" + groupOffset + " groupLimit=MAX_VALUE");
    assertEquals(mode + " groupOffset=" + groupOffset, expected, actual);
  }

  private void checkGroupDocsLimit(boolean block, int docOffset) {
    String mode = block ? "block" : "term";
    GroupingSearch ref = (block ? blockGrouping() : termGrouping()).setGroupDocsOffset(docOffset);
    String expected = search(ref.setGroupDocsLimit(reader.maxDoc()), 0, 10, "groupDocsLimit=maxDoc");
    GroupingSearch gs = (block ? blockGrouping() : termGrouping()).setGroupDocsOffset(docOffset);
    String actual =
        search(
            gs.setGroupDocsLimit(MAX),
            0,
            10,
            mode + " grouping, groupDocsOffset=" + docOffset + " groupDocsLimit=MAX_VALUE");
    assertEquals(mode + " groupDocsOffset=" + docOffset, expected, actual);
  }

  public void testTermGroupLimitMaxValueOffset0() {
    checkGroupLimit(false, 0);
  }

  public void testTermGroupLimitMaxValueOffset1() {
    checkGroupLimit(false, 1);
  }

  public void testTermGroupDocsLimitMaxValueOffset0() {
    checkGroupDocsLimit(false, 0);
  }

  public void testTermGroupDocsLimitMaxValueOffset1() {
    checkGroupDocsLimit(false, 1);
  }

  public void testBlockGroupLimitMaxValueOffset0() {
    checkGroupLimit(true, 0);
  }

  public void testBlockGroupLimitMaxValueOffset1() {
    checkGroupLimit(true, 1);
  }

  public void testBlockGroupDocsLimitMaxValueOffset0() {
    checkGroupDocsLimit(true, 0);
  }

  public void testBlockGroupDocsLimitMaxValueOffset1() {
    checkGroupDocsLimit(true, 1);
  }
}
