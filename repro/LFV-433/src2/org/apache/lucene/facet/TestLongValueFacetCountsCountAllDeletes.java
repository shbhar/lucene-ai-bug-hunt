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
package org.apache.lucene.facet;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SoftDeletesDirectoryReaderWrapper;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The IndexReader constructors of LongValueFacetCounts are documented to produce "the same result
 * as computing facets on a MatchAllDocsQuery". Since LUCENE-10070 the field-based constructor skips
 * deleted documents (upstream TestLongValueFacetCounts#testCountAll), but the constructors that
 * take a LongValuesSource or a MultiLongValuesSource still count every document up to maxDoc,
 * including deleted ones.
 */
public class TestLongValueFacetCountsCountAllDeletes extends LuceneTestCase {

  private static final String EXPECTED = "dim=field path=[] value=9 childCount=2\n  0 (4)\n  1 (5)\n";

  public void testSingleValuedCountAllSkipsDeletedDocs() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 10; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", String.valueOf(i), Field.Store.NO));
          doc.add(new NumericDocValuesField("field", i % 2));
          w.addDocument(doc);
        }
        w.deleteDocuments(new Term("id", "0"));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(9, r.numDocs());

        // Reference 1: the field-based "count all" constructor (fixed by LUCENE-10070).
        assertEquals(
            EXPECTED, new LongValueFacetCounts("field", r).getAllChildrenSortByValue().toString());

        // Reference 2: the same facets computed on a MatchAllDocsQuery, as the javadoc promises.
        FacetsCollector fc =
            new IndexSearcher(r).search(new MatchAllDocsQuery(), new FacetsCollectorManager());
        assertEquals(
            EXPECTED,
            new LongValueFacetCounts("field", LongValuesSource.fromLongField("field"), fc)
                .getAllChildrenSortByValue()
                .toString());

        // The LongValuesSource "count all" constructor counts the deleted document 0:
        assertEquals(
            EXPECTED,
            new LongValueFacetCounts("field", LongValuesSource.fromLongField("field"), r)
                .getAllChildrenSortByValue()
                .toString());
      }
    }
  }

  public void testMultiValuedCountAllSkipsDeletedDocs() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 10; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", String.valueOf(i), Field.Store.NO));
          doc.add(new SortedNumericDocValuesField("field", i % 2));
          w.addDocument(doc);
        }
        w.deleteDocuments(new Term("id", "0"));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(
            EXPECTED, new LongValueFacetCounts("field", r).getAllChildrenSortByValue().toString());
        // The MultiLongValuesSource "count all" constructor counts the deleted document 0:
        assertEquals(
            EXPECTED,
            new LongValueFacetCounts("field", MultiLongValuesSource.fromLongField("field"), r)
                .getAllChildrenSortByValue()
                .toString());
      }
    }
  }

  /**
   * The same through a reader that hides documents with getLiveDocs(): soft-deleted documents
   * behind SoftDeletesDirectoryReaderWrapper are still counted, so a value that only hidden
   * documents carry shows up as a facet label.
   */
  public void testSoftDeletesWrapperHiddenDocsAreCounted() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setSoftDeletesField("soft_delete")
              .setMergePolicy(NoMergePolicy.INSTANCE); // keep the soft-deleted document around
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 3; i++) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("field", i == 0 ? 42 : 1));
          if (i == 0) {
            doc.add(new NumericDocValuesField("soft_delete", 1)); // hidden by the wrapper
          }
          w.addDocument(doc);
        }
      }
      try (DirectoryReader r =
          new SoftDeletesDirectoryReaderWrapper(DirectoryReader.open(dir), "soft_delete")) {
        assertEquals(2, r.numDocs());
        String expected = "dim=field path=[] value=2 childCount=1\n  1 (2)\n";
        assertEquals(
            expected, new LongValueFacetCounts("field", r).getAllChildrenSortByValue().toString());
        assertEquals(
            expected,
            new LongValueFacetCounts("field", LongValuesSource.fromLongField("field"), r)
                .getAllChildrenSortByValue()
                .toString());
      }
    }
  }
}
