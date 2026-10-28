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
package org.apache.lucene.backward_codecs.lucene94;

import java.io.IOException;
import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnByteVectorField;
import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.KnnVectorValues.DocIndexIterator;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BitSetIterator;
import org.apache.lucene.util.FixedBitSet;

/**
 * Sparse byte vectors in a Lucene 9.4 segment, read the way ByteKnnVectorFieldSource reads them:
 * position the iterator with advance(doc), then read vectorValue(iterator.index()).
 *
 * <p>6 documents; only docs 0, 2 and 4 have a vector, [doc, doc + 1], so their ordinals are 0, 1
 * and 2.
 */
public class TestLucene94SparseByteVectorsAdvance extends LuceneTestCase {

  private static Directory writeSparseByteIndex() throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(new Lucene94RWCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int doc = 0; doc < 6; doc++) {
        Document d = new Document();
        if (doc % 2 == 0) {
          byte[] vector = {(byte) doc, (byte) (doc + 1)};
          d.add(new KnnByteVectorField("v", vector, VectorSimilarityFunction.EUCLIDEAN));
        }
        w.addDocument(d);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  private static String read(ByteVectorValues values, DocIndexIterator it) throws IOException {
    return "ord " + it.index() + " " + Arrays.toString(values.vectorValue(it.index()));
  }

  /** A fresh iterator, positioned with advance() only. */
  public void testAdvanceThenIndex() throws IOException {
    try (Directory dir = writeSparseByteIndex();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      ByteVectorValues values = getOnlyLeafReader(reader).getByteVectorValues("v");
      DocIndexIterator it = values.iterator();
      assertEquals(2, it.advance(2));
      assertEquals("doc 2 read through advance()/index()", "ord 1 [2, 3]", read(values, it));
    }
  }

  /** nextDoc() first, then advance(): index() still names the earlier document. */
  public void testNextDocThenAdvanceThenIndex() throws IOException {
    try (Directory dir = writeSparseByteIndex();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      ByteVectorValues values = getOnlyLeafReader(reader).getByteVectorValues("v");
      DocIndexIterator it = values.iterator();
      assertEquals(0, it.nextDoc());
      assertEquals(4, it.advance(4));
      assertEquals("doc 4 read after nextDoc(), advance(4)", "ord 2 [4, 5]", read(values, it));
    }
  }

  /** The helper itself: docs 1, 3, 5 and 7 have ordinals 0 to 3. */
  public void testFromDISIAdvanceKeepsOrdinal() throws IOException {
    FixedBitSet docs = new FixedBitSet(8);
    docs.set(1);
    docs.set(3);
    docs.set(5);
    docs.set(7);
    DocIndexIterator it = FromDISI.of(new BitSetIterator(docs, 4));
    assertEquals(5, it.advance(5));
    assertEquals("index() of doc 5", 2, it.index());
  }

  /** gives the test access to the protected static KnnVectorValues.fromDISI */
  private abstract static class FromDISI extends ByteVectorValues {
    static DocIndexIterator of(DocIdSetIterator disi) {
      return fromDISI(disi);
    }
  }
}
