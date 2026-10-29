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

import java.util.List;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSortRescorerMaxTopN extends LuceneTestCase {

  public void testTopNIntegerMaxValue() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 2; i++) {
          w.addDocument(List.of(new NumericDocValuesField("price", i)));
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs firstPass = searcher.search(new MatchAllDocsQuery(), 10);
        Rescorer rescorer = new SortRescorer(new Sort(new SortField("price", SortField.Type.LONG)));
        try {
          TopDocs rescored = rescorer.rescore(searcher, firstPass, Integer.MAX_VALUE);
          assertEquals(2, rescored.scoreDocs.length);
        } catch (OutOfMemoryError | IllegalArgumentException e) {
          // Expected: the 2 hits come back re-sorted (as with topN = 2); actual: rescore throws.
          throw new AssertionError("expected 2 re-sorted hits, but rescore threw " + e, e);
        }
      }
    }
  }
}
