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
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * ToParentBlockJoinSortField: the childFilter "defines which child documents participates in
 * sorting". A sibling that the childFilter excludes (here: a child of another type, which has no
 * value for the sort field) must not influence the parent's sort value.
 */
public class TestBlockJoinSortChildFilterMissing extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;
  private BitSetProducer parents;
  private BitSetProducer typeA;
  private Query query;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      // block A: a type-a child with value "z"/30, a type-b child without the field, parent "A"
      List<Document> docs = new ArrayList<>();
      docs.add(child("a", "z", 30));
      docs.add(child("b", null, 0));
      docs.add(parent("A"));
      w.addDocuments(docs);
      // block B: a type-a child with value "c"/20, parent "B"
      docs = new ArrayList<>();
      docs.add(child("a", "c", 20));
      docs.add(parent("B"));
      w.addDocuments(docs);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
    parents = new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
    typeA = new QueryBitSetProducer(new TermQuery(new Term("type", "a")));
    CheckJoinIndex.check(reader, parents);
    query = new ToParentBlockJoinQuery(new TermQuery(new Term("type", "a")), parents, ScoreMode.None);
  }

  private static Document child(String type, String s, long v) {
    Document d = new Document();
    d.add(new StringField("type", type, Field.Store.NO));
    if (s != null) {
      d.add(new SortedDocValuesField("s", new BytesRef(s)));
      d.add(new NumericDocValuesField("n", v));
    }
    return d;
  }

  private static Document parent(String id) {
    Document d = new Document();
    d.add(new StringField("type", "parent", Field.Store.NO));
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private String order(TopFieldDocs td) throws Exception {
    StringBuilder sb = new StringBuilder();
    for (ScoreDoc sd : td.scoreDocs) {
      Object v = ((FieldDoc) sd).fields[0];
      if (v instanceof BytesRef b) v = b.utf8ToString();
      sb.append(searcher.storedFields().document(sd.doc).get("id")).append('=').append(v).append(' ');
    }
    return sb.toString().trim();
  }

  /** Default STRING sort (no missing values configured), ascending: B ("c") before A ("z"). */
  public void testStringAscendingDefault() throws Exception {
    SortField sf = new ToParentBlockJoinSortField("s", SortField.Type.STRING, false, parents, typeA);
    assertEquals("B=c A=z", order(searcher.search(query, 10, new Sort(sf))));
  }

  /**
   * INT sort with childMissingValue=5: no type-a child lacks the field, so the missing value must
   * not take part; ascending order is B (20) before A (30).
   */
  public void testIntChildMissingValueIgnoresFilteredOutSiblings() throws Exception {
    SortField sf =
        new ToParentBlockJoinSortField(
            "n", SortField.Type.INT, false, null, 5, parents, typeA);
    assertEquals("B=20 A=30", order(searcher.search(query, 10, new Sort(sf))));
  }
}
