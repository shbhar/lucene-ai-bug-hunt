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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestParentsChildrenChildLimitAdvance extends LuceneTestCase {

  private static Document doc(String type, String id) {
    Document d = new Document();
    d.add(new StringField("type", type, Field.Store.NO));
    d.add(new StringField("id", id, Field.Store.NO));
    return d;
  }

  public void testAdvanceHonoursChildLimitPerParent() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocuments(List.of(doc("child", "a"), doc("child", "b"), doc("parent", "p1")));
      w.addDocuments(List.of(doc("child", "c"), doc("parent", "p2")));
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        Query parent = new TermQuery(new Term("type", "parent"));
        Query join =
            new ParentsChildrenBlockJoinQuery(
                new QueryBitSetProducer(parent), parent, new TermQuery(new Term("type", "child")), 1);
        Query joinAndB =
            new BooleanQuery.Builder()
                .add(join, Occur.MUST)
                .add(new TermQuery(new Term("id", "b")), Occur.FILTER)
                .build();
        // expected: join alone matches {a, c}, so join AND id:b is empty and b is not a match; actual: b matches
        assertEquals(2, searcher.count(join));
        assertEquals(0, searcher.count(joinAndB));
        assertFalse(searcher.explain(join, 1).isMatch());
      }
    }
  }
}
