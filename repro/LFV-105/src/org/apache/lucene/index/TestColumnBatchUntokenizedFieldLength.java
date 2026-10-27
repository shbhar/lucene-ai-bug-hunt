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
package org.apache.lucene.index;

import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.InvertableType;
import org.apache.lucene.document.column.BinaryColumn;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.ObjectTupleCursor;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * An untokenized indexed value that goes through the single-term inversion path (InvertableType
 * BINARY) adds 2 to the field length instead of 1. With DOCS_AND_FREQS and norms enabled this
 * changes the norm, and therefore the BM25 score, compared with indexing the same value with the
 * same field type through addDocument.
 */
public class TestColumnBatchUntokenizedFieldLength extends LuceneTestCase {

  private static FieldType keywordWithFreqsAndNorms() {
    FieldType ft = new FieldType();
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    ft.setTokenized(false);
    ft.setOmitNorms(false);
    ft.freeze();
    return ft;
  }

  private static IndexWriterConfig config() {
    return new IndexWriterConfig()
        .setCodec(TestUtil.getDefaultCodec())
        .setMergePolicy(NoMergePolicy.INSTANCE);
  }

  private static long norm(Directory dir) throws Exception {
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      NumericDocValues norms = reader.leaves().get(0).reader().getNormValues("tag");
      assertTrue(norms.advanceExact(0));
      return norms.longValue();
    }
  }

  private static float score(Directory dir) throws Exception {
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      TopDocs td = new IndexSearcher(reader).search(new TermQuery(new Term("tag", "x")), 1);
      assertEquals(1, td.totalHits.value());
      return td.scoreDocs[0].score;
    }
  }

  /** Same document via addDocument (reference) and via addBatch with a BinaryColumn. */
  public void testBatchMatchesAddDocument() throws Exception {
    FieldType ft = keywordWithFreqsAndNorms();
    try (Directory viaDocument = new ByteBuffersDirectory();
        Directory viaBatch = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(viaDocument, config())) {
        Document doc = new Document();
        doc.add(new Field("tag", "x", ft));
        w.addDocument(doc);
      }
      try (IndexWriter w = new IndexWriter(viaBatch, config())) {
        BinaryColumn column =
            new BinaryColumn("tag", ft, Column.Density.SPARSE) {
              @Override
              public ObjectTupleCursor<BytesRef> tuples() {
                return new ObjectTupleCursor<>() {
                  int pos = -1;

                  @Override
                  public int nextDoc() {
                    pos++;
                    return pos == 0 ? 0 : DocIdSetIterator.NO_MORE_DOCS;
                  }

                  @Override
                  public BytesRef value() {
                    return new BytesRef("x");
                  }
                };
              }
            };
        w.addBatch(
            new ColumnBatch() {
              @Override
              public int numDocs() {
                return 1;
              }

              @Override
              public Iterable<Column> columns() {
                return List.of(column);
              }
            });
      }
      // one value, one token: both paths must record the same field length
      assertEquals(norm(viaDocument), norm(viaBatch));
      assertEquals(score(viaDocument), score(viaBatch), 0f);
    }
  }

  /**
   * The same discrepancy on the addDocument path: a field that asks for the single-term inversion
   * path (InvertableType.BINARY) gets a different norm than the identical field inverted through a
   * TokenStream.
   */
  public void testBinaryInvertableTypeMatchesTokenStream() throws Exception {
    FieldType ft = keywordWithFreqsAndNorms();
    try (Directory tokenStreamPath = new ByteBuffersDirectory();
        Directory binaryPath = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(tokenStreamPath, config())) {
        Document doc = new Document();
        doc.add(new Field("tag", new BytesRef("x"), ft)); // Field#invertableType() is TOKEN_STREAM
        w.addDocument(doc);
      }
      try (IndexWriter w = new IndexWriter(binaryPath, config())) {
        Document doc = new Document();
        doc.add(
            new Field("tag", new BytesRef("x"), ft) {
              @Override
              public InvertableType invertableType() {
                return InvertableType.BINARY;
              }
            });
        w.addDocument(doc);
      }
      assertEquals(norm(tokenStreamPath), norm(binaryPath));
    }
  }
}
