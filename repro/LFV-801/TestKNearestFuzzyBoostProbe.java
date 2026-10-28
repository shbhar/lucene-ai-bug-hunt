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
package org.apache.lucene.classification;

import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.classification.utils.NearestFuzzyQuery;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Characterization probe (always passes): prints, for the two indexes of the brief's test, what
 * KNearestFuzzyClassifier and the sibling KNearestNeighborClassifier return for several
 * textFieldNames configurations, and what NearestFuzzyQuery rewrites to. Run it on the stock jars
 * and with a fix shadowed; compare the "[probe]" lines.
 */
public class TestKNearestFuzzyBoostProbe extends LuceneTestCase {

  private static final String[][] ONE_FIELD = {
    {"fruit", "apple banana cherry"},
    {"fruit", "apple pear"},
    {"vehicle", "car truck bus"},
    {"vehicle", "bus train"}
  };

  private static final String[][] TWO_FIELDS = {
    {"fruit", "apple banana", "sweet juice"},
    {"fruit", "apple cherry", "sweet tree"},
    {"fruit", "banana cherry", "juice garden"},
    {"vehicle", "car truck", "wheel engine"},
    {"vehicle", "truck bus", "engine wheel road"},
    {"vehicle", "bus car", "road wheel"}
  };

  public void testPrint() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    try (Directory d1 = newDirectory();
        Directory d2 = newDirectory();
        IndexReader one = index(d1, analyzer, ONE_FIELD, "text");
        IndexReader two = index(d2, analyzer, TWO_FIELDS, "title", "body")) {
      for (String f : new String[] {"text", "text^2"}) {
        System.out.println(
            "[probe] one-field index: MultiTerms.getTerms(reader, \""
                + f
                + "\") = "
                + (MultiTerms.getTerms(one, f) == null ? "null" : "terms"));
      }
      for (String f : new String[] {"text", "text^2"}) {
        NearestFuzzyQuery q = new NearestFuzzyQuery(analyzer);
        q.addTerms("apple banana", f);
        System.out.println(
            "[probe] one-field index: NearestFuzzyQuery(\"apple banana\", \""
                + f
                + "\").rewrite = ["
                + q.rewrite(new IndexSearcher(one))
                + "]");
      }
      for (String input : new String[] {"apple banana", "bus train"}) {
        for (String[] fields : new String[][] {{"text"}, {"text^2"}}) {
          fuzzy("one-field", one, analyzer, input, fields);
        }
        for (String[] fields : new String[][] {{"text"}, {"text^2"}, {"nosuchfield"}}) {
          knn("one-field", one, analyzer, input, fields);
        }
      }
      String input = "apple wheel";
      for (String[] fields :
          new String[][] {
            {"title", "body"}, {"title^10", "body"}, {"title^1", "body"}, {"body"}, {"title"}
          }) {
        fuzzy("two-field", two, analyzer, input, fields);
      }
      for (String[] fields : new String[][] {{"title", "body"}, {"title^10", "body"}, {"body"}}) {
        knn("two-field", two, analyzer, input, fields);
      }
    }
    analyzer.close();
  }

  private static IndexReader index(
      Directory dir, Analyzer analyzer, String[][] docs, String... fields) throws Exception {
    // plain IndexWriterConfig: one segment, default codec and merge policy
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      for (String[] d : docs) {
        Document doc = new Document();
        doc.add(new StringField("cls", d[0], Field.Store.YES));
        for (int i = 0; i < fields.length; i++) {
          doc.add(new TextField(fields[i], d[i + 1], Field.Store.NO));
        }
        w.addDocument(doc);
      }
    }
    return DirectoryReader.open(dir);
  }

  private static void fuzzy(
      String idx, IndexReader r, Analyzer a, String input, String... fields) throws Exception {
    print(idx, "fuzzy", input, fields, new KNearestFuzzyClassifier(r, null, a, null, 3, "cls", fields));
  }

  private static void knn(String idx, IndexReader r, Analyzer a, String input, String... fields)
      throws Exception {
    print(
        idx,
        "knn",
        input,
        fields,
        new KNearestNeighborClassifier(r, null, a, null, 3, 1, 1, "cls", fields));
  }

  private static void print(
      String idx, String kind, String input, String[] fields, Classifier<BytesRef> c)
      throws Exception {
    ClassificationResult<BytesRef> r = c.assignClass(input);
    List<ClassificationResult<BytesRef>> all = c.getClasses(input);
    StringBuilder sb = new StringBuilder();
    for (ClassificationResult<BytesRef> cr : all) {
      if (sb.length() > 0) sb.append(", ");
      sb.append(cr.assignedClass().utf8ToString()).append('=').append(cr.score());
    }
    System.out.println(
        "[probe] "
            + idx
            + " "
            + kind
            + " input=\""
            + input
            + "\" fields="
            + String.join(",", fields)
            + " -> assignClass="
            + (r == null ? "null" : r.assignedClass().utf8ToString())
            + " getClasses=["
            + sb
            + "]");
  }
}
