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
package org.apache.lucene.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * LogOddsFusionWeight#scorerSupplier returns the only non-null clause scorer unchanged when just
 * one clause has postings in a segment, so documents in that segment get the raw clause score
 * instead of the fused score. Two documents with identical content then score differently
 * depending on which segment they are in, and explain() disagrees with the score.
 */
public class TestLogOddsFusionSegmentShortcut extends LuceneTestCase {

  private static Document doc(String id, String body) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    d.add(new TextField("body", body, Field.Store.NO));
    return d;
  }

  public void testScoreDoesNotDependOnSegment() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w =
        new IndexWriter(
            dir,
            new IndexWriterConfig(new MockAnalyzer(random()))
                .setMergePolicy(NoMergePolicy.INSTANCE));
    // segment 1: contains both terms
    w.addDocument(doc("x", "apple pie"));
    w.addDocument(doc("other", "banana split"));
    w.commit();
    // segment 2: contains only "apple"
    w.addDocument(doc("y", "apple pie"));
    w.commit();
    DirectoryReader reader = DirectoryReader.open(w);
    w.close();
    assertEquals(2, reader.leaves().size());
    IndexSearcher searcher = new IndexSearcher(reader);

    Query query =
        new LogOddsFusionQuery(
            List.of(
                new BayesianScoreQuery(new TermQuery(new Term("body", "apple")), 1f, 0f),
                new BayesianScoreQuery(new TermQuery(new Term("body", "banana")), 1f, 0f)),
            0.5f);
    Map<String, Float> scores = new HashMap<>();
    Map<String, Float> explained = new HashMap<>();
    for (ScoreDoc sd : searcher.search(query, 10).scoreDocs) {
      String id = searcher.storedFields().document(sd.doc).get("id");
      scores.put(id, sd.score);
      explained.put(id, searcher.explain(query, sd.doc).getValue().floatValue());
    }
    reader.close();
    dir.close();

    assertEquals(
        "docs x and y have identical content; scores=" + scores + " explain=" + explained,
        scores.get("x"),
        scores.get("y"));
    assertEquals("explain(y) must equal score(y)", explained.get("y"), scores.get("y"));
  }
}
