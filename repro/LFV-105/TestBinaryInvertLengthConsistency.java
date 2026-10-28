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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.InvertableType;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Checks whether the field length recorded for a single-term (InvertableType.BINARY) field agrees
 * with the other places in Lucene that describe the same document: the term statistics written by
 * the same IndexWriter, the TokenStream inversion of the same field type, and MemoryIndex's own
 * single-term inversion path.
 */
public class TestBinaryInvertLengthConsistency extends LuceneTestCase {

  private static FieldType keywordWithFreqsAndNorms() {
    FieldType ft = new FieldType();
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    ft.setTokenized(false);
    ft.setOmitNorms(false);
    ft.freeze();
    return ft;
  }

  /** Like KeywordField/StringField: no TokenStream, single binary term. */
  private static Field binaryField(String name, String value, FieldType ft) {
    return new Field(name, new BytesRef(value), ft) {
      @Override
      public InvertableType invertableType() {
        return InvertableType.BINARY;
      }

      @Override
      public TokenStream tokenStream(Analyzer analyzer, TokenStream reuse) {
        return null;
      }
    };
  }

  private static long[] indexWriterNormAndSumTtf(Field... fields) throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(
                dir,
                new IndexWriterConfig()
                    .setCodec(TestUtil.getDefaultCodec())
                    .setMergePolicy(NoMergePolicy.INSTANCE))) {
      Document doc = new Document();
      for (Field f : fields) {
        doc.add(f);
      }
      w.addDocument(doc);
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LeafReader leaf = getOnlyLeafReader(reader);
        NumericDocValues norms = leaf.getNormValues("tag");
        assertTrue(norms.advanceExact(0));
        return new long[] {norms.longValue(), leaf.terms("tag").getSumTotalTermFreq()};
      }
    }
  }

  private static long memoryIndexNorm(Field... fields) throws Exception {
    MemoryIndex mi = new MemoryIndex();
    for (Field f : fields) {
      mi.addField(f, null);
    }
    LeafReader leaf = (LeafReader) mi.createSearcher().getIndexReader();
    NumericDocValues norms = leaf.getNormValues("tag");
    assertTrue(norms.advanceExact(0));
    return norms.longValue();
  }

  /** Single value: norm must equal the per-document sum of term frequencies (1). */
  public void testSingleValueNormMatchesSumTotalTermFreq() throws Exception {
    FieldType ft = keywordWithFreqsAndNorms();
    long[] iw = indexWriterNormAndSumTtf(binaryField("tag", "x", ft));
    long mem = memoryIndexNorm(binaryField("tag", "x", ft));
    long[] ts = indexWriterNormAndSumTtf(new Field("tag", new BytesRef("x"), ft));
    System.out.println(
        "single: IW-BINARY norm="
            + iw[0]
            + " sumTTF="
            + iw[1]
            + "; IW-TOKEN_STREAM norm="
            + ts[0]
            + " sumTTF="
            + ts[1]
            + "; MemoryIndex-BINARY norm="
            + mem);
    assertEquals("MemoryIndex (same single-term path, same commit) norm", 1, mem);
    assertEquals("TokenStream path norm", 1, ts[0]);
    assertEquals("sumTotalTermFreq on BINARY path", 1, iw[1]);
    assertEquals("IndexWriter BINARY-path norm vs sumTotalTermFreq", iw[1], iw[0]);
  }

  /** Three values a, b, a: TokenStream path and MemoryIndex record length 3. */
  public void testMultiValuedNormMatchesSumTotalTermFreq() throws Exception {
    FieldType ft = keywordWithFreqsAndNorms();
    long[] iw =
        indexWriterNormAndSumTtf(
            binaryField("tag", "a", ft), binaryField("tag", "b", ft), binaryField("tag", "a", ft));
    long mem =
        memoryIndexNorm(
            binaryField("tag", "a", ft), binaryField("tag", "b", ft), binaryField("tag", "a", ft));
    long[] ts =
        indexWriterNormAndSumTtf(
            new Field("tag", new BytesRef("a"), ft),
            new Field("tag", new BytesRef("b"), ft),
            new Field("tag", new BytesRef("a"), ft));
    System.out.println(
        "multi: IW-BINARY norm="
            + iw[0]
            + " sumTTF="
            + iw[1]
            + "; IW-TOKEN_STREAM norm="
            + ts[0]
            + " sumTTF="
            + ts[1]
            + "; MemoryIndex-BINARY norm="
            + mem);
    assertEquals("MemoryIndex norm", 3, mem);
    assertEquals("TokenStream path norm", 3, ts[0]);
    assertEquals("sumTotalTermFreq on BINARY path", 3, iw[1]);
    assertEquals("IndexWriter BINARY-path norm vs sumTotalTermFreq", iw[1], iw[0]);
  }
}
