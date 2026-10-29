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
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestBlockJoinMustNotExcludesParent extends LuceneTestCase {

  public void testMustNotExcludesParent() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        // docs 0,1 = children, doc 2 = parent p1 (visible)
        // docs 3,4 = children, doc 5 = parent p2 (hidden)
        for (String[] p : new String[][] {{"p1", "visible", "python"}, {"p2", "hidden", "go"}}) {
          List<Document> block = new ArrayList<>();
          for (String skill : new String[] {"java", p[2]}) {
            Document child = new Document();
            child.add(new StringField("skill", skill, Field.Store.NO));
            block.add(child);
          }
          Document parent = new Document();
          parent.add(new StringField("type", "parent", Field.Store.NO));
          parent.add(new StringField("id", p[0], Field.Store.YES));
          parent.add(new StringField("status", p[1], Field.Store.NO));
          block.add(parent);
          w.addDocuments(block);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        BitSetProducer parents = new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
        CheckJoinIndex.check(reader, parents);

        Query join =
            new ToParentBlockJoinQuery(
                new TermQuery(new Term("skill", "java")), parents, ScoreMode.Avg);
        Query q =
            new BooleanQuery.Builder()
                .add(join, Occur.MUST)
                .add(new TermQuery(new Term("status", "hidden")), Occur.MUST_NOT)
                .build();

        assertEquals(1, searcher.count(q)); // the non-scoring path excludes p2
        List<String> ids = new ArrayList<>();
        for (ScoreDoc sd : searcher.search(q, 10).scoreDocs) {
          ids.add(searcher.storedFields().document(sd.doc).get("id"));
        }
        assertEquals(List.of("p1"), ids);
      }
    }
  }
}
