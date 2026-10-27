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
package emptyblock;

import java.util.Collections;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Version-portable main() variant (8.x and later): exits 1 when the empty-block leak is present.
 * Argument "soft" uses softUpdateDocuments (soft-deletes field configured) instead of
 * updateDocuments.
 */
public class EmptyBlockUpdateLeakMain {
  public static void main(String[] args) throws Exception {
    boolean soft = args.length > 0 && args[0].equals("soft");
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig();
    if (soft) {
      iwc.setSoftDeletesField("soft");
    }
    IndexWriter w =
        new IndexWriter(
            dir,
            iwc
                .setRAMBufferSizeMB(1.0)
                .setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
                .setMergePolicy(NoMergePolicy.INSTANCE));
    Document d = new Document();
    d.add(new StringField("id", "a", Field.Store.YES));
    w.addDocument(d);
    w.commit();
    for (int i = 0; i < 100; i++) {
      if (soft) {
        w.softUpdateDocuments(
            new Term("id", "a"), Collections.<Document>emptyList(), new NumericDocValuesField("soft", 1));
      } else {
        w.updateDocuments(new Term("id", "a"), Collections.<Document>emptyList());
      }
      w.commit();
    }
    long ram = w.ramBytesUsed();
    for (int i = 0; i < 200; i++) {
      Document n = new Document();
      n.add(new StringField("id", "n" + i, Field.Store.YES));
      w.addDocument(n);
    }
    w.commit();
    int segments;
    try (DirectoryReader r = DirectoryReader.open(w)) {
      segments = r.leaves().size();
    }
    System.out.println("ramBytesUsed after 100 empty-block updates=" + ram + "; 200 docs -> segments=" + segments);
    try {
      w.close();
    } catch (Throwable t) {
      System.out.println("close failed: " + t);
    }
    if (ram >= 1024 * 1024 || segments > 3) {
      System.out.println("LEAK PRESENT");
      System.exit(1);
    }
    System.out.println("no leak");
  }
}
