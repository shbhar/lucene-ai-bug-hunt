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

package org.apache.lucene.queries.intervals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Intervals.before(source, reference) "returns intervals from the source that appear before
 * intervals from the reference". Nothing appears before the first token of a field, so a source at
 * the first position must not match against a reference that also starts there.
 */
public class TestIntervalsBeforeFirstPosition extends LuceneTestCase {

  private static final String FIELD = "f";

  /** Indexes one document per text and returns the indexes of the texts that match. */
  private static List<Integer> matchingTexts(IntervalsSource source, String... texts)
      throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < texts.length; i++) {
          Document doc = new Document();
          doc.add(new TextField(FIELD, texts[i], Field.Store.NO));
          doc.add(new StoredField("id", i));
          w.addDocument(doc);
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        StoredFields storedFields = searcher.storedFields();
        List<Integer> ids = new ArrayList<>();
        for (ScoreDoc hit : searcher.search(new IntervalQuery(FIELD, source), 10).scoreDocs) {
          ids.add(storedFields.document(hit.doc).getField("id").numericValue().intValue());
        }
        Collections.sort(ids);
        return ids;
      }
    }
  }

  /** A single "york" is not before itself, wherever it is; only "york york" has one. */
  public void testLoneTermIsNotBeforeItself() throws IOException {
    IntervalsSource york = Intervals.term("york");
    assertEquals(
        "before(york, york)",
        List.of(2),
        matchingTexts(Intervals.before(york, york), "york", "x york", "york york"));
  }

  /** "new" is not before the phrase "new york" that it starts; only "new x new york" matches. */
  public void testTermIsNotBeforePhraseItStarts() throws IOException {
    IntervalsSource q = Intervals.before(Intervals.term("new"), Intervals.phrase("new", "york"));
    assertEquals(
        q.toString(),
        List.of(3),
        matchingTexts(q, "new york", "x new york", "new york city", "new x new york"));
  }

  /** Controls that pass today: the same texts with ordered(), after() and a later reference. */
  public void testControls() throws IOException {
    IntervalsSource york = Intervals.term("york");
    IntervalsSource newTerm = Intervals.term("new");
    String[] yorks = {"york", "x york", "york york"};
    assertEquals(List.of(2), matchingTexts(Intervals.ordered(york, york), yorks));
    assertEquals(List.of(2), matchingTexts(Intervals.after(york, york), yorks));
    String[] news = {"new york", "x new york", "new york city", "new x new york"};
    assertEquals(
        List.of(3),
        matchingTexts(Intervals.ordered(newTerm, Intervals.phrase("new", "york")), news));
    assertEquals(
        List.of(2),
        matchingTexts(Intervals.before(newTerm, Intervals.phrase("york", "city")), news));
  }
}
