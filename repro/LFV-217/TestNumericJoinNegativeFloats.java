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
package org.apache.lucene.search.join;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * JoinUtil.createJoinQuery(..., Float.class / Double.class, ...) collects the from-side values as
 * raw IEEE bits (FloatDocValuesField / DoubleDocValuesField), sorts them as signed longs and
 * streams them, in that order, into PointInSetQuery / PointInSetIncludingScoreQuery, which require
 * points in ascending (sortable-encoded) order. For negative values the two orders differ.
 */
public class TestNumericJoinNegativeFloats extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      // "from" side: two docs with negative join values -1 and -2
      for (float v : new float[] {-1f, -2f}) {
        Document d = new Document();
        d.add(new StringField("side", "from", Field.Store.NO));
        d.add(new FloatDocValuesField("fromF", v));
        d.add(new DoubleDocValuesField("fromD", v));
        w.addDocument(d);
      }
      // "to" side: one doc per value
      for (float v : new float[] {-1f, -2f, 3f}) {
        Document d = new Document();
        d.add(new StringField("side", "to", Field.Store.NO));
        d.add(new FloatPoint("toF", v));
        d.add(new DoublePoint("toD", v));
        w.addDocument(d);
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private void check(String from, String to, Class<? extends Number> type, ScoreMode sm)
      throws Exception {
    Query fromQuery = new TermQuery(new Term("side", "from"));
    Query join = JoinUtil.createJoinQuery(from, false, to, type, fromQuery, searcher, sm);
    // to-docs with value -1 and -2 are joined
    assertEquals(type.getSimpleName() + " " + sm, 2, searcher.count(join));
  }

  public void testFloatNoScore() throws Exception {
    check("fromF", "toF", Float.class, ScoreMode.None);
  }

  public void testFloatWithScore() throws Exception {
    check("fromF", "toF", Float.class, ScoreMode.Max);
  }

  public void testDoubleNoScore() throws Exception {
    check("fromD", "toD", Double.class, ScoreMode.None);
  }

  public void testDoubleWithScore() throws Exception {
    check("fromD", "toD", Double.class, ScoreMode.Avg);
  }
}
