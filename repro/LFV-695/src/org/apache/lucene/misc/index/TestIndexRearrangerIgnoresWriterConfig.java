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

import org.apache.lucene.codecs.simpletext.SimpleTextCodec;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * IndexRearranger takes an IndexWriterConfig, but since the deletions support was added it builds
 * both of its writers with {@code new IndexWriterConfig(config.getAnalyzer())}, so everything else
 * in the caller's config is dropped. Two symptoms, each on the success path:
 *
 * <ul>
 *   <li>the index sort of the config (and of the input) is not applied to the rearranged index, so
 *       an IndexWriter opened with that same config can no longer open the output;
 *   <li>the codec of the config is not used to write the rearranged index.
 * </ul>
 *
 * Public API only.
 */
@LuceneTestCase.SuppressCodecs("SimpleText")
public class TestIndexRearrangerIgnoresWriterConfig extends LuceneTestCase {

  private static final Sort SORT = new Sort(new SortField("ord", SortField.Type.LONG));

  private static Document doc(int ord) {
    Document d = new Document();
    d.add(new BinaryDocValuesField("id", new BytesRef("doc" + ord)));
    d.add(new NumericDocValuesField("ord", ord));
    return d;
  }

  /** Two segments, written with the given config. */
  private static Directory input(IndexWriterConfig iwc) throws Exception {
    Directory in = newDirectory();
    try (IndexWriter w = new IndexWriter(in, iwc.setMergePolicy(NoMergePolicy.INSTANCE))) {
      w.addDocument(doc(3));
      w.addDocument(doc(1));
      w.commit();
      w.addDocument(doc(2));
      w.addDocument(doc(0));
      w.commit();
    }
    return in;
  }

  public void testIndexSortOfConfigIsKept() throws Exception {
    try (Directory in = input(new IndexWriterConfig().setIndexSort(SORT));
        Directory out = newDirectory()) {
      new IndexRearranger(
              in,
              out,
              new IndexWriterConfig().setIndexSort(SORT),
              BinaryDocValueSelector.createLiveSelectorsFromIndex("id", in))
          .execute();

      for (SegmentCommitInfo sci : SegmentInfos.readLatestCommit(out)) {
        assertEquals(
            "index sort of rearranged segment " + sci.info.name,
            SORT.toString(),
            String.valueOf(sci.info.getIndexSort()));
      }
      // The rearranged index can be opened by a writer with the same config as the input.
      new IndexWriter(out, new IndexWriterConfig().setIndexSort(SORT)).close();
    }
  }

  public void testCodecOfConfigIsUsed() throws Exception {
    try (Directory in = input(new IndexWriterConfig());
        Directory out = newDirectory()) {
      new IndexRearranger(
              in,
              out,
              new IndexWriterConfig().setCodec(new SimpleTextCodec()),
              BinaryDocValueSelector.createLiveSelectorsFromIndex("id", in))
          .execute();

      for (SegmentCommitInfo sci : SegmentInfos.readLatestCommit(out)) {
        assertEquals(
            "codec of rearranged segment " + sci.info.name,
            "SimpleText",
            sci.info.getCodec().getName());
      }
    }
  }
}
