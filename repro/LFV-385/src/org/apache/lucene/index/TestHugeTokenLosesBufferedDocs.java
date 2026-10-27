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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Shows the consequence: a document added (acknowledged) before the huge token is gone. */
public class TestHugeTokenLosesBufferedDocs extends LuceneTestCase {

  public void testAcknowledgedUncommittedDocumentIsLost() throws Exception {
    FieldType ft = new FieldType();
    ft.setIndexOptions(IndexOptions.DOCS);
    ft.setTokenized(false);
    ft.freeze();
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()));
      Document a = new Document();
      a.add(new StringField("id", "committed", Field.Store.YES));
      w.addDocument(a);
      w.commit();
      Document b = new Document();
      b.add(new StringField("id", "acknowledged-not-committed", Field.Store.YES));
      w.addDocument(b);

      Document huge = new Document();
      huge.add(new Field("body", "a".repeat(Integer.MAX_VALUE / 3 + 1), ft));
      Throwable t = expectThrows(Throwable.class, () -> w.addDocument(huge));
      System.out.println("addDocument threw: " + t + "; tragic=" + w.getTragicException() + "; isOpen=" + w.isOpen());
      // documented: the immense term only rejects this document; the writer and doc B survive
      try {
        w.commit();
      } catch (AlreadyClosedException e) {
        System.out.println("commit() after the rejected document: " + e);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        System.out.println("numDocs after reopen = " + r.numDocs());
        assertEquals("the document added before the huge token must still be committable", 2, r.numDocs());
      }
      w.close();
    }
  }
}
