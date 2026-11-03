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

import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNullIndexWriterEventListener extends LuceneTestCase {

  public void testCommitWithNullEventListener() throws Exception {
    IndexWriterConfig config;
    try {
      config = new IndexWriterConfig().setIndexWriterEventListener(null);
    } catch (IllegalArgumentException expected) {
      return; // the setter rejects null, like the other IndexWriterConfig setters
    }
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
        for (int i = 0; i < 12; i++) {
          w.addDocument(new Document());
          w.commit();
        }
      }
      try (IndexWriter w = new IndexWriter(dir, config)) {
        w.addDocument(new Document());
        // expected: commit() succeeds; actual: NullPointerException from
        // IndexWriter.prepareCommitInternal, because commit() runs a merge-on-commit merge
        w.commit();
      }
    }
  }
}
