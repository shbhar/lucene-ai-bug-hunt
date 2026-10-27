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
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DoubleValuesSourceRescorer accepts any DoubleValuesSource, but always calls getValues(ctx, null).
 * DoubleValuesSource#getValues allows a null scores argument only when needsScores() is false, so a
 * source that needs scores (here DoubleValuesSource.SCORES) fails inside rescore() with an
 * AssertionError (-ea) or a NullPointerException (-da). explain() for the same source returns a
 * normal explanation.
 */
public class TestDoubleValuesSourceRescorerScoreSource extends LuceneTestCase {

  public void testSourceThatNeedsScores() throws Exception {
    rescoreWith(DoubleValuesSource.SCORES);
  }

  /** Control: the same rescore with a source that does not need scores passes. */
  public void testControlConstantSource() throws Exception {
    rescoreWith(DoubleValuesSource.constant(1));
  }

  private static void rescoreWith(DoubleValuesSource source) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 3; i++) {
          Document doc = new Document();
          doc.add(new TextField("body", i == 0 ? "a a" : "a", Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query query = new TermQuery(new Term("body", "a"));
        TopDocs firstPass = searcher.search(query, 3);
        assertEquals(3, firstPass.scoreDocs.length);

        Rescorer rescorer =
            new DoubleValuesSourceRescorer(source) {
              @Override
              protected float combine(float firstPassScore, boolean valuePresent, double value) {
                return valuePresent ? (float) value : firstPassScore;
              }
            };

        // explain() handles this source
        int doc = firstPass.scoreDocs[0].doc;
        Explanation explanation =
            rescorer.explain(searcher, searcher.explain(query, doc), doc);
        assertTrue(explanation.isMatch());

        // rescore() must not fail on the same source
        TopDocs rescored = rescorer.rescore(searcher, firstPass, 3);
        assertEquals(3, rescored.scoreDocs.length);
      }
    }
  }
}
