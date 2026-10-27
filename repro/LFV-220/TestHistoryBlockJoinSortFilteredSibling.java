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
 * Version-portable repro (STRING only; uses the 5-argument ToParentBlockJoinSortField constructor
 * that exists in 9.x and every 10.x release). childFilter = type:a in both tests.
 *
 * <p>testValuedSibling: block P1 = [type:a s=y, type:b s=x, parent P1], block P2 = [type:a s=d,
 * parent P2]. Every document in both blocks has the sort field, so no missing value can take part.
 *
 * <p>testFieldlessSibling: block A = [type:a s=z, type:b (no sort field), parent A], block B =
 * [type:a s=c, parent B]. The type:b document is outside childFilter.
 */
public class TestHistoryBlockJoinSortFilteredSibling extends LuceneTestCase {

  private BitSetProducer parents() {
    return new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
  }

  private BitSetProducer typeA() {
    return new QueryBitSetProducer(new TermQuery(new Term("type", "a")));
  }

  private static Document child(String type, String s) {
    Document d = new Document();
    d.add(new StringField("type", type, Field.Store.NO));
    if (s != null) {
      d.add(new SortedDocValuesField("s", new BytesRef(s)));
    }
    return d;
  }

  private static Document parent(String id) {
    Document d = new Document();
    d.add(new StringField("type", "parent", Field.Store.NO));
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  /** Indexes the blocks, sorts parents by MIN(s) over type:a children ascending, prints id=value. */
  private String sortAscending(List<List<Document>> blocks) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        for (List<Document> block : blocks) {
          w.addDocuments(block);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        BitSetProducer parents = parents();
        CheckJoinIndex.check(reader, parents);
        IndexSearcher searcher = new IndexSearcher(reader);
        ToParentBlockJoinQuery q =
            new ToParentBlockJoinQuery(
                new TermQuery(new Term("type", "a")), parents, ScoreMode.None);
        SortField sf =
            new ToParentBlockJoinSortField("s", SortField.Type.STRING, false, parents, typeA());
        StringBuilder sb = new StringBuilder();
        for (ScoreDoc sd : searcher.search(q, 10, new Sort(sf)).scoreDocs) {
          Object v = ((FieldDoc) sd).fields[0];
          if (v instanceof BytesRef) {
            v = ((BytesRef) v).utf8ToString();
          }
          sb.append(searcher.storedFields().document(sd.doc).get("id"))
              .append('=')
              .append(v)
              .append(' ');
        }
        return sb.toString().trim();
      }
    }
  }

  /** The excluded type:b sibling HAS the sort field. Expected P2=d, then P1=y. */
  public void testValuedSibling() throws Exception {
    List<List<Document>> blocks = new ArrayList<>();
    List<Document> b1 = new ArrayList<>();
    b1.add(child("a", "y"));
    b1.add(child("b", "x"));
    b1.add(parent("P1"));
    blocks.add(b1);
    List<Document> b2 = new ArrayList<>();
    b2.add(child("a", "d"));
    b2.add(parent("P2"));
    blocks.add(b2);
    assertEquals("P2=d P1=y", sortAscending(blocks));
  }

  /** The excluded type:b sibling LACKS the sort field. Filter-relative reading: B=c, then A=z. */
  public void testFieldlessSibling() throws Exception {
    List<List<Document>> blocks = new ArrayList<>();
    List<Document> b1 = new ArrayList<>();
    b1.add(child("a", "z"));
    b1.add(child("b", null));
    b1.add(parent("A"));
    blocks.add(b1);
    List<Document> b2 = new ArrayList<>();
    b2.add(child("a", "c"));
    b2.add(parent("B"));
    blocks.add(b2);
    assertEquals("B=c A=z", sortAscending(blocks));
  }
}
