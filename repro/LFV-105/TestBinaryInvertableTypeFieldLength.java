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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.InvertableType;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * addDocument-only variant (compiles against releases without the column API). An untokenized indexed value that goes through the single-term inversion path (InvertableType
 * BINARY) adds 2 to the field length instead of 1. With DOCS_AND_FREQS and norms enabled this
 * changes the norm, and therefore the BM25 score, compared with indexing the same value with the
 * same field type through addDocument.
 */
public class TestBinaryInvertableTypeFieldLength extends LuceneTestCase {

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
