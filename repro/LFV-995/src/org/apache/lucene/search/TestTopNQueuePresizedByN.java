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
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTopNQueuePresizedByN extends LuceneTestCase {

  public void testRescoreTopNQueryIntegerMaxValue() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader reader = oneDocIndex(dir)) {
      Query q =
          new RescoreTopNQuery(
              new MatchAllDocsQuery(), DoubleValuesSource.constant(1), Integer.MAX_VALUE);
      int count;
      try {
        count = new IndexSearcher(reader).count(q);
      } catch (IllegalArgumentException e) {
        throw new AssertionError("expected 1 hit for n = Integer.MAX_VALUE on 1 doc, got " + e, e);
      }
      assertEquals(1, count);
    }
  }

  public void testLatLonPointNearestIntegerMaxValue() throws IOException {
    try (Directory dir = new ByteBuffersDirectory();
        DirectoryReader reader = oneDocIndex(dir)) {
      TopFieldDocs hits;
      try {
        hits = LatLonPoint.nearest(new IndexSearcher(reader), "loc", 0, 0, Integer.MAX_VALUE);
      } catch (IllegalArgumentException e) {
        throw new AssertionError("expected 1 hit for n = Integer.MAX_VALUE on 1 doc, got " + e, e);
      }
      assertEquals(1, hits.scoreDocs.length);
    }
  }

  private static DirectoryReader oneDocIndex(Directory dir) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new LatLonPoint("loc", 0, 0));
      w.addDocument(doc);
    }
    return DirectoryReader.open(dir);
  }
}
