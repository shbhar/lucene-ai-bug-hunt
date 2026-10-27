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

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Rescoring the (empty) hits of a search on an index without segments must return no hits, as
 * QueryRescorer does. DoubleValuesSourceRescorer (and so LateInteractionRescorer) calls
 * leaves.getFirst() before looking at the hits and throws NoSuchElementException.
 */
public class TestDoubleValuesSourceRescorerEmptyIndex extends LuceneTestCase {

  /** Runs a first-pass search on a valid index that has no segments, then rescores its hits. */
  private static void rescoreOnIndexWithoutSegments(Rescorer rescorer) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.commit(); // a valid, empty index
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(0, reader.leaves().size());
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs firstPass = searcher.search(new MatchAllDocsQuery(), 10);
        assertEquals(0, firstPass.scoreDocs.length);
        assertEquals(0, rescorer.rescore(searcher, firstPass, 10).scoreDocs.length);
      }
    }
  }

  /** Control: QueryRescorer returns no hits for the same input. */
  public void testQueryRescorer() throws Exception {
    rescoreOnIndexWithoutSegments(
        new QueryRescorer(new MatchAllDocsQuery()) {
          @Override
          protected float combine(
              float firstPassScore, boolean secondPassMatches, float secondPassScore) {
            return firstPassScore + secondPassScore;
          }
        });
  }

  public void testDoubleValuesSourceRescorer() throws Exception {
    rescoreOnIndexWithoutSegments(
        new DoubleValuesSourceRescorer(DoubleValuesSource.constant(1)) {
          @Override
          protected float combine(float firstPassScore, boolean valuePresent, double sourceValue) {
            return valuePresent ? (float) sourceValue : firstPassScore;
          }
        });
  }

  public void testLateInteractionRescorer() throws Exception {
    rescoreOnIndexWithoutSegments(
        LateInteractionRescorer.create("multivector", new float[][] {{1f, 0f}}));
  }
}
