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
import java.util.Map;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.DocValuesProducer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestPartitionedMergeChecksumsWrapperProducers extends LuceneTestCase {

  public void testDocValuesOfAnotherIndex() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        Directory copy = new ByteBuffersDirectory()) {
      IndexWriterConfig config = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, config.setUseCompoundFile(false))) {
        for (int i = 0; i < 2; i++) {
          Document doc = new Document();
          doc.add(new NumericDocValuesField("n", i));
          w.addDocument(doc);
        }
      }
      for (String name : dir.listAll()) {
        try (IndexInput in = dir.openInput(name, IOContext.READONCE);
            IndexOutput out = copy.createOutput(name, IOContext.DEFAULT)) {
          byte[] bytes = new byte[(int) in.length()];
          in.readBytes(bytes, 0, bytes.length);
          if (name.endsWith(".dvd")) {
            bytes[bytes.length - CodecUtil.footerLength() - 1] ^= 1;
          }
          out.writeBytes(bytes, bytes.length);
        }
      }
      try (DirectoryReader copyReader = DirectoryReader.open(copy)) {
        CodecReader copyLeaf = (CodecReader) getOnlyLeafReader(copyReader);
        MergePolicy policy =
            new FilterMergePolicy(NoMergePolicy.INSTANCE) {
              @Override
              public MergeSpecification findForcedMerges(
                  SegmentInfos infos,
                  int maxSegmentCount,
                  Map<SegmentCommitInfo, Boolean> segmentsToMerge,
                  MergeContext mergeContext) {
                if (infos.size() != 1) {
                  return null;
                }
                MergeSpecification spec = new MergeSpecification();
                spec.add(
                    new OneMerge(infos.asList()) {
                      @Override
                      public boolean isPartitioned() {
                        return true;
                      }

                      @Override
                      public DocRangePartitions getDocRangePartitions(List<CodecReader> readers) {
                        return DocRangePartitions.of(new int[][] {{0, 1, 2}});
                      }

                      @Override
                      public CodecReader wrapForMerge(CodecReader reader) {
                        return new FilterCodecReader(reader) {
                          @Override
                          public DocValuesProducer getDocValuesReader() {
                            return copyLeaf.getDocValuesReader();
                          }

                          @Override
                          public CacheHelper getCoreCacheHelper() {
                            return null;
                          }

                          @Override
                          public CacheHelper getReaderCacheHelper() {
                            return null;
                          }
                        };
                      }
                    });
                return spec;
              }
            };
        IndexWriterConfig mergeConfig =
            new IndexWriterConfig()
                .setCodec(TestUtil.getDefaultCodec())
                .setMergePolicy(policy)
                .setMergeScheduler(new SerialMergeScheduler());
        try (IndexWriter w = new IndexWriter(dir, mergeConfig)) {
          // expected: CorruptIndexException, as from an ordinary merge; actual: the merge completes
          expectThrows(CorruptIndexException.class, () -> w.forceMerge(1));
        }
      }
    }
  }
}
