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

import java.io.IOException;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.classification.ClassificationResult;
import org.apache.lucene.classification.Classifier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Number probe (always passes): prints getPrecision(), getRecall(), getF1Measure() and getAccuracy()
 * for a few small (correct class, assigned class) datasets, to compare stock and patched builds.
 */
public class TestConfusionMatrixMetricsProbe extends LuceneTestCase {

  private static final class EchoClassifier implements Classifier<BytesRef> {
    @Override
    public ClassificationResult<BytesRef> assignClass(String text) {
      return new ClassificationResult<>(new BytesRef(text), 1d);
    }

    @Override
    public List<ClassificationResult<BytesRef>> getClasses(String text) {
      return null;
    }

    @Override
    public List<ClassificationResult<BytesRef>> getClasses(String text, int max) {
      return null;
    }
  }

  private static void probe(String name, String... correctThenAssigned) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        for (int i = 0; i < correctThenAssigned.length; i += 2) {
          Document doc = new Document();
          doc.add(new StringField("category", correctThenAssigned[i], Field.Store.YES));
          doc.add(new TextField("text", correctThenAssigned[i + 1], Field.Store.YES));
          w.addDocument(doc);
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        ConfusionMatrixGenerator.ConfusionMatrix cm =
            ConfusionMatrixGenerator.getConfusionMatrix(
                reader, new EchoClassifier(), "category", "text", -1);
        System.out.println(
            "PROBE "
                + name
                + " matrix="
                + cm.getLinearizedMatrix()
                + " precision="
                + cm.getPrecision()
                + " recall="
                + cm.getRecall()
                + " f1="
                + cm.getF1Measure()
                + " accuracy="
                + cm.getAccuracy());
      }
    }
  }

  public void testPrintMetrics() throws Exception {
    probe("sports-politics", "sports", "sports", "sports", "politics", "sports", "politics",
        "sports", "politics");
    probe("abc", "a", "a", "a", "c", "b", "b", "b", "c");
    probe("control-a-b", "a", "a", "b", "a");
    probe("control-balanced", "a", "a", "a", "a", "a", "b", "b", "b", "b", "b", "b", "a");
  }
}
