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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * RescoreTopNQuery over a non-kNN inner query, on a reader whose segment still holds deleted
 * documents. Deleted documents must not take any of the n slots, and IndexSearcher#count must count
 * live documents only ("Counts the number of live documents that match", Weight#count).
 */
public class TestRescoreTopNQueryDeletedDocs extends LuceneTestCase {

  private static final Query INNER = new TermQuery(new Term("f", "a"));

  /**
   * One segment. Docs 0..numMatches-1 match f:a and have v = doc id, so the highest doc id has the
   * best rescore value. One more doc does not match f:a, so the segment is never fully deleted.
   * NoMergePolicy only keeps the deletions in place deterministically.
   */
  private static DirectoryReader openReader(Directory dir, int numMatches, int... deletedIds)
      throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i <= numMatches; i++) {
        Document doc = new Document();
        doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        doc.add(new StringField("f", i < numMatches ? "a" : "b", Field.Store.NO));
        doc.add(new NumericDocValuesField("v", i));
        w.addDocument(doc);
      }
      w.commit();
      for (int id : deletedIds) {
        w.deleteDocuments(new Term("id", Integer.toString(id)));
      }
      w.commit();
    }
    DirectoryReader reader = DirectoryReader.open(dir);
    assertEquals("one segment", 1, reader.leaves().size());
    assertEquals("deletions retained", deletedIds.length, reader.numDeletedDocs());
    return reader;
  }

  private static void assertTopN(IndexReader reader, Query q, int[] expectedDocs)
      throws IOException {
    IndexSearcher searcher = new IndexSearcher(reader);
    int[] hits = Arrays.stream(searcher.search(q, 10).scoreDocs).mapToInt(sd -> sd.doc).toArray();
    int count = searcher.count(q);
    String state = "hits=" + Arrays.toString(hits) + " count=" + count;
    assertArrayEquals("top n live docs; " + state, expectedDocs, hits);
    assertEquals("count of live matches; " + state, expectedDocs.length, count);
  }

  /** Control: no deletions. 4 matches, n=2: docs 3 and 2 have the best values. */
  public void testNoDeletions() throws IOException {
    try (Directory dir = newDirectory();
        DirectoryReader reader = openReader(dir, 4)) {
      Query q = new RescoreTopNQuery(INNER, DoubleValuesSource.fromLongField("v"), 2);
      assertTopN(reader, q, new int[] {3, 2});
    }
  }

  /** The deleted best doc 3 must not take a slot: expected [2, 1], count 2. */
  public void testDeletedBestDocDoesNotTakeASlot() throws IOException {
    try (Directory dir = newDirectory();
        DirectoryReader reader = openReader(dir, 4, 3)) {
      Query q = new RescoreTopNQuery(INNER, DoubleValuesSource.fromLongField("v"), 2);
      assertTopN(reader, q, new int[] {2, 1});
    }
  }

  /**
   * Same with a values source that needs scores (rescored in the other loop of rewrite). All scores
   * tie, so the ties go to the lowest live doc ids: expected [1, 2], count 2.
   */
  public void testDeletedDocDoesNotTakeASlotWhenRescoringByScore() throws IOException {
    try (Directory dir = newDirectory();
        DirectoryReader reader = openReader(dir, 4, 0)) {
      Query q = new RescoreTopNQuery(INNER, DoubleValuesSource.SCORES, 2);
      assertTopN(reader, q, new int[] {1, 2});
    }
  }

  /** n larger than the match set: the hits are right, and count must not include deleted doc 2. */
  public void testCountExcludesDeletedDocs() throws IOException {
    try (Directory dir = newDirectory();
        DirectoryReader reader = openReader(dir, 3, 2)) {
      Query q = new RescoreTopNQuery(INNER, DoubleValuesSource.fromLongField("v"), 10);
      assertTopN(reader, q, new int[] {1, 0});
    }
  }

  /** The only match is deleted: no hits, count 0, and no exception. */
  public void testOnlyMatchDeleted() throws IOException {
    try (Directory dir = newDirectory();
        DirectoryReader reader = openReader(dir, 1, 0)) {
      Query q = new RescoreTopNQuery(INNER, DoubleValuesSource.fromLongField("v"), 2);
      assertTopN(reader, q, new int[0]);
    }
  }
}
