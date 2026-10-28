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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.column.BinaryColumn;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.DictionaryColumn;
import org.apache.lucene.document.column.ObjectTupleCursor;
import org.apache.lucene.document.column.OrdinalsTupleCursor;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * IndexWriter#addBatch indexes an untokenized BinaryColumn or DictionaryColumn value through the
 * single-term path (InvertableType.BINARY). It must get the same norm and BM25 score as the same
 * value added with addDocument.
 */
public class TestColumnBatchUntokenizedNorms extends LuceneTestCase {

  private static final FieldType TYPE = new FieldType();

  static {
    TYPE.setTokenized(false);
    TYPE.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    TYPE.setOmitNorms(false); // the FieldType default
    TYPE.freeze();
  }

  /** A batch of one document that has the value "x" in the column. */
  private static ColumnBatch oneDocument(Column column) {
    return new ColumnBatch() {
      @Override
      public int numDocs() {
        return 1;
      }

      @Override
      public Iterable<Column> columns() {
        return List.of(column);
      }
    };
  }

  private static BinaryColumn binaryColumn() {
    return new BinaryColumn("tag", TYPE, Column.Density.SPARSE) {
      @Override
      public ObjectTupleCursor<BytesRef> tuples() {
        return new ObjectTupleCursor<>() {
          int i = -1;

          @Override
          public int nextDoc() {
            return ++i == 0 ? 0 : DocIdSetIterator.NO_MORE_DOCS;
          }

          @Override
          public BytesRef value() {
            return new BytesRef("x");
          }
        };
      }
    };
  }

  private static DictionaryColumn dictionaryColumn() {
    return new DictionaryColumn("tag", TYPE, Column.Density.SPARSE, List.of(new BytesRef("x"))) {
      @Override
      public OrdinalsTupleCursor tuples() {
        return new OrdinalsTupleCursor() {
          int i = -1;

          @Override
          public int nextDoc() {
            return ++i == 0 ? 0 : DocIdSetIterator.NO_MORE_DOCS;
          }

          @Override
          public int ordValue() {
            return 0;
          }
        };
      }
    };
  }

  public void testBatchMatchesAddDocument() throws IOException {
    try (Directory dir = newDirectory()) {
      // default IndexWriterConfig: BM25 similarity
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new Field("tag", new BytesRef("x"), TYPE));
        w.addDocument(doc); // doc 0
        w.addBatch(oneDocument(binaryColumn())); // doc 1
        w.addBatch(oneDocument(dictionaryColumn())); // doc 2
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(3, reader.maxDoc());
        NumericDocValues norms = MultiDocValues.getNormValues(reader, "tag");
        long[] norm = new long[3];
        for (int doc = 0; doc < 3; doc++) {
          assertTrue(norms.advanceExact(doc));
          norm[doc] = norms.longValue();
        }
        float[] score = new float[3];
        TermQuery query = new TermQuery(new Term("tag", "x"));
        for (ScoreDoc hit : new IndexSearcher(reader).search(query, 3).scoreDocs) {
          score[hit.doc] = hit.score;
        }
        String observed =
            "norms [addDocument, BinaryColumn, DictionaryColumn] = "
                + Arrays.toString(norm)
                + ", BM25 scores = "
                + Arrays.toString(score);
        assertEquals(observed, norm[0], norm[1]);
        assertEquals(observed, norm[0], norm[2]);
        assertEquals(observed, score[0], score[1], 0f);
        assertEquals(observed, score[0], score[2], 0f);
      }
    }
  }
}
