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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortedNumericSortField;
import org.apache.lucene.search.SortedSetSortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Index sorting: a document whose sort field is indexed without doc values.
 *
 * <p>Scenario: one committed document, one uncommitted legal document that does not have the sort
 * field at all (a missing value), then a document that has the sort field indexed only as a
 * keyword, point or stored field (no doc values). Then commit.
 *
 * <p>Expected (CHANGES LUCENE-8441: "IndexWriter now checks doc value type for index sort fields
 * and fails the document if they are not compatible"): either addDocument rejects that document
 * and the writer stays usable, or the document is accepted and treated as having a missing value.
 * In neither case should commit fail and close the writer.
 *
 * <p>Uses only public API.
 */
public class TestSortFieldWithoutDocValues extends LuceneTestCase {

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    return d;
  }

  /** Runs the scenario; fails if commit throws or the writer closes. */
  private void check(Sort indexSort, Field sortFieldWithoutDocValues) throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
      iwc.setIndexSort(indexSort);
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      IndexWriter w = new IndexWriter(dir, iwc);

      w.addDocument(doc("committed"));
      w.commit();

      // legal: the sort field is simply missing in this document
      w.addDocument(doc("pending"));

      Document bad = doc("bad");
      bad.add(sortFieldWithoutDocValues);
      boolean rejected = false;
      String rejectMessage = null;
      try {
        w.addDocument(bad);
      } catch (IllegalArgumentException e) {
        rejected = true;
        rejectMessage = e.getMessage();
      }

      Throwable commitFailure = null;
      try {
        w.commit();
      } catch (Throwable t) {
        commitFailure = t;
      }
      boolean open = w.isOpen();
      Throwable tragic = w.getTragicException();
      int committedDocs;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        committedDocs = r.numDocs();
      }
      if (open) {
        w.close();
      }

      String state =
          "sort="
              + indexSort
              + " badField="
              + sortFieldWithoutDocValues.getClass().getSimpleName()
              + " addDocument(bad) "
              + (rejected ? "rejected: " + rejectMessage : "accepted")
              + "; commit "
              + (commitFailure == null ? "succeeded" : "threw " + commitFailure)
              + "; writer open="
              + open
              + " tragic="
              + (tragic != null)
              + " committed docs="
              + committedDocs;
      if (VERBOSE || commitFailure != null) {
        System.out.println("V1-STATE " + state);
      }
      assertNull("commit must not fail: " + state, commitFailure);
      assertTrue("writer must stay open: " + state, open);
      assertEquals(
          "the pending legal document must be committed: " + state,
          rejected ? 2 : 3,
          committedDocs);
    }
  }

  public void testStringSortWithStringField() throws Exception {
    check(new Sort(new SortField("sort", SortField.Type.STRING)), new StringField("sort", "x", Field.Store.NO));
  }

  public void testLongSortWithLongPoint() throws Exception {
    check(new Sort(new SortField("sort", SortField.Type.LONG)), new LongPoint("sort", 5L));
  }

  public void testIntSortWithIntPoint() throws Exception {
    check(new Sort(new SortField("sort", SortField.Type.INT)), new IntPoint("sort", 5));
  }

  public void testFloatSortWithFloatPoint() throws Exception {
    check(new Sort(new SortField("sort", SortField.Type.FLOAT)), new FloatPoint("sort", 5f));
  }

  public void testDoubleSortWithDoublePoint() throws Exception {
    check(new Sort(new SortField("sort", SortField.Type.DOUBLE)), new DoublePoint("sort", 5d));
  }

  public void testSortedNumericSortWithStoredField() throws Exception {
    check(
        new Sort(new SortedNumericSortField("sort", SortField.Type.LONG)),
        new StoredField("sort", 5L));
  }

  public void testSortedSetSortWithStringField() throws Exception {
    check(new Sort(new SortedSetSortField("sort", false)), new StringField("sort", "x", Field.Store.NO));
  }

  /**
   * Control 1 (expected to pass): the same scenario with the correct doc values type commits all
   * three documents.
   */
  public void testControlCorrectDocValues() throws Exception {
    check(new Sort(new SortField("sort", SortField.Type.STRING)), new SortedDocValuesField("sort", new BytesRef("x")));
  }

  /**
   * Control 2 (expected to pass): a wrong doc values type is rejected by addDocument (LUCENE-8441)
   * and the writer stays usable.
   */
  public void testControlWrongDocValuesTypeIsRejectedEarly() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
      iwc.setIndexSort(new Sort(new SortField("sort", SortField.Type.STRING)));
      IndexWriter w = new IndexWriter(dir, iwc);
      w.addDocument(doc("pending"));
      Document bad = doc("bad");
      bad.add(new NumericDocValuesField("sort", 5L));
      expectThrows(IllegalArgumentException.class, () -> w.addDocument(bad));
      w.commit();
      assertTrue(w.isOpen());
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.numDocs());
      }
      w.close();
    }
  }
}
