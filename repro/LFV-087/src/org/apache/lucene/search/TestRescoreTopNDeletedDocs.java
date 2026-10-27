/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * RescoreTopNQuery rescoring a (non-kNN) query on a reader with deletions. Its rewrite iterates the
 * inner Scorer without checking live docs, so a deleted document can take one of the n slots: the
 * search then returns fewer than n live hits, and IndexSearcher#count still counts the deleted one.
 */
public class TestRescoreTopNDeletedDocs extends LuceneTestCase {

  private void check(boolean deleteBest) throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 4; i++) {
        Document d = new Document();
        d.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        d.add(new StringField("f", "a", Field.Store.NO));
        d.add(new NumericDocValuesField("v", i)); // rescore value: doc 3 is the best
        w.addDocument(d);
      }
      w.commit();
      if (deleteBest) {
        w.deleteDocuments(new Term("id", "3"));
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query q =
            new RescoreTopNQuery(
                new TermQuery(new Term("f", "a")), DoubleValuesSource.fromLongField("v"), 2);
        TopDocs td = searcher.search(q, 10);
        // the 2 best live docs by "v"
        int[] expected = deleteBest ? new int[] {2, 1} : new int[] {3, 2};
        int[] actual = Arrays.stream(td.scoreDocs).mapToInt(sd -> sd.doc).toArray();
        int count = searcher.count(q);
        String state = "hits=" + Arrays.toString(actual) + " count=" + count;
        assertArrayEquals("top-2 live docs; " + state, expected, actual);
        assertEquals("count vs. collected hits; " + state, actual.length, count);
      }
    }
  }

  /** Bug: the deleted best doc takes a top-2 slot; 1 hit is returned and count() says 2. */
  public void testDeletedDocTakesTopNSlot() throws Exception {
    check(true);
  }

  /**
   * Bug, same root cause, other loop: a values source that needs scores is rescored inline. All
   * scores tie, so the top-2 are the two lowest live doc ids; deleted doc 0 takes a slot.
   */
  public void testDeletedDocTakesTopNSlotInlinePath() throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 4; i++) {
        Document d = new Document();
        d.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        d.add(new StringField("f", "a", Field.Store.NO));
        w.addDocument(d);
      }
      w.commit();
      w.deleteDocuments(new Term("id", "0"));
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query q = new RescoreTopNQuery(new TermQuery(new Term("f", "a")), DoubleValuesSource.SCORES, 2);
        TopDocs td = searcher.search(q, 10);
        int[] actual = Arrays.stream(td.scoreDocs).mapToInt(sd -> sd.doc).toArray();
        int count = searcher.count(q);
        String state = "hits=" + Arrays.toString(actual) + " count=" + count;
        assertEquals("2 live hits expected; " + state, 2, actual.length);
        assertEquals("count vs. collected hits; " + state, actual.length, count);
      }
    }
  }

  /** Control: no deletions. */
  public void testControlNoDeletions() throws Exception {
    check(false);
  }
}
