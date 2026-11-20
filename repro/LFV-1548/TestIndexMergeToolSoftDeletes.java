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
package org.apache.lucene.misc;

import java.nio.file.Path;
import java.util.List;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SoftDeletesDirectoryReaderWrapper;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIndexMergeToolSoftDeletes extends LuceneTestCase {

  // Expected: a merged index with 2 live docs; actual: main throws IllegalArgumentException.
  public void testMergeIndexesWithSoftDeletes() throws Exception {
    Path[] indexes = {createTempDir("index1"), createTempDir("index2")};
    for (Path index : indexes) {
      try (Directory dir = FSDirectory.open(index);
          IndexWriter w =
              new IndexWriter(dir, new IndexWriterConfig().setSoftDeletesField("soft"))) {
        w.addDocument(List.of(new StringField("id", "a", Field.Store.NO)));
        w.softUpdateDocument(
            new Term("id", "a"),
            List.of(new StringField("id", "a", Field.Store.NO)),
            new NumericDocValuesField("soft", 1));
      }
    }
    Path merged = createTempDir("merged");
    IndexMergeTool.main(
        new String[] {merged.toString(), indexes[0].toString(), indexes[1].toString()});
    try (Directory dir = FSDirectory.open(merged);
        DirectoryReader reader =
            new SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), "soft")) {
      assertEquals(2, reader.numDocs());
    }
  }
}
