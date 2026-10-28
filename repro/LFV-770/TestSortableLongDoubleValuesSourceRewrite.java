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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.NumericUtils;

/**
 * DoubleValuesSource#toSortableLongDoubleValuesSource() is documented as "Convert to {@link
 * LongValuesSource} by calling {@link NumericUtils#doubleToSortableLong}", and LongValuesSource
 * tells clients to call rewrite(IndexSearcher) before getValues. After rewrite the source casts the
 * doubles to long instead (it returns inner.rewrite(searcher).toLongValuesSource()).
 */
public class TestSortableLongDoubleValuesSourceRewrite extends LuceneTestCase {

  private static final double[] VALUES = {0.75, 0.25, -0.5, 2.5};

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    for (double v : VALUES) {
      Document doc = new Document();
      doc.add(new DoubleDocValuesField("price", v));
      w.addDocument(doc);
    }
    w.forceMerge(1);
    w.close();
    reader = DirectoryReader.open(dir);
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /** Control: without rewrite the values are the sortable longs, as documented. */
  public void testValuesWithoutRewrite() throws Exception {
    LongValuesSource src =
        DoubleValuesSource.fromDoubleField("price").toSortableLongDoubleValuesSource();
    assertValues(src);
  }

  /** The documented client protocol: rewrite against the top-level searcher, then getValues. */
  public void testValuesAfterRewrite() throws Exception {
    LongValuesSource src =
        DoubleValuesSource.fromDoubleField("price").toSortableLongDoubleValuesSource();
    assertValues(src.rewrite(searcher));
  }

  /** IndexSearcher rewrites the Sort, so a sort on the sortable source sorts on (long) casts. */
  public void testSortField() throws Exception {
    SortField sf =
        DoubleValuesSource.fromDoubleField("price")
            .toSortableLongDoubleValuesSource()
            .getSortField(false);
    TopFieldDocs td = searcher.search(new MatchAllDocsQuery(), 10, new Sort(sf));
    // ascending by value: -0.5 (doc 2), 0.25 (doc 1), 0.75 (doc 0), 2.5 (doc 3)
    int[] expectedDocs = {2, 1, 0, 3};
    StringBuilder got = new StringBuilder();
    for (ScoreDoc sd : td.scoreDocs) {
      got.append(sd.doc).append('=').append(((FieldDoc) sd).fields[0]).append(' ');
    }
    for (int i = 0; i < expectedDocs.length; i++) {
      FieldDoc fd = (FieldDoc) td.scoreDocs[i];
      assertEquals("hit order, got " + got, expectedDocs[i], fd.doc);
      assertEquals(
          "sort value of doc " + fd.doc + ", got " + got,
          NumericUtils.doubleToSortableLong(VALUES[fd.doc]),
          ((Long) fd.fields[0]).longValue());
    }
  }

  private void assertValues(LongValuesSource src) throws Exception {
    for (LeafReaderContext ctx : reader.leaves()) {
      LongValues values = src.getValues(ctx, null);
      for (int doc = 0; doc < ctx.reader().maxDoc(); doc++) {
        assertTrue(values.advanceExact(doc));
        double v = VALUES[ctx.docBase + doc];
        assertEquals(
            "value of doc " + (ctx.docBase + doc) + " (" + v + ") via " + src,
            NumericUtils.doubleToSortableLong(v),
            values.longValue());
      }
    }
  }
}
