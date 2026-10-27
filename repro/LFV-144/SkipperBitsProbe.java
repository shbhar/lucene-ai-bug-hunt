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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValuesSkipper;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.NumericUtils;

/**
 * Prints the numbers behind the explanation: raw vs sortable bits of a few floats, the skip
 * index blocks of the finder's index (doc i has value -i, 10,000 docs, one segment), and the
 * ascending and descending top 3 with the number of documents the collector saw, for the field with a skip
 * index ("skip") and the control without one ("plain"), for doc i = -i, doc i = +i, and a mixed
 * layout (-i for the first 8,192 docs, +i after).
 */
public class SkipperBitsProbe {
  public static void main(String[] args) throws Exception {
    float[] samples = {1f, 0f, -0f, -1f, -2f, -997f, -4095f, -4096f, -8191f, -8192f, -9999f};
    System.out.println("value | raw bits (floatToRawIntBits) | sortable bits (floatToSortableInt)");
    for (float v : samples) {
      System.out.printf(
          "%s | %d | %d%n", v, Float.floatToRawIntBits(v), NumericUtils.floatToSortableInt(v));
    }
    Path tmp = Files.createTempDirectory("skipper-bits-probe");
    // layout 0: doc i has -i; layout 1: doc i has +i; layout 2: -i for docs 0-8191, +i after
    for (int layout = 0; layout < 3; layout++) {
      Path p = tmp.resolve("layout" + layout);
      try (Directory dir = FSDirectory.open(p)) {
        try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
          for (int i = 0; i < 10_000; i++) {
            float v = layout == 0 || (layout == 2 && i < 8192) ? -(float) i : (float) i;
            Document doc = new Document();
            doc.add(new StringField("all", "x", Field.Store.NO));
            doc.add(NumericDocValuesField.indexedField("skip", Float.floatToRawIntBits(v)));
            doc.add(new FloatDocValuesField("plain", v));
            w.addDocument(doc);
          }
          w.forceMerge(1);
        }
        try (DirectoryReader reader = DirectoryReader.open(dir)) {
          String[] names = {"-i", "+i", "-i for docs 0-8191, +i after"};
          System.out.println("== values " + names[layout] + ", leaves=" + reader.leaves().size());
          LeafReader leaf = reader.leaves().get(0).reader();
          DocValuesSkipper skipper = leaf.getDocValuesSkipper("skip");
          System.out.println("skipper levels=" + skipper.numLevels());
          int target = 0;
          while (true) {
            skipper.advance(target);
            if (skipper.minDocID(0) == DocIdSetIterator.NO_MORE_DOCS) {
              break;
            }
            System.out.printf(
                "block docs [%d, %d]: raw min %d (%s), raw max %d (%s)%n",
                skipper.minDocID(0),
                skipper.maxDocID(0),
                skipper.minValue(0),
                Float.intBitsToFloat((int) skipper.minValue(0)),
                skipper.maxValue(0),
                Float.intBitsToFloat((int) skipper.maxValue(0)));
            target = skipper.maxDocID(0) + 1;
          }
          IndexSearcher searcher = new IndexSearcher(reader);
          searcher.setQueryCache(null);
          Query q = new TermQuery(new Term("all", "x"));
          for (boolean reverse : new boolean[] {false, true}) {
            for (String field : new String[] {"plain", "skip"}) {
              TopFieldDocs td =
                  searcher.search(q, 3, new Sort(new SortField(field, SortField.Type.FLOAT, reverse)));
              Object[] vals = new Object[td.scoreDocs.length];
              for (int i = 0; i < vals.length; i++) {
                vals[i] = ((FieldDoc) td.scoreDocs[i]).fields[0];
              }
              System.out.println(
                  (reverse ? "descending" : "ascending")
                      + " top 3 on "
                      + field
                      + ": "
                      + Arrays.toString(vals)
                      + ", totalHits "
                      + td.totalHits);
            }
          }
        }
      }
    }
  }
}
