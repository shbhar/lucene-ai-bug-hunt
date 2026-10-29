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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMultiCollectorManagerSingleNested extends LuceneTestCase {

  public void testSingleNestedManager() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(new Document());
      w.addDocument(new Document());
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        MultiCollectorManager inner =
            new MultiCollectorManager(
                new TopScoreDocCollectorManager(1, Integer.MAX_VALUE),
                new TopScoreDocCollectorManager(2, Integer.MAX_VALUE));
        Object[] results =
            (Object[])
                new IndexSearcher(reader)
                    .search(new MatchAllDocsQuery(), new MultiCollectorManager(inner))[0];
        // expected: the inner top-2 manager returns 2 hits, as when inner is searched directly
        assertEquals(2, ((TopDocs) results[1]).scoreDocs.length);
      }
    }
  }
}
