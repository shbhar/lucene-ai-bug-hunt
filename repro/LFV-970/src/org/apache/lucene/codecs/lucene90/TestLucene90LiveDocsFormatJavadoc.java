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
package org.apache.lucene.codecs.lucene90;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestLucene90LiveDocsFormatJavadoc extends LuceneTestCase {

  public void testGenerationFollowsIndexHeader() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String id : new String[] {"0", "1"}) {
          Document doc = new Document();
          doc.add(new StringField("id", id, Field.Store.NO));
          w.addDocument(doc);
        }
        w.deleteDocuments(new Term("id", "0"));
      }
      SegmentCommitInfo sci = SegmentInfos.readLatestCommit(dir).info(0);
      long gen = sci.getDelGen();
      String file = IndexFileNames.fileNameFromGeneration(sci.info.name, "liv", gen);
      try (IndexInput in = dir.openInput(file, IOContext.READONCE)) {
        String suffix = Long.toString(gen, Character.MAX_RADIX);
        CodecUtil.checkIndexHeader(in, "Lucene90LiveDocs", 0, 0, sci.info.getId(), suffix);
        // javadoc: IndexHeader,Generation,Bits; actual: gen is the header suffix, next is Bits 0b10
        assertEquals("Generation", gen, in.readLong());
      }
    }
  }
}
