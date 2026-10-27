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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.classification.utils.NearestFuzzyQuery;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SoftDeletesRetentionMergePolicy;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * NearestFuzzyQuery.rewrite boosts every fuzzy variant by edit-score^2 * idf(df, numDocs) with
 * idf = log((numDocs + 1) / (df + 1)) + 1 (NearestFuzzyQuery.java:155,187,198-200). numDocs = reader.numDocs()
 * counts live documents only, while df = reader.docFreq(term) also counts deleted documents that are not merged
 * away yet. When df + 1 > e * (numDocs + 1) the idf is negative, new BoostQuery(...) throws
 * IllegalArgumentException ("boost must be a positive float"), and KNearestFuzzyClassifier fails for that input.
 * Here 6 documents contain "apple" and 5 of them are deleted but not merged away (NoMergePolicy), or soft-deleted
 * and retained (SoftDeletesRetentionMergePolicy), so df = 6 and numDocs = 1: idf = log(2 / 7) + 1 = -0.2528. With
 * the default TieredMergePolicy (deletesPctAllowed 20%) such a deletion ratio is normally reclaimed.
 */
public class TestNearestFuzzyQueryDeletedDocs extends LuceneTestCase {

  private void check(IndexWriterConfig iwc, boolean nrt, boolean softDeletes) throws Exception {
    Analyzer analyzer = iwc.getAnalyzer();
    try (Directory dir = newDirectory()) {
      DirectoryReader nrtReader = null;
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 6; i++) {
          Document d = new Document();
          d.add(new StringField("id", "d" + i, Field.Store.YES));
          d.add(new TextField("text", "apple pie", Field.Store.YES));
          d.add(new StringField("cls", "fruit", Field.Store.YES));
          w.addDocument(d);
        }
        w.commit();
        // documents 1..5 are deleted (e.g. retracted training examples); their terms stay until a merge
        for (int i = 1; i < 6; i++) {
          if (softDeletes) {
            w.updateDocValues(new Term("id", "d" + i), new NumericDocValuesField("soft_delete", 1));
          } else {
            w.deleteDocuments(new Term("id", "d" + i));
          }
        }
        if (nrt) {
          nrtReader = DirectoryReader.open(w); // near-real-time reader (applies soft deletes)
        }
      }
      try (DirectoryReader reader = nrt ? nrtReader : DirectoryReader.open(dir)) {
        assertEquals(1, reader.numDocs());
        assumeTrue("deletes were merged away", reader.docFreq(new Term("text", "apple")) == 6);

        NearestFuzzyQuery q = new NearestFuzzyQuery(analyzer);
        q.addTerms("apple", "text");
        TopDocs hits = new IndexSearcher(reader).search(q, 10); // throws IllegalArgumentException on main
        assertEquals(1, hits.totalHits.value());

        ClassificationResult<BytesRef> r =
            new KNearestFuzzyClassifier(reader, null, analyzer, null, 3, "cls", "text").assignClass("apple");
        assertEquals("fruit", r.assignedClass().utf8ToString());
      }
    }
  }

  private static Analyzer analyzer() {
    return new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
  }

  /** deletes not merged away yet (NoMergePolicy makes that state deterministic) */
  public void testDeletedDocumentsNotMergedAway() throws Exception {
    check(new IndexWriterConfig(analyzer()).setMergePolicy(NoMergePolicy.INSTANCE), false, false);
  }

  /** soft deletes kept by SoftDeletesRetentionMergePolicy (the documented way to retain deleted history) */
  public void testRetainedSoftDeletes() throws Exception {
    IndexWriterConfig iwc =
        new IndexWriterConfig(analyzer())
            .setSoftDeletesField("soft_delete")
            .setMergePolicy(
                new SoftDeletesRetentionMergePolicy(
                    "soft_delete", MatchAllDocsQuery::new, new TieredMergePolicy()));
    check(iwc, true, true);
  }
}
