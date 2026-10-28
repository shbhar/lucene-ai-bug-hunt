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

import java.util.Random;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.BytesRef;

/**
 * Same defect as TestFullFlushFailedFreezeDropsDeletes, with a real OutOfMemoryError and no
 * InfoStream (default NO_OUTPUT): about 60 MB of buffered delete terms, then an NRT reopen in a
 * small heap (run with -Xmx160m). The reopen has no documents to flush, so it freezes the buffered
 * deletes on their own; that allocation fails, and the caller gets IllegalStateException("Can't
 * close queue unless all changes are applied") instead of the OutOfMemoryError, the writer stays
 * open, and the acknowledged deleteDocuments(id:1) is missing from the next commit.
 *
 * <p>Exits 1 if the Error is not what the caller sees, or if the writer stays open and the next
 * commit lacks the acknowledged delete. Arg: buffered delete-term RAM in MB (default 60).
 */
public class RealOomeDuringNakedFreezeMain {
  public static void main(String[] args) throws Exception {
    long targetMB = args.length > 0 ? Long.parseLong(args[0]) : 60;
    ByteBuffersDirectory dir = new ByteBuffersDirectory();
    IndexWriter w =
        new IndexWriter(
            dir,
            new IndexWriterConfig(new StandardAnalyzer())
                .setRAMBufferSizeMB(4096) // keep the deletes buffered until the reopen
                .setMergePolicy(NoMergePolicy.INSTANCE));
    for (String id : new String[] {"1", "2"}) {
      Document d = new Document();
      d.add(new StringField("id", id, Field.Store.YES));
      w.addDocument(d);
    }
    w.commit();
    Random random = new Random(42);
    byte[] b = new byte[64];
    while (w.ramBytesUsed() < targetMB * 1024 * 1024) {
      random.nextBytes(b);
      w.deleteDocuments(new Term("other", new BytesRef(b.clone())));
    }
    long seqNo = w.deleteDocuments(new Term("id", "1")); // acknowledged
    System.out.println("buffered deletes: " + (w.ramBytesUsed() >> 20) + " MB; delete(id:1) seqNo=" + seqNo);

    Throwable thrown = null;
    try {
      DirectoryReader.open(w).close();
    } catch (Throwable t) {
      thrown = t;
    }
    boolean open = w.isOpen();
    System.out.println("NRT reopen threw: " + thrown + "; writer open=" + open + "; tragic=" + w.getTragicException());
    if (thrown == null) {
      System.out.println("INCONCLUSIVE: no OutOfMemoryError at this heap size; try another size");
      System.exit(2);
    }
    boolean sawOome = false;
    for (Throwable t = thrown; t != null; t = t.getCause()) {
      if (t instanceof OutOfMemoryError) sawOome = true;
      for (Throwable s : t.getSuppressed()) if (s instanceof OutOfMemoryError) sawOome = true;
    }
    int failures = 0;
    if (sawOome == false) {
      System.out.println("FAIL: the OutOfMemoryError was replaced by " + thrown.getClass().getName());
      failures++;
    }
    if (open) {
      w.commit();
      w.close();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        int live = new IndexSearcher(r).count(new TermQuery(new Term("id", "1")));
        System.out.println("after commit: id:1 live docs = " + live);
        if (live != 0) {
          System.out.println("FAIL: acknowledged deleteDocuments(id:1) (seqNo " + seqNo + ") is not in the commit");
          failures++;
        }
      }
    } else {
      System.out.println("writer closed itself (tragic)");
    }
    System.exit(failures == 0 ? 0 : 1);
  }
}
