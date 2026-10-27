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
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Two blocks. Block P1: child(type=a, s=y, n=40), child(type=b, s=x, n=7), parent P1. Block P2:
 * child(type=a, s=d, n=10), parent P2. childFilter = type:a. Every non-parent document has both
 * sort fields, so no child lacks a value and no missing value may take part in the sort.
 */
public class TestBlockJoinSortFilteredSibling extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;
  private BitSetProducer parents;
  private BitSetProducer typeA;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w =
        new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      List<Document> b1 = new ArrayList<>();
      b1.add(child("a", "y", 40));
      b1.add(child("b", "x", 7));
      b1.add(parent("P1"));
      w.addDocuments(b1);
      List<Document> b2 = new ArrayList<>();
      b2.add(child("a", "d", 10));
      b2.add(parent("P2"));
      w.addDocuments(b2);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
    parents = new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
    typeA = new QueryBitSetProducer(new TermQuery(new Term("type", "a")));
    CheckJoinIndex.check(reader, parents);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private static Document child(String type, String s, long n) {
    Document d = new Document();
    d.add(new StringField("type", type, Field.Store.NO));
    d.add(new SortedDocValuesField("s", new BytesRef(s)));
    d.add(new NumericDocValuesField("n", n));
    return d;
  }

  private static Document parent(String id) {
    Document d = new Document();
    d.add(new StringField("type", "parent", Field.Store.NO));
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  /** Sorts the parents of the type:a children and prints "id=sortValue" per hit. */
  private String run(SortField sf) throws Exception {
    ToParentBlockJoinQuery q =
        new ToParentBlockJoinQuery(new TermQuery(new Term("type", "a")), parents, ScoreMode.None);
    StringBuilder sb = new StringBuilder();
    for (ScoreDoc sd : searcher.search(q, 10, new Sort(sf)).scoreDocs) {
      Object v = ((FieldDoc) sd).fields[0];
      if (v instanceof BytesRef b) v = b.utf8ToString();
      sb.append(searcher.storedFields().document(sd.doc).get("id"))
          .append('=')
          .append(v)
          .append(' ');
    }
    return sb.toString().trim();
  }

  /** Default STRING ascending (MIN over the type:a children): P2=d, then P1=y. */
  public void testStringAscending() throws Exception {
    assertEquals(
        "P2=d P1=y",
        run(new ToParentBlockJoinSortField("s", SortField.Type.STRING, false, parents, typeA)));
  }

  /** LONG ascending, childMissingValue=0 for children that lack the field (none do). */
  public void testLongChildMissingValue() throws Exception {
    assertEquals(
        "P2=10 P1=40",
        run(
            new ToParentBlockJoinSortField(
                "n", SortField.Type.LONG, false, null, 0L, parents, typeA)));
  }
}
