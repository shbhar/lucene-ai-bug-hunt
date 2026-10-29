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
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestParentsChildrenMatchesParentDoc extends LuceneTestCase {

  public void testParentDocHasNoMatches() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document child = new Document();
        child.add(new TextField("text", "size", Field.Store.NO));
        Document parent = new Document();
        parent.add(new StringField("type", "parent", Field.Store.NO));
        parent.add(new TextField("text", "red", Field.Store.NO));
        w.addDocuments(List.of(child, parent));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(r);
        Query join =
            new ParentsChildrenBlockJoinQuery(
                new QueryBitSetProducer(new TermQuery(new Term("type", "parent"))),
                new TermQuery(new Term("text", "red")),
                new TermQuery(new Term("text", "size")));
        Weight weight =
            searcher.createWeight(searcher.rewrite(join), ScoreMode.COMPLETE_NO_SCORES, 1f);
        // the only hit is child doc 0: expected null Matches for parent doc 1, actual parentQuery's
        assertNull("Matches of parent doc 1", weight.matches(r.leaves().get(0), 1));
      }
    }
  }
}
