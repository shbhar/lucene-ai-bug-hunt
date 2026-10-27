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
package org.apache.lucene.misc.index;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Two more symptoms of the same dropped IndexWriterConfig. Here IndexRearranger.execute() throws,
 * and a failed execute() also leaves its executor threads running (a separate defect), so thread
 * leak checking is turned off for this class to keep the report about the config only.
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class TestIndexRearrangerIgnoresWriterConfigOnFailurePath extends LuceneTestCase {

  private static Document doc(String id) {
    Document d = new Document();
    d.add(new StringField("sid", id, Field.Store.NO));
    d.add(new BinaryDocValuesField("id", new BytesRef(id)));
    return d;
  }

  /** OpenMode.CREATE in the config is ignored, so rearranging again into the same output fails. */
  public void testOpenModeCreateOfConfigIsUsed() throws Exception {
    try (Directory in = newDirectory();
        Directory out = newDirectory()) {
      try (IndexWriter w = new IndexWriter(in, new IndexWriterConfig())) {
        w.addDocument(doc("a"));
        w.addDocument(doc("b"));
      }
      for (int run = 0; run < 2; run++) {
        new IndexRearranger(
                in,
                out,
                new IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.CREATE),
                BinaryDocValueSelector.createLiveSelectorsFromIndex("id", in))
            .execute();
      }
      try (IndexReader r = DirectoryReader.open(out)) {
        assertEquals(1, r.leaves().size());
        assertEquals(2, r.numDocs());
      }
    }
  }

  /** The soft-deletes field of the config is ignored, so an index with soft deletes is rejected. */
  public void testSoftDeletesFieldOfConfigIsUsed() throws Exception {
    try (Directory in = newDirectory();
        Directory out = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(
              in,
              new IndexWriterConfig()
                  .setSoftDeletesField("soft")
                  .setMergePolicy(NoMergePolicy.INSTANCE))) {
        w.addDocument(doc("a"));
        w.softUpdateDocument(new Term("sid", "a"), doc("b"), new NumericDocValuesField("soft", 1));
      }
      new IndexRearranger(
              in,
              out,
              new IndexWriterConfig().setSoftDeletesField("soft"),
              BinaryDocValueSelector.createLiveSelectorsFromIndex("id", in))
          .execute();
      try (IndexReader r = DirectoryReader.open(out)) {
        assertEquals(2, r.maxDoc());
      }
    }
  }
}
