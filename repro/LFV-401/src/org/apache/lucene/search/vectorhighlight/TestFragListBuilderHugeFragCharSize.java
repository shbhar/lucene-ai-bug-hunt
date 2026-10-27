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
package org.apache.lucene.search.vectorhighlight;

import java.io.IOException;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * BaseFragListBuilder.createFieldFragList computes the end of a fragment as
 * {@code Math.max(currentPhraseEndOffset, spanStart + fragCharSize)} in int. fragCharSize has only a
 * lower bound, so a caller that asks for the whole field with fragCharSize = Integer.MAX_VALUE gets a
 * wrapped, negative sum as soon as spanStart > 0 (the first match starts more than 'margin' chars
 * into the field). The fragment then ends at the first match, the rebuilt fragment covers the whole
 * field anyway, and every later match is discarded: only the first match is highlighted.
 */
public class TestFragListBuilderHugeFragCharSize extends LuceneTestCase {

  private static final String TEXT = "aaaaaaaaaaaaaaaaaaaa foo bbb foo ccc foo ddd";
  private static final String ALL_HIGHLIGHTED =
      "aaaaaaaaaaaaaaaaaaaa <b>foo</b> bbb <b>foo</b> ccc <b>foo</b> ddd";

  private static String[] highlight(int fragCharSize) throws IOException {
    try (Directory dir = newDirectory()) {
      FieldType ft = new FieldType(TextField.TYPE_STORED);
      ft.setStoreTermVectors(true);
      ft.setStoreTermVectorPositions(true);
      ft.setStoreTermVectorOffsets(true);
      ft.freeze();
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new WhitespaceAnalyzer()))) {
        Document d = new Document();
        d.add(new Field("f", TEXT, ft));
        w.addDocument(d);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        FastVectorHighlighter fvh = new FastVectorHighlighter(); // SimpleFragListBuilder, margin 6
        FieldQuery fq = fvh.getFieldQuery(new TermQuery(new Term("f", "foo")), r);
        return fvh.getBestFragments(fq, r, 0, "f", fragCharSize, 5);
      }
    }
  }

  /** Control: a fragment size larger than the field highlights all three matches. */
  public void testLargeFragCharSize() throws IOException {
    assertArrayEquals(new String[] {ALL_HIGHLIGHTED}, highlight(1000));
    assertArrayEquals(new String[] {ALL_HIGHLIGHTED}, highlight(Integer.MAX_VALUE - 100));
  }

  /** fragCharSize = Integer.MAX_VALUE ("the whole field"): only the first match is highlighted. */
  public void testMaxValueFragCharSize() throws IOException {
    assertArrayEquals(new String[] {ALL_HIGHLIGHTED}, highlight(Integer.MAX_VALUE));
  }
}
