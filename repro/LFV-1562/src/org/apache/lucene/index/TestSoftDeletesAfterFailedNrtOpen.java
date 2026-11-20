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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.FilterIndexInput;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestSoftDeletesAfterFailedNrtOpen extends LuceneTestCase {

  public void testDeleteByTermKeepsOtherDocument() throws Exception {
    AtomicBoolean failScan = new AtomicBoolean();
    Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexInput openInput(String name, IOContext context) throws IOException {
            return failing(in.openInput(name, context), failScan);
          }
        };
    Field softDelete = new NumericDocValuesField("soft", 1);
    try (IndexWriter w = new IndexWriter(dir, config())) {
      w.softUpdateDocument(new Term("id", "1"), doc("1"), softDelete);
      w.softUpdateDocument(new Term("id", "2"), doc("2"), softDelete);
      w.updateDocValues(new Term("id", "1"), softDelete);
    }
    try (IndexWriter w = new IndexWriter(dir, config())) {
      failScan.set(true);
      expectThrows(IOException.class, () -> DirectoryReader.open(w));
      failScan.set(false);
      assertNull(w.getTragicException());
      w.deleteDocuments(new Term("id", "1"));
      w.commit();
    }
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      // expected: document 2, which no call deleted, is live; actual: the commit has no documents
      assertEquals(1, reader.numDocs());
    }
    dir.close();
  }

  private static IndexWriterConfig config() {
    return new IndexWriterConfig()
        .setSoftDeletesField("soft")
        .setMergePolicy(NoMergePolicy.INSTANCE)
        .setCodec(TestUtil.getDefaultCodec());
  }

  private static List<Field> doc(String id) {
    return List.of(new StringField("id", id, Field.Store.NO));
  }

  private static IndexInput failing(IndexInput input, AtomicBoolean failScan) {
    return new FilterIndexInput(input.toString(), input) {
      @Override
      public byte readByte() throws IOException {
        if (failScan.get() && callStackContainsAnyOf("onNewReader")) {
          throw new IOException("injected read failure");
        }
        return in.readByte();
      }

      @Override
      public IndexInput slice(String description, long offset, long length) throws IOException {
        return failing(in.slice(description, offset, length), failScan);
      }

      @Override
      public IndexInput clone() {
        return failing(in.clone(), failScan);
      }
    };
  }
}
