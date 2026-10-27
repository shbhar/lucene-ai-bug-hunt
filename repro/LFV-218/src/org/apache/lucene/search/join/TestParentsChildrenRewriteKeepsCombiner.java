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
import java.util.function.BinaryOperator;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * ParentsChildrenBlockJoinQuery with a custom scoreCombiner: when the parent or child query
 * rewrites (here a one-clause BooleanQuery), rewrite() builds the new query without the combiner,
 * so the default Float::sum is used.
 */
public class TestParentsChildrenRewriteKeepsCombiner extends LuceneTestCase {

  public void testCustomCombinerSurvivesRewrite() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        List<Document> block = new ArrayList<>();
        Document child = new Document();
        child.add(new StringField("type", "child", Field.Store.NO));
        block.add(child);
        Document parent = new Document();
        parent.add(new StringField("type", "parent", Field.Store.NO));
        block.add(parent);
        w.addDocuments(block);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        BitSetProducer parents =
            new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
        // parent scores 2, child scores 3
        Query parentQuery =
            new BoostQuery(new ConstantScoreQuery(new TermQuery(new Term("type", "parent"))), 2f);
        Query childQuery =
            new BoostQuery(new ConstantScoreQuery(new TermQuery(new Term("type", "child"))), 3f);
        BinaryOperator<Float> product = (p, c) -> p * c;

        // no rewrite needed: the combiner is used, score = 2 * 3
        Query q1 = new ParentsChildrenBlockJoinQuery(parents, parentQuery, childQuery, 1, product);
        TopDocs td1 = searcher.search(q1, 10);
        assertEquals(1, td1.totalHits.value());
        assertEquals(6f, td1.scoreDocs[0].score, 0f);

        // same parent query wrapped in a one-clause BooleanQuery, which rewrites to the clause
        Query wrapped = new BooleanQuery.Builder().add(parentQuery, Occur.MUST).build();
        Query q2 = new ParentsChildrenBlockJoinQuery(parents, wrapped, childQuery, 1, product);
        TopDocs td2 = searcher.search(q2, 10);
        assertEquals(1, td2.totalHits.value());
        assertEquals("custom scoreCombiner must survive rewrite()", 6f, td2.scoreDocs[0].score, 0f);
      }
    }
  }
}
