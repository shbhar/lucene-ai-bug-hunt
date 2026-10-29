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
package org.apache.lucene.codecs.lucene104;

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.lucene104.Lucene104PostingsFormat.IntBlockTermState;
import org.apache.lucene.index.DocValuesSkipIndexType;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.VectorEncoding;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.InfoStream;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.Version;

public class TestDocIDRunEndNearMaxDoc extends LuceneTestCase {

  public void testDenseBlockAfterLastLevel1Block() throws Exception {
    FieldInfo fi =
        new FieldInfo(
            "f", 0, false, true, false, IndexOptions.DOCS, DocValuesType.NONE,
            DocValuesSkipIndexType.NONE, -1, Map.of(), 0, 0, 0, 0, VectorEncoding.FLOAT32,
            VectorSimilarityFunction.EUCLIDEAN, false, false);
    FieldInfos fieldInfos = new FieldInfos(new FieldInfo[] {fi});
    int firstDoc = Integer.MAX_VALUE - 16383;
    try (Directory dir = new ByteBuffersDirectory()) {
      SegmentInfo si =
          new SegmentInfo(
              dir, Version.LATEST, Version.LATEST, "_0", IndexWriter.MAX_DOCS, false, false,
              Codec.getDefault(), Map.of(), StringHelper.randomId(), new HashMap<>(), null);
      IntBlockTermState state = new IntBlockTermState();
      state.docFreq = 8192 + 256;
      SegmentWriteState writeState =
          new SegmentWriteState(InfoStream.NO_OUTPUT, dir, si, fieldInfos, null, IOContext.DEFAULT);
      try (Lucene104PostingsWriter writer = new Lucene104PostingsWriter(writeState)) {
        writer.setField(fi);
        writer.startTerm(null);
        for (int i = 0; i < state.docFreq; i++) {
          writer.startDoc(firstDoc + i, 1);
          writer.finishDoc();
        }
        writer.finishTerm(state);
      }
      SegmentReadState readState = new SegmentReadState(dir, si, fieldInfos, IOContext.DEFAULT);
      try (Lucene104PostingsReader reader = new Lucene104PostingsReader(readState)) {
        PostingsEnum pe = reader.postings(fi, state, null, PostingsEnum.NONE);
        int doc = pe.advance(firstDoc + 8192);
        int runEnd = pe.docIDRunEnd();
        // one level-1 block, then a dense block: expected runEnd > docID(), actual MIN_VALUE
        assertTrue("expected docIDRunEnd() > " + doc + ", got " + runEnd, runEnd > doc);
      }
    }
  }
}
