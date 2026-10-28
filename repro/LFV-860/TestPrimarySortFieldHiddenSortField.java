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
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterDirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.FieldFilterLeafReader;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Hiding a field that a query does not use must not change the query's hits. Here the index is
 * sorted by (c, f) and a FilterLeafReader hides c. The documents are still in (c, f) order, and
 * getMetaData().sort() still says so, but Sort.getPrimarySortField treats c as "no values in this
 * segment" because c is not in the reader's FieldInfos, and returns f. The doc-values range queries
 * then use their index-sort shortcuts on f, whose values are not in doc order.
 */
public class TestPrimarySortFieldHiddenSortField extends LuceneTestCase {

  // (c, f) pairs; sorted by (c, f) the docs hold f = 5, 9, 1, 7, 3
  private static final long[][] DOCS = {{1, 7}, {0, 9}, {2, 3}, {0, 5}, {1, 1}};

  private interface DocBuilder {
    void add(Document doc, long c, long f);
  }

  private static Directory index(Sort sort, DocBuilder builder) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setIndexSort(sort);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (long[] cf : DOCS) {
        Document doc = new Document();
        builder.add(doc, cf[0], cf[1]);
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  /** Wraps every leaf in a FieldFilterLeafReader that hides the given field. */
  private static DirectoryReader hide(DirectoryReader in, String field) throws IOException {
    return new FilterDirectoryReader(
        in,
        new FilterDirectoryReader.SubReaderWrapper() {
          @Override
          public LeafReader wrap(LeafReader reader) {
            return new FieldFilterLeafReader(reader, Set.of(field), true);
          }
        }) {
      @Override
      protected DirectoryReader doWrapDirectoryReader(DirectoryReader in) throws IOException {
        return hide(in, field);
      }

      @Override
      public CacheHelper getReaderCacheHelper() {
        return null;
      }
    };
  }

  private static long expected(long lo, long hi) {
    long n = 0;
    for (long[] cf : DOCS) {
      if (cf[1] >= lo && cf[1] <= hi) {
        n++;
      }
    }
    return n;
  }

  private static void check(Sort sort, DocBuilder builder, Query q, long expected)
      throws IOException {
    try (Directory dir = index(sort, builder);
        DirectoryReader plain = DirectoryReader.open(dir);
        DirectoryReader hidden = hide(DirectoryReader.open(dir), "c")) {
      assertEquals(1, plain.leaves().size());
      IndexSearcher sp = new IndexSearcher(plain);
      sp.setQueryCache(null);
      IndexSearcher sh = new IndexSearcher(hidden);
      sh.setQueryCache(null);
      // control: the unwrapped reader is right
      assertEquals("unwrapped reader, " + q, expected, sp.count(q));
      assertEquals("unwrapped reader, " + q, expected, sp.search(q, 10).totalHits.value());
      // the same query on f, with the unrelated field c hidden
      assertEquals("reader hiding c, search(), " + q, expected, sh.search(q, 10).totalHits.value());
      assertEquals("reader hiding c, count(), " + q, expected, sh.count(q));
    }
  }

  /** NumericDocValuesField with a skip index: SortedNumericDocValuesRangeQuery's shortcut. */
  public void testNumericDocValuesRange() throws IOException {
    Sort sort =
        new Sort(new SortField("c", SortField.Type.LONG), new SortField("f", SortField.Type.LONG));
    DocBuilder b =
        (doc, c, f) -> {
          doc.add(NumericDocValuesField.indexedField("c", c));
          doc.add(NumericDocValuesField.indexedField("f", f));
        };
    check(sort, b, NumericDocValuesField.newSlowRangeQuery("f", 1, 3), expected(1, 3));
  }

  /** LongField (points + doc values, no skip index): IndexSortSortedNumericDocValuesRangeQuery. */
  public void testLongFieldRange() throws IOException {
    Sort sort =
        new Sort(
            new SortedNumericSortField("c", SortField.Type.LONG),
            new SortedNumericSortField("f", SortField.Type.LONG));
    DocBuilder b =
        (doc, c, f) -> {
          doc.add(new LongField("c", c, Field.Store.NO));
          doc.add(new LongField("f", f, Field.Store.NO));
        };
    check(sort, b, LongField.newRangeQuery("f", 1, 3), expected(1, 3));
  }

  /** SortedDocValuesField with a skip index: SortedSetDocValuesRangeQuery's shortcut. */
  public void testSortedDocValuesRange() throws IOException {
    Sort sort =
        new Sort(
            new SortField("c", SortField.Type.LONG), new SortField("f", SortField.Type.STRING));
    DocBuilder b =
        (doc, c, f) -> {
          doc.add(NumericDocValuesField.indexedField("c", c));
          doc.add(SortedDocValuesField.indexedField("f", new BytesRef(Long.toString(f))));
        };
    Query q =
        SortedDocValuesField.newSlowRangeQuery(
            "f", new BytesRef("1"), new BytesRef("3"), true, true);
    check(sort, b, q, expected(1, 3));
  }
}
