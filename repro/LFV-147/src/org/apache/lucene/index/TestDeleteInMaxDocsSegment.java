/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * End to end, default IndexWriterConfig with the default codec (pinned, since LuceneTestCase
 * randomizes Codec.getDefault()): an index of IndexWriter.MAX_DOCS (2^31 - 129) documents in
 * one segment, then one deletion. Writing the segment's live docs (Lucene90LiveDocsFormat) walks
 * the bits in batches of 1024 with an int offset that wraps past 2^31 - 1024.
 *
 * <p>Runtime: about 2.1 billion addDocument calls (roughly 10 minutes on one core).
 */
public class TestDeleteInMaxDocsSegment extends LuceneTestCase {

  public void testDeleteOneDocumentAndCommit() throws Exception {
    final int maxDocs = IndexWriter.MAX_DOCS;
    try (Directory dir = FSDirectory.open(createTempDir())) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        Document empty = new Document();
        Document victim = new Document();
        victim.add(new StringField("id", "victim", Field.Store.NO));
        w.addDocument(victim); // doc 0
        for (int doc = 1; doc < maxDocs; doc++) {
          w.addDocument(empty);
        }
        w.commit();
        w.deleteDocuments(new org.apache.lucene.index.Term("id", "victim"));
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(maxDocs, r.maxDoc());
        assertEquals(maxDocs - 1, r.numDocs());
      }
    }
  }
}
