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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSortRescorerTopNScore extends LuceneTestCase {

  public void testTopHitKeepsItsOwnScore() throws Exception {
    ByteBuffersDirectory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    for (long popularity : new long[] {1, 2}) {
      Document doc = new Document();
      doc.add(new NumericDocValuesField("popularity", popularity));
      w.addDocument(doc);
    }
    w.close();
    DirectoryReader r = DirectoryReader.open(dir);
    TopDocs firstPass =
        new TopDocs(
            new TotalHits(2, TotalHits.Relation.EQUAL_TO),
            new ScoreDoc[] {new ScoreDoc(0, 2f), new ScoreDoc(1, 1f)});
    Sort byPopularity = new Sort(new SortField("popularity", SortField.Type.LONG, true));
    TopDocs top1 = new SortRescorer(byPopularity).rescore(new IndexSearcher(r), firstPass, 1);
    r.close();
    dir.close();
    assertEquals(1, top1.scoreDocs[0].doc);
    // expected: doc 1 keeps its own first-pass score 1.0; actual: doc 0's score 2.0
    assertEquals("score of doc 1", 1f, top1.scoreDocs[0].score, 0f);
  }
}
