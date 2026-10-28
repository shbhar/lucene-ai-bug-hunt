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
package org.apache.lucene.spatial.prefix;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.spatial.prefix.tree.DateRangePrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.locationtech.spatial4j.shape.Shape;

/**
 * DateRangePrefixTree.parseCalendar rejects day 32 and month 13 with a ParseException, but a day
 * that its month does not have (November 31, February 30) is accepted and rolled into the next
 * month, so the range [2014-11-01 TO 2014-11-31] also matches a document dated 2014-12-01.
 */
public class TestDateRangeNonexistentDay extends LuceneTestCase {

  private static DateRangePrefixTree[] trees() {
    return new DateRangePrefixTree[] {
      new DateRangePrefixTree(DateRangePrefixTree.DEFAULT_CAL),
      new DateRangePrefixTree(DateRangePrefixTree.JAVA_UTIL_TIME_COMPAT_CAL)
    };
  }

  /** Controls: out-of-range fields are rejected; real month ends parse as written. */
  public void testControls() throws Exception {
    for (DateRangePrefixTree tree : trees()) {
      expectThrows(ParseException.class, () -> tree.parseCalendar("2014-11-32"));
      expectThrows(ParseException.class, () -> tree.parseCalendar("2014-13-01"));
      assertEquals("2014-11-30", tree.toString(tree.parseCalendar("2014-11-30")));
      assertEquals("2016-02-29", tree.toString(tree.parseCalendar("2016-02-29")));
      assertEquals(
          "2014-11-30T24", "2014-12-01T00", tree.toString(tree.parseCalendar("2014-11-30T24")));
    }
  }

  /** A day that its month does not have should be rejected, like day 32. */
  public void testNonexistentDayIsRejected() throws Exception {
    List<String> accepted = new ArrayList<>();
    for (DateRangePrefixTree tree : trees()) {
      for (String s : new String[] {"2014-11-31", "2014-02-30", "2013-02-29", "2014-04-31T10"}) {
        try {
          accepted.add(s + " -> " + tree.toString(tree.parseCalendar(s)));
        } catch (
            @SuppressWarnings("unused")
            ParseException expected) {
          // correct
        }
      }
    }
    assertTrue("non-existent days accepted: " + accepted, accepted.isEmpty());
  }

  /** Query-level effect: "all of November" written with day 31 also matches December 1st. */
  public void testNovemberRangeDoesNotMatchDecember() throws Exception {
    DateRangePrefixTree tree = new DateRangePrefixTree(DateRangePrefixTree.DEFAULT_CAL);
    NumberRangePrefixTreeStrategy strategy = new NumberRangePrefixTreeStrategy(tree, "date");
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      for (String d : new String[] {"2014-11-15", "2014-12-01", "2014-12-02"}) {
        Document doc = new Document();
        for (Field f : strategy.createIndexableFields(tree.parseShape(d))) {
          doc.add(f);
        }
        doc.add(new StoredField("id", d));
        w.addDocument(doc);
      }
      w.commit();
      Shape november;
      try {
        november = tree.parseShape("[2014-11-01 TO 2014-11-31]");
      } catch (
          @SuppressWarnings("unused")
          ParseException expected) {
        return; // correct: the non-existent upper bound is rejected
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        List<String> hits = new ArrayList<>();
        for (ScoreDoc sd :
            s.search(strategy.makeQuery(new SpatialArgs(SpatialOperation.Intersects, november)), 10)
                .scoreDocs) {
          hits.add(s.storedFields().document(sd.doc).get("id"));
        }
        fail("[2014-11-01 TO 2014-11-31] parsed to " + november + " and matched " + hits);
      }
    }
  }
}
