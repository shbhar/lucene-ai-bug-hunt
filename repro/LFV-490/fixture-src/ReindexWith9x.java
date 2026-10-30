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
import java.nio.file.Paths;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.BytesRef;

/**
 * Step 2, run with Lucene 9.12.3 on the index CreateWith8x made: delete the 8.x seed document and
 * add documents the way Lucene 9 accepts them for an index created by Lucene 8 (LUCENE-10518:
 * field data structures need not be the same in every document). The 8.x segment becomes fully
 * deleted and is dropped, so every remaining segment has version = minVersion = 9.12.3.
 *
 * <p>Usage: ReindexWith9x DIR exists|exists-merged
 *
 * <ul>
 *   <li>exists: the two documents of Lucene 9's TestFieldExistsQuery#testOldIndices, one segment
 *       each: d1 has my_field as a StringField plus a BinaryDocValuesField, d2 only the StringField.
 *   <li>exists-merged: the same, then forceMerge(1) by 9.12.3 (one segment holding both).
 * </ul>
 */
public class ReindexWith9x {
  public static void main(String[] args) throws Exception {
    String mode = args[1];
    try (Directory dir = new NIOFSDirectory(Paths.get(args[0]))) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setOpenMode(IndexWriterConfig.OpenMode.APPEND)
              .setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.deleteDocuments(new Term("id", "seed"));
        if (mode.startsWith("exists")) {
          Document d1 = new Document();
          d1.add(new StringField("id", "d1", Field.Store.YES));
          d1.add(new StringField("my_field", "text", Field.Store.YES));
          d1.add(new BinaryDocValuesField("my_field", new BytesRef("first")));
          w.addDocument(d1);
          w.flush();
          Document d2 = new Document();
          d2.add(new StringField("id", "d2", Field.Store.YES));
          d2.add(new StringField("my_field", "text", Field.Store.YES));
          w.addDocument(d2);
          w.commit();
        } else {
          throw new IllegalArgumentException(mode);
        }
      }
      if (mode.equals("exists-merged")) {
        // a second writer with the default merge policy merges the 9.12.3 segments into one
        try (IndexWriter w =
            new IndexWriter(
                dir, new IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.APPEND))) {
          w.forceMerge(1);
          w.commit();
        }
      }
      SegmentInfos sis = SegmentInfos.readLatestCommit(dir);
      StringBuilder sb = new StringBuilder();
      for (SegmentCommitInfo si : sis) {
        sb.append(' ').append(si.info.name).append(" version=").append(si.info.getVersion());
        sb.append(" minVersion=").append(si.info.getMinVersion()).append(" maxDoc=").append(si.info.maxDoc());
      }
      System.out.println("indexCreatedVersionMajor=" + sis.getIndexCreatedVersionMajor() + sb);
    }
  }
}
