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
package org.apache.lucene.index;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.ExitableDirectoryReader.ExitingReaderException;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestExitableReaderExactKnnSearch extends LuceneTestCase {

  public void testExactKnnSearchChecksTheReadersTimeout() throws IOException {
    int numDocs = 2000;
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < numDocs; i++) {
          Document doc = new Document();
          doc.add(new KnnFloatVectorField("vector", new float[] {i}));
          doc.add(new StringField("color", "red", Field.Store.NO));
          writer.addDocument(doc);
        }
      }
      AtomicBoolean timeIsUp = new AtomicBoolean();
      try (DirectoryReader reader =
          ExitableDirectoryReader.wrap(DirectoryReader.open(dir), timeIsUp::get)) {
        Query query =
            new KnnFloatVectorQuery(
                "vector", new float[] {0}, numDocs, new TermQuery(new Term("color", "red"))) {
              @Override
              protected TopDocs exactSearch(
                  LeafReaderContext context,
                  DocIdSetIterator acceptIterator,
                  QueryTimeout queryTimeout)
                  throws IOException {
                timeIsUp.set(true);
                return super.exactSearch(context, acceptIterator, queryTimeout);
              }
            };
        // expected: the exact scan stops with ExitingReaderException; actual: the search returns
        expectThrows(
            ExitingReaderException.class, () -> new IndexSearcher(reader).search(query, numDocs));
      }
    }
  }
}
