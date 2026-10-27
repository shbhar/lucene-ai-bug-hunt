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

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.apache.lucene.classification.ClassificationResult;
import org.apache.lucene.classification.Classifier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * ConfusionMatrixGenerator gives each classification 5 seconds ("fail if classification takes
 * more than 5s") on a private single-thread executor, but does not cancel a classification that
 * timed out. The stuck task keeps the only worker thread, so every later document waits behind it
 * and is counted as timed out too, although its own classification is instant.
 */
// the stuck classification is released at the end of the test; keep the framework's thread-leak
// check from racing with it
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class TestConfusionMatrixTimedOutClassification extends LuceneTestCase {

  public void testOneStuckClassificationDoesNotTimeOutTheOthers() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    Classifier<BytesRef> classifier =
        new Classifier<>() {
          @Override
          public ClassificationResult<BytesRef> assignClass(String text) {
            if (text.equals("stuck")) {
              try {
                release.await(); // a classification that never finishes in time
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
            return new ClassificationResult<>(new BytesRef(text.equals("stuck") ? "a" : text), 1.0);
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
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        // doc 0 is the stuck one; docs 1 and 2 classify instantly and correctly
        String[][] docs = {{"a", "stuck"}, {"b", "b"}, {"c", "c"}};
        for (String[] d : docs) {
          Document doc = new Document();
          doc.add(new StringField("class", d[0], Field.Store.YES));
          doc.add(new TextField("text", d[1], Field.Store.YES));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        ConfusionMatrixGenerator.ConfusionMatrix matrix =
            ConfusionMatrixGenerator.getConfusionMatrix(reader, classifier, "class", "text", -1);
        assertEquals(
            "documents whose own classification finished at once: "
                + matrix.getLinearizedMatrix(),
            2,
            matrix.getNumberOfEvaluatedDocs());
      } finally {
        release.countDown();
      }
    }
  }
}
