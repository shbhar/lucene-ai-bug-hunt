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
package org.apache.lucene.codecs.uniformsplit.sharedterms;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestSTUniformSplitMergeMappingChange extends LuceneTestCase {

  private static Codec codec(PostingsFormat forA, PostingsFormat forB) {
    return new Lucene104Codec() {
      @Override
      public PostingsFormat getPostingsFormatForField(String field) {
        return field.equals("a") ? forA : forB;
      }
    };
  }

  private static Document doc(String a, String b) {
    Document doc = new Document();
    doc.add(new StringField("a", a, Field.Store.NO));
    doc.add(new StringField("b", b, Field.Store.NO));
    return doc;
  }

  public void testMergeTakesFieldFromAnotherFormat() throws Exception {
    PostingsFormat st = new STUniformSplitPostingsFormat();
    try (Directory dir = new ByteBuffersDirectory()) {
      Codec before = codec(st, TestUtil.getDefaultPostingsFormat());
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(before))) {
        w.addDocument(doc("a1", "b1"));
      }
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(codec(st, st)))) {
        w.addDocument(doc("a2", "b2"));
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        // b:b1 was written under the default format: expected docFreq 1 after the merge, actual 0
        assertEquals("docFreq(b:b1) after forceMerge", 1, r.docFreq(new Term("b", "b1")));
      }
    }
  }
}
