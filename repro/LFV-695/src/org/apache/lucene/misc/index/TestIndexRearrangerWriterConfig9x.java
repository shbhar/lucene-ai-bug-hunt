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

import java.io.IOException;
import java.util.List;
import org.apache.lucene.codecs.simpletext.SimpleTextCodec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BitSet;
import org.apache.lucene.util.FixedBitSet;

/**
 * Same checks as TestIndexRearrangerIgnoresWriterConfig, written against the 9.x DocumentSelector
 * API (getFilteredLiveDocs; BinaryDocValueSelector's factories do not exist there). Expected to
 * PASS on 9.x, where IndexRearranger still opened its writer with the caller's config.
 */
@LuceneTestCase.SuppressCodecs("SimpleText")
public class TestIndexRearrangerWriterConfig9x extends LuceneTestCase {

  private static final Sort SORT = new Sort(new SortField("ord", SortField.Type.LONG));

  /** Selects the documents whose "ord" has the given parity. */
  static final class ParitySelector implements IndexRearranger.DocumentSelector {
    final int parity;

    ParitySelector(int parity) {
      this.parity = parity;
    }

    @Override
    public BitSet getFilteredLiveDocs(CodecReader reader) throws IOException {
      FixedBitSet bits = new FixedBitSet(reader.maxDoc());
      NumericDocValues ord = reader.getNumericDocValues("ord");
      for (int d = 0; d < reader.maxDoc(); d++) {
        if (ord.advanceExact(d) && ord.longValue() % 2 == parity) {
          bits.set(d);
        }
      }
      return bits;
    }
  }

  private static Document doc(int ord) {
    Document d = new Document();
    d.add(new NumericDocValuesField("ord", ord));
    return d;
  }

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
              List.of(new ParitySelector(1), new ParitySelector(0)))
          .execute();
      for (SegmentCommitInfo sci : SegmentInfos.readLatestCommit(out)) {
        assertEquals(
            "index sort of rearranged segment " + sci.info.name,
            SORT.toString(),
            String.valueOf(sci.info.getIndexSort()));
      }
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
              List.of(new ParitySelector(1), new ParitySelector(0)))
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
