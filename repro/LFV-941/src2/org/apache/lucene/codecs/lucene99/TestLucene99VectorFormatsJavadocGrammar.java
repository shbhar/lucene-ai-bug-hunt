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
package org.apache.lucene.codecs.lucene99;

import java.util.Random;
import java.util.TreeSet;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.hnsw.HnswGraphProvider;
import org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.hnsw.HnswGraph;

/**
 * The class javadocs of {@link Lucene99FlatVectorsFormat} (.vemf) and {@link
 * Lucene99HnswVectorsFormat} (.vem, .vex) document, per field: [int32] field number, [int32] vector
 * similarity function ordinal, [vlong] offset, [vlong] length, [vint] dimension, [int] count, and
 * for .vemf an [int8] dense/sparse marker (not checked); .vex stores, per level and
 * node, [vint] the number of neighbours and array[vint] the delta-encoded neighbour ordinals. These
 * tests parse the files written by the default codec with that grammar. Only public API is used.
 *
 * <p>Release variant (10.x releases): the graph is obtained through the public
 * PerFieldKnnVectorsFormat.FieldsReader#getFieldReader of those releases.
 */
public class TestLucene99VectorFormatsJavadocGrammar extends LuceneTestCase {

  private static final String FIELD = "v";

  private static Directory index(int numDocs) throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setUseCompoundFile(false)
            .setMergePolicy(NoMergePolicy.INSTANCE);
    Random r = new Random(42);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < numDocs; i++) {
        Document doc = new Document();
        float[] v = {r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()};
        doc.add(new KnnFloatVectorField(FIELD, v, VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(doc);
      }
    }
    return dir;
  }

  private static String file(Directory dir, String ext) throws Exception {
    for (String f : dir.listAll()) {
      if (ext.equals(IndexFileNames.getExtension(f))) {
        return f;
      }
    }
    throw new AssertionError("no ." + ext + " file");
  }

  private static String suffix(String file) {
    String s = IndexFileNames.stripExtension(file);
    return s.substring(s.indexOf('_', 1) + 1);
  }

  private static FieldInfo fieldInfo(Directory dir) throws Exception {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      return r.leaves().get(0).reader().getFieldInfos().fieldInfo(FIELD);
    }
  }

  /** .vemf: the second int32 is documented as the similarity function ordinal. */
  public void testFlatMetaDocumentedSequence() throws Exception {
    try (Directory dir = index(10)) {
      FieldInfo fi = fieldInfo(dir);
      byte[] id = SegmentInfos.readLatestCommit(dir).info(0).info.getId();
      String vemf = file(dir, "vemf");
      try (IndexInput in = dir.openInput(vemf, IOContext.READONCE)) {
        CodecUtil.checkIndexHeader(
            in, "Lucene99FlatVectorsFormatMeta", 0, Integer.MAX_VALUE, id, suffix(vemf));
        assertEquals("field number", fi.number, in.readInt());
        assertEquals(
            "vector similarity function ordinal ("
                + fi.getVectorSimilarityFunction()
                + ", encoding "
                + fi.getVectorEncoding()
                + ")",
            fi.getVectorSimilarityFunction().ordinal(),
            in.readInt());
        in.readVLong(); // offset
        in.readVLong(); // length
        assertEquals("dimension", fi.getVectorDimension(), in.readVInt());
        assertEquals("count", 10, in.readInt());
        // the [int8] dense/sparse marker that follows is not checked here: open PR #15430 already
        // rewrites that line of the javadoc
      }
    }
  }

  /** .vem: the second int32 is documented as the similarity function ordinal. */
  public void testHnswMetaDocumentedSequence() throws Exception {
    try (Directory dir = index(10)) {
      FieldInfo fi = fieldInfo(dir);
      byte[] id = SegmentInfos.readLatestCommit(dir).info(0).info.getId();
      String vem = file(dir, "vem");
      try (IndexInput in = dir.openInput(vem, IOContext.READONCE)) {
        CodecUtil.checkIndexHeader(
            in, "Lucene99HnswVectorsFormatMeta", 0, Integer.MAX_VALUE, id, suffix(vem));
        assertEquals("field number", fi.number, in.readInt());
        int similarity = in.readInt();
        in.readVLong(); // offset
        in.readVLong(); // length
        assertEquals(
            "dimension (similarity ordinal read as " + similarity + ")",
            fi.getVectorDimension(),
            in.readVInt());
      }
    }
  }

  /** .vex: neighbour lists are documented as a vint count followed by vint deltas. */
  public void testGraphNeighboursDocumentedAsVInts() throws Exception {
    try (Directory dir = index(1000)) {
      byte[] id = SegmentInfos.readLatestCommit(dir).info(0).info.getId();
      String vem = file(dir, "vem");
      String vex = file(dir, "vex");
      long fieldOffset;
      try (IndexInput meta = dir.openInput(vem, IOContext.READONCE)) {
        CodecUtil.checkIndexHeader(
            meta, "Lucene99HnswVectorsFormatMeta", 0, Integer.MAX_VALUE, id, suffix(vem));
        meta.readInt(); // field number
        meta.readInt(); // vector encoding (undocumented)
        meta.readInt(); // similarity
        fieldOffset = meta.readVLong();
      }
      try (DirectoryReader r = DirectoryReader.open(dir);
          IndexInput in = dir.openInput(vex, IOContext.READONCE)) {
        CodecReader leaf = (CodecReader) r.leaves().get(0).reader();
        // 10.x releases: the per-field reader is public and hands out the HNSW reader of the field
        HnswGraph graph =
            ((HnswGraphProvider)
                    ((PerFieldKnnVectorsFormat.FieldsReader) leaf.getVectorReader())
                        .getFieldReader(FIELD))
                .getGraph(FIELD);
        assertTrue("the segment must have a graph", graph != null && graph.size() == 1000);
        in.seek(fieldOffset);
        for (int node = 0; node < graph.size(); node++) {
          TreeSet<Integer> expected = new TreeSet<>();
          graph.seek(0, node);
          for (int nb = graph.nextNeighbor(); nb != DocIdSetIterator.NO_MORE_DOCS; ) {
            expected.add(nb);
            nb = graph.nextNeighbor();
          }
          int count = in.readVInt();
          TreeSet<Integer> parsed = new TreeSet<>();
          int ord = 0;
          for (int i = 0; i < count; i++) {
            ord += in.readVInt();
            parsed.add(ord);
          }
          assertEquals("level 0 neighbours of node " + node, expected, parsed);
        }
      }
    }
  }
}
