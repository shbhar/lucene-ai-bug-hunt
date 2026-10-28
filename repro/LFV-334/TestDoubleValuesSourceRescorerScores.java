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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DoubleValuesSourceRescorer accepts any DoubleValuesSource, but rescore() calls
 * source.getValues(ctx, null) for every hit. DoubleValuesSource#getValues allows a null scores
 * argument only when needsScores() is false, and DoubleValuesSource.SCORES says callers must not
 * pass null, so rescoring with SCORES fails on the first hit. explain() for the same document
 * returns normally.
 */
public class TestDoubleValuesSourceRescorerScores extends LuceneTestCase {

  private static final Query QUERY = new TermQuery(new Term("body", "contents"));

  /** Uses the source's value as the new score, or -1 when the source has no value. */
  private static Rescorer valueRescorer(DoubleValuesSource source) {
    return new DoubleValuesSourceRescorer(source) {
      @Override
      protected float combine(float firstPassScore, boolean valuePresent, double sourceValue) {
        return valuePresent ? (float) sourceValue : -1f;
      }
    };
  }

  /** Four documents; body:contents matches docs 0, 1 and 2. */
  private static void index(Directory dir) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String body :
          new String[] {"contents contents", "contents", "contents of the book", "other"}) {
        Document doc = new Document();
        doc.add(new TextField("body", body, Field.Store.NO));
        w.addDocument(doc);
      }
    }
  }

  /** Rescoring with the hit's own score must give every hit its first-pass score back. */
  public void testRescoreWithScoresSource() throws IOException {
    try (Directory dir = newDirectory()) {
      index(dir);
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs firstPass = searcher.search(QUERY, 3);
        assertEquals(3, firstPass.scoreDocs.length);
        Rescorer rescorer = valueRescorer(DoubleValuesSource.SCORES);

        // rescore() updates the ScoreDoc objects in place, so keep the first-pass hits
        int[] docs = new int[3];
        float[] scores = new float[3];
        for (int i = 0; i < 3; i++) {
          docs[i] = firstPass.scoreDocs[i].doc;
          scores[i] = firstPass.scoreDocs[i].score;
        }

        // explain() returns normally for the same rescorer, source and document
        Explanation explanation =
            rescorer.explain(searcher, searcher.explain(QUERY, docs[0]), docs[0]);
        assertTrue(explanation.isMatch());

        // rescore() must not fail on the same source: every hit keeps its first-pass score
        TopDocs rescored = rescorer.rescore(searcher, firstPass, 3);
        for (int i = 0; i < 3; i++) {
          assertEquals(docs[i], rescored.scoreDocs[i].doc);
          assertEquals(scores[i], rescored.scoreDocs[i].score, 0f);
        }
        // and explain() describes the score that rescore() gives the document
        assertEquals(scores[0], explanation.getValue().floatValue(), 0f);
      }
    }
  }

  /** Control: the same rescore with a source that does not need scores works. */
  public void testControlConstantSource() throws IOException {
    try (Directory dir = newDirectory()) {
      index(dir);
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs firstPass = searcher.search(QUERY, 3);
        TopDocs rescored =
            valueRescorer(DoubleValuesSource.constant(1)).rescore(searcher, firstPass, 3);
        assertEquals(3, rescored.scoreDocs.length);
        for (ScoreDoc hit : rescored.scoreDocs) {
          assertEquals(1f, hit.score, 0f);
        }
      }
    }
  }
}
