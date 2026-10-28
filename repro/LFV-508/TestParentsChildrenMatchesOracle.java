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

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.search.CheckHits;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The test framework's own Matches oracle (CheckHits.checkMatches, run by QueryUtils.check) asserts
 * both directions of the Weight#matches contract: non-null for every hit and null for the
 * non-matching document just before a hit. The join query alone, with no enclosing BooleanQuery
 * and no child limit, fails the null direction on a matching parent document. The sibling
 * ToParentBlockJoinQuery passes the same oracle on the same index (control).
 */
public class TestParentsChildrenMatchesOracle extends LuceneTestCase {

  private static Document doc(String type, String text) {
    Document d = new Document();
    d.add(new StringField("type", type, Field.Store.NO));
    d.add(new TextField("text", text, Field.Store.YES));
    return d;
  }

  private Directory dir;
  private DirectoryReader reader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      // block 1: doc 0 is a child of parent doc 1; block 2: doc 2 is a child of parent doc 3
      w.addDocuments(List.of(doc("child", "size small"), doc("parent", "red shirt")));
      w.addDocuments(List.of(doc("child", "size large"), doc("parent", "red coat")));
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private static BitSetProducer parents() {
    return new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
  }

  /** Hits are children 0 and 2; parent doc 1 lies between them and is not a hit. */
  public void testJoinAloneStockOracle() throws Exception {
    IndexSearcher searcher = new IndexSearcher(reader);
    Query join =
        new ParentsChildrenBlockJoinQuery(
            parents(), new TermQuery(new Term("text", "red")), new TermQuery(new Term("text", "size")));
    assertEquals(2, searcher.count(join));
    CheckHits.checkMatches(join, searcher);
  }

  /** Control: the sibling block join passes the same oracle on the same index. */
  public void testToParentControlStockOracle() throws Exception {
    IndexSearcher searcher = new IndexSearcher(reader);
    Query toParent =
        new ToParentBlockJoinQuery(new TermQuery(new Term("text", "size")), parents(), ScoreMode.None);
    assertEquals(2, searcher.count(toParent));
    CheckHits.checkMatches(toParent, searcher);
  }
}
