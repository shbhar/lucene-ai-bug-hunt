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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SortRescorer must return every kept hit with its own first-pass score ("set scores from the
 * original score docs"), also when topN is smaller than the number of first-pass hits, which is the
 * typical use (re-sort the best 1,000 hits, keep the top 100).
 */
public class TestSortRescorerTopNScores extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;
  private TopDocs firstPass;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      // doc 0: "a" once, popularity 1; doc 1: "a" three times, popularity 2
      Document d0 = new Document();
      d0.add(newTextField("body", "a b c d", Field.Store.NO));
      d0.add(new NumericDocValuesField("popularity", 1));
      w.addDocument(d0);
      Document d1 = new Document();
      d1.add(newTextField("body", "a a a", Field.Store.NO));
      d1.add(new NumericDocValuesField("popularity", 2));
      w.addDocument(d1);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
    firstPass = searcher.search(new TermQuery(new Term("body", "a")), 10);
    assertEquals(2, firstPass.scoreDocs.length);
    assertEquals(1, firstPass.scoreDocs[0].doc); // doc 1 scores higher
    assertTrue(firstPass.scoreDocs[0].score > firstPass.scoreDocs[1].score);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private float firstPassScore(int doc) {
    for (ScoreDoc sd : firstPass.scoreDocs) {
      if (sd.doc == doc) {
        return sd.score;
      }
    }
    throw new AssertionError("doc " + doc + " is not a first-pass hit");
  }

  private void assertOwnScores(TopDocs rescored) {
    for (ScoreDoc sd : rescored.scoreDocs) {
      assertEquals("first-pass score of doc " + sd.doc, firstPassScore(sd.doc), sd.score, 0f);
    }
  }

  /** Re-sort the 2 first-pass hits by popularity and keep the top 1. */
  public void testSortByFieldTopNSmallerThanHits() throws Exception {
    Rescorer rescorer =
        new SortRescorer(new Sort(new SortField("popularity", SortField.Type.LONG, true)));
    TopDocs rescored = rescorer.rescore(searcher, firstPass, 1);
    assertEquals(1, rescored.scoreDocs.length);
    assertEquals(1, rescored.scoreDocs[0].doc);
    assertOwnScores(rescored);
  }

  /** The same with a sort on the score itself: the FieldDoc's sort value and score must agree. */
  public void testSortByScoreTopNSmallerThanHits() throws Exception {
    Rescorer rescorer = new SortRescorer(new Sort(SortField.FIELD_SCORE));
    TopDocs rescored = rescorer.rescore(searcher, firstPass, 1);
    assertEquals(1, rescored.scoreDocs.length);
    assertEquals(1, rescored.scoreDocs[0].doc);
    assertEquals(
        "sort value of the returned FieldDoc",
        firstPassScore(1),
        (Float) ((FieldDoc) rescored.scoreDocs[0]).fields[0],
        0f);
    assertOwnScores(rescored);
  }

  /** Control: with topN equal to the number of hits every hit keeps its own score. */
  public void testTopNEqualsHits() throws Exception {
    Rescorer rescorer =
        new SortRescorer(new Sort(new SortField("popularity", SortField.Type.LONG, true)));
    TopDocs rescored = rescorer.rescore(searcher, firstPass, 2);
    assertEquals(2, rescored.scoreDocs.length);
    assertEquals(1, rescored.scoreDocs[0].doc);
    assertOwnScores(rescored);
  }
}
