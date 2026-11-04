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
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.StoredFieldsFormat;
import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.codecs.StoredFieldsWriter;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestUnderscoreInCodecFileExtension extends LuceneTestCase {

  // Expected: commit succeeds with "_0.my_ext"; actual: NoSuchFileException: _0_ext.
  public void testUnderscoreInExtension() throws IOException {
    StoredFieldsFormat in = TestUtil.getDefaultCodec().storedFieldsFormat();
    StoredFieldsFormat format =
        new StoredFieldsFormat() {
          @Override
          public StoredFieldsReader fieldsReader(
              Directory d, SegmentInfo si, FieldInfos fn, IOContext ctx) throws IOException {
            return in.fieldsReader(d, si, fn, ctx);
          }

          @Override
          public StoredFieldsWriter fieldsWriter(Directory d, SegmentInfo si, IOContext ctx)
              throws IOException {
            try (IndexOutput out =
                d.createOutput(IndexFileNames.segmentFileName(si.name, "", "my_ext"), ctx)) {
              CodecUtil.writeIndexHeader(out, "Extra", 0, si.getId(), "");
              CodecUtil.writeFooter(out);
            }
            return in.fieldsWriter(d, si, ctx);
          }
        };
    FilterCodec codec =
        new FilterCodec("ExtraFileCodec", TestUtil.getDefaultCodec()) {
          @Override
          public StoredFieldsFormat storedFieldsFormat() {
            return format;
          }
        };
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(codec))) {
      w.addDocument(new Document());
      w.commit();
    }
  }
}
