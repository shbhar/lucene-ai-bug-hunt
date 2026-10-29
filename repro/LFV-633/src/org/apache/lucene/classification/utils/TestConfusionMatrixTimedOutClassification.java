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
package org.apache.lucene.classification.utils;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.apache.lucene.classification.ClassificationResult;
import org.apache.lucene.classification.Classifier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

public class TestConfusionMatrixTimedOutClassification extends LuceneTestCase {

  public void testStuckClassificationDoesNotTimeOutNextDocument() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    Classifier<BytesRef> classifier =
        new Classifier<>() {
          @Override
          public ClassificationResult<BytesRef> assignClass(String text) {
            if (text.equals("stuck")) {
              try {
                release.await(); // never finishes within the generator's 5 s limit
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
            return new ClassificationResult<>(new BytesRef(text), 1.0);
          }

          @Override
          public List<ClassificationResult<BytesRef>> getClasses(String text) {
            throw new UnsupportedOperationException();
          }

          @Override
          public List<ClassificationResult<BytesRef>> getClasses(String text, int max) {
            throw new UnsupportedOperationException();
          }
        };
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String text : new String[] {"stuck", "b"}) { // doc 0 hangs, doc 1 is instant
          Document doc = new Document();
          doc.add(new StringField("class", "b", Field.Store.YES));
          doc.add(new StringField("text", text, Field.Store.YES));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        ConfusionMatrixGenerator.ConfusionMatrix matrix =
            ConfusionMatrixGenerator.getConfusionMatrix(reader, classifier, "class", "text", -1);
        // expected: only doc 0 times out, so 1 doc is evaluated; actual: doc 1 times out too (0)
        assertEquals("evaluated documents", 1, matrix.getNumberOfEvaluatedDocs());
      } finally {
        release.countDown();
      }
    }
  }
}
