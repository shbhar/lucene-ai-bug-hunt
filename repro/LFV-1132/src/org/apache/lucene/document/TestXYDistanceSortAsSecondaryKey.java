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
package org.apache.lucene.document;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestXYDistanceSortAsSecondaryKey extends LuceneTestCase {

  public void testMissingValueBottomDoesNotDropCloserTiedHit() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new SortedDocValuesField("cat", new BytesRef("b")));
      doc.add(new XYDocValuesField("loc", 1f, 0f));
      w.addDocument(doc);
      doc = new Document();
      doc.add(new SortedDocValuesField("cat", new BytesRef("a")));
      w.addDocument(doc);
      doc = new Document();
      doc.add(new SortedDocValuesField("cat", new BytesRef("a")));
      doc.add(new XYDocValuesField("loc", 3f, 0f));
      w.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        Sort sort =
            new Sort(
                new SortField("cat", SortField.Type.STRING),
                XYDocValuesField.newDistanceSort("loc", 0f, 0f));
        TopDocs top = new IndexSearcher(reader).search(new MatchAllDocsQuery(), 1, sort);
        // expected: doc 2 (cat a, distance 3) beats doc 1 (cat a, no location); actual: doc 1
        assertEquals(2, top.scoreDocs[0].doc);
      }
    }
  }
}
