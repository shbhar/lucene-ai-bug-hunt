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
package org.apache.lucene.document;

import org.apache.lucene.geo.GeoEncodingUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.search.CheckHits;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.SloppyMath;

/**
 * LatLonPointDistanceFeatureQuery's scorer turns a minimum competitive score into a maximum
 * distance with a binary search that stops once the interval is 1 metre wide, and keeps the lower
 * end. The bounding box built from it is padded by only 7 cm, so a document whose distance lies
 * between (lower end + 7 cm) and the true threshold distance still has a competitive score but is
 * skipped.
 *
 * <p>Layout (origin 0,0, pivot 1000 m, weight 1, all points on the meridian lon=0, due north):
 *
 * <ul>
 *   <li>doc 0: about 999.999 m from the origin (score 0.50000024)
 *   <li>docs 1..NUM_FAR: far away (lat 40..), they only make the pruning box selective
 *   <li>last doc: about 999.803 m from the origin, i.e. closer than doc 0 (score 0.5000492)
 * </ul>
 *
 * After doc 0 fills a top-1 queue the collector sets minCompetitiveScore = nextUp(0.50000024).
 * The binary search returns about 999.729 m, the box reaches about 999.799 m, so the last doc
 * (999.803 m, score 0.5000492 >= minCompetitiveScore) is outside the box and never scored.
 */
public class TestLatLonDistanceFeatureMinCompetitiveGap extends LuceneTestCase {

  private static final String FIELD = "loc";
  private static final int NUM_FAR = 5000;
  // encoded latitudes (GeoEncodingUtils) of the two near points, both at longitude 0
  private static final int NEAR_THRESHOLD_LAT = 214586; // ~999.999 m north of the origin
  private static final int NEAR_TARGET_LAT = 214544; // ~999.803 m north of the origin

  private static void addPoint(IndexWriter w, double lat, double lon) throws Exception {
    Document doc = new Document();
    doc.add(new LatLonPoint(FIELD, lat, lon));
    doc.add(new LatLonDocValuesField(FIELD, lat, lon));
    doc.add(new StringField("type", "shop", Field.Store.NO));
    w.addDocument(doc);
  }

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;
  private Query query;
  private int targetDoc;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    iwc.setRAMBufferSizeMB(256);
    iwc.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    IndexWriter w = new IndexWriter(dir, iwc);
    addPoint(w, GeoEncodingUtils.decodeLatitude(NEAR_THRESHOLD_LAT), 0);
    for (int i = 0; i < NUM_FAR; i++) {
      addPoint(w, 40 + i * 1e-3, 100);
    }
    addPoint(w, GeoEncodingUtils.decodeLatitude(NEAR_TARGET_LAT), 0);
    targetDoc = NUM_FAR + 1;
    w.close();
    reader = DirectoryReader.open(dir);
    assertEquals(1, reader.leaves().size());
    searcher = new IndexSearcher(reader);
    query = LatLonPoint.newDistanceFeatureQuery(FIELD, 1f, 0, 0, 1000);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  public void testLayout() {
    double d0 =
        SloppyMath.haversinMeters(0, 0, GeoEncodingUtils.decodeLatitude(NEAR_THRESHOLD_LAT), 0);
    double d1 = SloppyMath.haversinMeters(0, 0, GeoEncodingUtils.decodeLatitude(NEAR_TARGET_LAT), 0);
    // the target doc is strictly closer than doc 0, by about 20 cm
    assertTrue(d1 < d0);
    assertTrue(d0 - d1 > 0.15);
  }

  public void testTopScoresTop1MatchesComplete() throws Exception {
    TopDocs complete = searcher.search(query, new TopScoreDocCollectorManager(1, Integer.MAX_VALUE));
    TopDocs topScores = searcher.search(query, new TopScoreDocCollectorManager(1, 1));
    assertEquals("COMPLETE top-1 should be the closest doc", targetDoc, complete.scoreDocs[0].doc);
    assertEquals(
        "TOP_SCORES top-1 (score "
            + topScores.scoreDocs[0].score
            + ") differs from COMPLETE top-1 (score "
            + complete.scoreDocs[0].score
            + ")",
        complete.scoreDocs[0].doc,
        topScores.scoreDocs[0].doc);
  }

  public void testDefaultSearchTop1() throws Exception {
    // IndexSearcher#search(Query, int) counts 1000 hits exactly, then prunes
    TopDocs topDocs = searcher.search(query, 1);
    assertEquals(
        "closest doc missing from search(query, 1); returned score " + topDocs.scoreDocs[0].score,
        targetDoc,
        topDocs.scoreDocs[0].doc);
  }

  public void testFilterPlusDistanceBoost() throws Exception {
    // "filter, then rank by distance": the FILTER clause has no score, so ReqOptSumScorer passes the
    // collector's minimum competitive score straight to the distance scorer
    Query q =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("type", "shop")), Occur.FILTER)
            .add(query, Occur.SHOULD)
            .build();
    TopDocs complete = searcher.search(q, new TopScoreDocCollectorManager(1, Integer.MAX_VALUE));
    TopDocs topScores = searcher.search(q, 1);
    assertEquals(targetDoc, complete.scoreDocs[0].doc);
    assertEquals(
        "closest doc missing from search(filter + distance boost, 1); returned score "
            + topScores.scoreDocs[0].score,
        targetDoc,
        topScores.scoreDocs[0].doc);
  }

  public void testLaterSegment() throws Exception {
    // Natural trigger in a multi-segment index: segment 1 holds doc 0 (and far docs), segment 2 starts
    // with the closer doc. When the collector moves to segment 2 it passes the already known minimum
    // competitive score to the new scorer (TopScoreDocCollector#setScorer), whose very first box
    // already excludes the closer doc.
    Directory dir2 = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    iwc.setRAMBufferSizeMB(256);
    iwc.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    IndexWriter w = new IndexWriter(dir2, iwc);
    addPoint(w, GeoEncodingUtils.decodeLatitude(NEAR_THRESHOLD_LAT), 0);
    for (int i = 0; i < NUM_FAR; i++) {
      addPoint(w, 40 + i * 1e-3, 100);
    }
    w.commit(); // segment 1
    addPoint(w, GeoEncodingUtils.decodeLatitude(NEAR_TARGET_LAT), 0);
    for (int i = 0; i < NUM_FAR; i++) {
      addPoint(w, -40 - i * 1e-3, -100);
    }
    w.close(); // segment 2
    try (DirectoryReader r2 = DirectoryReader.open(dir2)) {
      assertEquals(2, r2.leaves().size());
      IndexSearcher s2 = new IndexSearcher(r2);
      int expected = NUM_FAR + 1; // first doc of segment 2
      TopDocs complete = s2.search(query, new TopScoreDocCollectorManager(1, Integer.MAX_VALUE));
      assertEquals(expected, complete.scoreDocs[0].doc);
      TopDocs topDocs = s2.search(query, 1);
      assertEquals(
          "closest doc (first doc of the second segment) missing from search(query, 1); returned score "
              + topDocs.scoreDocs[0].score,
          expected,
          topDocs.scoreDocs[0].doc);
    } finally {
      dir2.close();
    }
  }

  public void testCheckTopScores() throws Exception {
    CheckHits.checkTopScores(random(), query, searcher);
  }
}
