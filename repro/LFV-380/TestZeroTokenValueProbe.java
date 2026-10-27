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
package org.apache.lucene.classification.document;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.classification.ClassificationResult;
import org.apache.lucene.classification.SimpleNaiveBayesClassifier;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/** Prints the numbers quoted in the brief (same index as the repro). Never fails. */
public class TestZeroTokenValueProbe extends LuceneTestCase {

  private static Document trainingDoc(String title, String body, String clazz) {
    Document d = new Document();
    d.add(new TextField("title", title, Field.Store.NO));
    d.add(new TextField("body", body, Field.Store.NO));
    d.add(new StringField("class", clazz, Field.Store.YES));
    return d;
  }

  private static Document input(String title, String... bodies) {
    Document d = new Document();
    if (title != null) {
      d.add(new TextField("title", title, Field.Store.NO));
    }
    for (String b : bodies) {
      d.add(new TextField("body", b, Field.Store.NO));
    }
    return d;
  }

  private static String fmt(List<ClassificationResult<BytesRef>> l) {
    StringBuilder sb = new StringBuilder("[");
    for (ClassificationResult<BytesRef> c : l) {
      sb.append(c.assignedClass().utf8ToString())
          .append('=')
          .append(String.format(Locale.ROOT, "%.4f", c.score()))
          .append(' ');
    }
    return sb.append(']').toString();
  }

  private static String fmt(ClassificationResult<BytesRef> c) {
    return c == null
        ? "null"
        : c.assignedClass().utf8ToString() + "/" + String.format(Locale.ROOT, "%.4f", c.score());
  }

  public void testPrintNumbers() throws Exception {
    Analyzer a = new EnglishAnalyzer();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
        w.addDocument(trainingDoc("apple", "sweet fruit", "fruit"));
        w.addDocument(trainingDoc("pear", "green fruit", "fruit"));
        w.addDocument(trainingDoc("car", "four wheels", "vehicle"));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        Map<String, Analyzer> f2a = Map.of("title", a, "body", a);
        Object[][] cases = {
          {"title 'apple', no body", input("apple")},
          {"title 'apple', body 'sweet'", input("apple", "sweet")},
          {"title 'apple', body ''", input("apple", "")},
          {"title 'apple', body 'To be or not to be'", input("apple", "To be or not to be")},
          {"title 'apple', body '!!!'", input("apple", "!!!")},
          {"title 'car', no body", input("car")},
          {"title 'car', body ''", input("car", "")},
          {"title 'apple', bodies 'sweet fruit' x5 + ''",
            input("apple", "sweet fruit", "sweet fruit", "sweet fruit", "sweet fruit", "sweet fruit", "")},
          {"no fields at all", input(null)},
          {"title '' only", input("")},
        };
        for (Object[] c : cases) {
          SimpleNaiveBayesDocumentClassifier snb =
              new SimpleNaiveBayesDocumentClassifier(r, null, "class", f2a, "title", "body");
          Document d = (Document) c[1];
          System.out.println(
              "PROBE "
                  + c[0]
                  + ": getClasses="
                  + fmt(snb.getClasses(d))
                  + " getClasses(doc,1)="
                  + fmt(snb.getClasses(d, 1))
                  + " assignClass="
                  + fmt(snb.assignClass(d)));
        }
        SimpleNaiveBayesClassifier text =
            new SimpleNaiveBayesClassifier(r, a, null, "class", "title", "body");
        System.out.println(
            "PROBE text classifier, input '': getClasses="
                + fmt(text.getClasses(""))
                + " assignClass="
                + fmt(text.assignClass("")));
        KNearestNeighborDocumentClassifier knn =
            new KNearestNeighborDocumentClassifier(
                r, null, null, 3, 1, 1, "class", f2a, "title", "body");
        System.out.println(
            "PROBE k-NN document classifier, title 'apple', body '': assignClass="
                + fmt(knn.assignClass(input("apple", ""))));
      }
    }
    a.close();
  }
}
