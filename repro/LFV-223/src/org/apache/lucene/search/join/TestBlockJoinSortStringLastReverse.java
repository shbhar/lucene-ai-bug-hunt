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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

public class TestBlockJoinSortStringLastReverse extends LuceneTestCase {

  private static Document doc(String type, String s) {
    Document d = new Document();
    d.add(new StringField("type", type, Field.Store.NO));
    if (s != null) {
      d.add(new SortedDocValuesField("s", new BytesRef(s)));
    }
    return d;
  }

  public void testStringLastWithReverseChildren() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        w.addDocuments(List.of(doc("child", "b"), doc("child", null), doc("parent", null)));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        BitSetProducer parents = new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
        Query children = new TermQuery(new Term("type", "child"));
        SortField sf =
            new ToParentBlockJoinSortField(
                "s",
                SortField.Type.STRING,
                false,
                true,
                null,
                SortField.STRING_LAST,
                parents,
                new QueryBitSetProducer(children));
        Query q = new ToParentBlockJoinQuery(children, parents, ScoreMode.None);
        try {
          assertEquals(1, new IndexSearcher(reader).search(q, 10, new Sort(sf)).scoreDocs.length);
        } catch (IndexOutOfBoundsException e) {
          // expected: the parent is returned; actual: lookupOrd(Integer.MAX_VALUE) throws
          throw new AssertionError("expected 1 sorted hit, got " + e, e);
        }
      }
    }
  }
}
