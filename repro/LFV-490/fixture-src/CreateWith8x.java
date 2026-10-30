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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;

/**
 * Step 1, run with Lucene 8.11.4: create the index (indexCreatedVersionMajor = 8) with a single
 * seed document. Step 2 (ReindexWith9x, Lucene 9.12.3) deletes it, so every segment of the final
 * index is written by 9.12.3.
 */
public class CreateWith8x {
  public static void main(String[] args) throws Exception {
    try (Directory dir = new NIOFSDirectory(Paths.get(args[0]));
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new StringField("id", "seed", Field.Store.YES));
      w.addDocument(doc);
      w.commit();
    }
  }
}
