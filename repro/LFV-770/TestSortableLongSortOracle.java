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
package verifyv3;

import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.NumericUtils;

/**
 * Public-API only (package outside org.apache.lucene), plain IndexWriterConfig and IndexSearcher.
 * Oracle: Lucene's own SortField("v", Type.DOUBLE). doubleToSortableLong is order-preserving for
 * doubles (NumericUtils javadoc), so a sort on toSortableLongDoubleValuesSource() must give the
 * same document order as the plain double sort, in both directions and across searchAfter pages.
 */
public class TestSortableLongSortOracle extends LuceneTestCase {

  private static final double[] VALUES = {1.5, -3.75, 1.25, Double.NaN, 0.0, -0.0, 1e300, 0.5};

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    for (double v : VALUES) {
      Document doc = new Document();
      doc.add(new DoubleDocValuesField("v", v));
      w.addDocument(doc);
    }
    w.forceMerge(1);
    w.close();
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private int[] order(Sort sort, int pageSize) throws Exception {
    int[] out = new int[VALUES.length];
    int n = 0;
    FieldDoc after = null;
    while (n < VALUES.length) {
      TopFieldDocs td =
          (TopFieldDocs) searcher.searchAfter(after, new MatchAllDocsQuery(), pageSize, sort);
      if (td.scoreDocs.length == 0) break;
      for (ScoreDoc sd : td.scoreDocs) {
        out[n++] = sd.doc;
      }
      after = (FieldDoc) td.scoreDocs[td.scoreDocs.length - 1];
    }
    return Arrays.copyOf(out, n);
  }

  private LongValuesSource sortable() {
    return DoubleValuesSource.fromDoubleField("v").toSortableLongDoubleValuesSource();
  }

  /** Control: the oracle itself orders by Double.compare. */
  public void testOracleIsDoubleCompareOrder() throws Exception {
    int[] got = order(new Sort(new SortField("v", SortField.Type.DOUBLE)), VALUES.length);
    for (int i = 1; i < got.length; i++) {
      assertTrue(Double.compare(VALUES[got[i - 1]], VALUES[got[i]]) <= 0);
    }
  }

  public void testAscendingMatchesDoubleSort() throws Exception {
    int[] expected = order(new Sort(new SortField("v", SortField.Type.DOUBLE)), VALUES.length);
    int[] actual = order(new Sort(sortable().getSortField(false)), VALUES.length);
    assertArrayEquals(
        "expected " + Arrays.toString(expected) + " got " + Arrays.toString(actual),
        expected,
        actual);
  }

  public void testDescendingMatchesDoubleSort() throws Exception {
    int[] expected =
        order(new Sort(new SortField("v", SortField.Type.DOUBLE, true)), VALUES.length);
    int[] actual = order(new Sort(sortable().getSortField(true)), VALUES.length);
    assertArrayEquals(
        "expected " + Arrays.toString(expected) + " got " + Arrays.toString(actual),
        expected,
        actual);
  }

  public void testSearchAfterPagesMatchDoubleSort() throws Exception {
    int[] expected = order(new Sort(new SortField("v", SortField.Type.DOUBLE)), 3);
    int[] actual = order(new Sort(sortable().getSortField(false)), 3);
    assertArrayEquals(
        "expected " + Arrays.toString(expected) + " got " + Arrays.toString(actual),
        expected,
        actual);
  }

  /** FieldDoc sort values must be the documented encoding. */
  public void testFieldDocValuesAreSortableLongs() throws Exception {
    TopFieldDocs td =
        searcher.search(new MatchAllDocsQuery(), VALUES.length, new Sort(sortable().getSortField(false)));
    for (ScoreDoc sd : td.scoreDocs) {
      FieldDoc fd = (FieldDoc) sd;
      assertEquals(
          "doc " + fd.doc + " value " + VALUES[fd.doc],
          NumericUtils.doubleToSortableLong(VALUES[fd.doc]),
          ((Long) fd.fields[0]).longValue());
    }
  }
}
