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
package org.apache.lucene.queries.spans;

import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Bits;

/**
 * 10.6+ only (FrequentTermsSpanBooleanQueryRewrite, #16484). The most frequent term and the
 * smallest term select disjoint documents, yet the two wrappers compare equal, so with both
 * deletes in one flush window only the first one is applied, in either order.
 */
public class TestSpanFrequentTermsWrapperDeleteAliasing extends LuceneTestCase {

  private static final String FIELD = "f";

  private static SpanMultiTermQueryWrapper<PrefixQuery> wrapper(
      SpanMultiTermQueryWrapper.SpanRewriteMethod method) {
    SpanMultiTermQueryWrapper<PrefixQuery> q =
        new SpanMultiTermQueryWrapper<>(new PrefixQuery(new Term(FIELD, "aa")));
    q.setRewriteMethod(method);
    return q;
  }

  /** The most frequent term only: aa3 (3 documents). */
  private static Query mostFrequent() {
    return wrapper(new SpanMultiTermQueryWrapper.FrequentTermsSpanBooleanQueryRewrite(1));
  }

  /** The top term by boost only: all boosts tie, so the smallest term, aa1 (1 document). */
  private static Query smallest() {
    return wrapper(new SpanMultiTermQueryWrapper.TopTermsSpanBooleanQueryRewrite(1));
  }

  /** aa1 in 1 document, aa2 in 2, aa3 in 3; ids 0..5. */
  private static void addSixDocs(IndexWriter w) throws Exception {
    String[] values = {"aa1", "aa2", "aa2", "aa3", "aa3", "aa3"};
    for (int i = 0; i < values.length; i++) {
      Document d = new Document();
      d.add(new StringField("id", Integer.toString(i), Field.Store.YES));
      d.add(new TextField(FIELD, values[i], Field.Store.NO));
      w.addDocument(d);
    }
  }

  private static Set<String> liveIds(Directory dir) throws Exception {
    Set<String> ids = new TreeSet<>();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      for (var ctx : r.leaves()) {
        Bits live = ctx.reader().getLiveDocs();
        StoredFields sf = ctx.reader().storedFields();
        for (int d = 0; d < ctx.reader().maxDoc(); d++) {
          if (live == null || live.get(d)) {
            ids.add(sf.document(d).get("id"));
          }
        }
      }
    }
    return ids;
  }

  public void testDisjointSelectionsCompareEqual() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        addSixDocs(w);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        assertEquals(3, s.count(mostFrequent()));
        assertEquals(1, s.count(smallest()));
        assertFalse(
            "wrappers that match disjoint documents (3 and 1) compare equal",
            mostFrequent().equals(smallest()));
      }
    }
  }

  private void deleteBoth(boolean frequentFirst) throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        addSixDocs(w);
        w.commit();
        if (frequentFirst) {
          w.deleteDocuments(mostFrequent()); // ids 3, 4, 5
          w.deleteDocuments(smallest()); // id 0
        } else {
          w.deleteDocuments(smallest());
          w.deleteDocuments(mostFrequent());
        }
        w.commit();
      }
      assertEquals(
          "after deleting the aa3 and the aa1 documents only the aa2 documents remain",
          Set.of("1", "2"),
          liveIds(dir));
    }
  }

  public void testDeleteFrequentThenSmallest() throws Exception {
    deleteBoth(true);
  }

  public void testDeleteSmallestThenFrequent() throws Exception {
    deleteBoth(false);
  }
}
