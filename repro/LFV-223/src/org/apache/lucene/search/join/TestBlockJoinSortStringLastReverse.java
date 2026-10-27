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
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * ToParentBlockJoinSortField on a STRING field with reverseChildren=true (MAX selection) and
 * childMissingValue=STRING_LAST, which the javadoc documents as a legal child missing value. A
 * parent with a child that lacks the field makes the search throw.
 */
public class TestBlockJoinSortStringLastReverse extends LuceneTestCase {

  private static Document child(String v) {
    Document d = new Document();
    d.add(new StringField("type", "child", Field.Store.NO));
    if (v != null) {
      d.add(new SortedDocValuesField("s", new BytesRef(v)));
    }
    return d;
  }

  private static Document parent() {
    Document d = new Document();
    d.add(new StringField("type", "parent", Field.Store.NO));
    return d;
  }

  public void testStringLastWithReverseChildren() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        w.addDocuments(List.of(child("b"), child(null), parent())); // parent A: one child lacks "s"
        w.addDocuments(List.of(child("c"), parent())); // parent B
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        BitSetProducer parents = new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
        BitSetProducer children = new QueryBitSetProducer(new TermQuery(new Term("type", "child")));
        CheckJoinIndex.check(reader, parents);
        Query q =
            new ToParentBlockJoinQuery(
                new TermQuery(new Term("type", "child")), parents, ScoreMode.None);
        for (boolean reverseParents : new boolean[] {false, true}) {
          SortField sf =
              new ToParentBlockJoinSortField(
                  "s",
                  SortField.Type.STRING,
                  reverseParents,
                  true, // reverseChildren: take the max child value
                  null,
                  SortField.STRING_LAST, // documented legal value for Type.STRING
                  parents,
                  children);
          TopFieldDocs td = searcher.search(q, 10, new Sort(sf));
          assertEquals(2, td.scoreDocs.length);
        }
      }
    }
  }
}
