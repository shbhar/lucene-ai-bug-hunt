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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.bloom.BloomFilteringPostingsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104Codec;
import org.apache.lucene.codecs.lucene104.Lucene104PostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 * End-to-end: index N documents with a unique "id" (one term per doc, the primary-key case the
 * bloom filter is for) through BloomFilteringPostingsFormat with the default factory, then
 * forceMerge(1), and report every segment's maxDoc and the size of its bloom (.blm) file. Exit 1
 * if a segment with maxDoc >= 226,279,579 carries a filter of under 1 KiB.
 *
 * <p>Usage: FarmScaleBloom DIR NUM_DOCS THREADS
 */
public class FarmScaleBloom {
  public static void main(String[] args) throws Exception {
    Path path = Path.of(args[0]);
    long numDocs = Long.parseLong(args[1]);
    int threads = Integer.parseInt(args[2]);
    PostingsFormat bloom = new BloomFilteringPostingsFormat(new Lucene104PostingsFormat());
    IndexWriterConfig iwc = new IndexWriterConfig(null);
    iwc.setCodec(
        new Lucene104Codec() {
          @Override
          public PostingsFormat getPostingsFormatForField(String field) {
            return bloom;
          }
        });
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(1000);
    iwc.setMergePolicy(mp);
    iwc.setUseCompoundFile(false);
    iwc.setRAMBufferSizeMB(1900);
    long t0 = System.nanoTime();
    try (Directory dir = FSDirectory.open(path);
        IndexWriter w = new IndexWriter(dir, iwc)) {
      AtomicLong next = new AtomicLong();
      Thread[] ts = new Thread[threads];
      for (int t = 0; t < threads; t++) {
        ts[t] =
            new Thread(
                () -> {
                  try {
                    Document d = new Document();
                    StringField f = new StringField("id", "", Field.Store.NO);
                    d.add(f);
                    long i;
                    while ((i = next.getAndIncrement()) < numDocs) {
                      f.setStringValue(String.format(Locale.ROOT, "%010d", i));
                      w.addDocument(d);
                    }
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                });
        ts[t].start();
      }
      for (Thread t : ts) t.join();
      w.commit();
      System.out.printf(Locale.ROOT, "indexed %d docs in %.1fs%n", numDocs, (System.nanoTime() - t0) / 1e9);
      report("before forceMerge", dir);
      w.forceMerge(1);
      w.commit();
      System.out.printf(Locale.ROOT, "forceMerge done at %.1fs%n", (System.nanoTime() - t0) / 1e9);
      boolean bad = report("after forceMerge(1)", dir);
      System.exit(bad ? 1 : 0);
    }
  }

  static boolean report(String when, Directory dir) throws Exception {
    boolean bad = false;
    System.out.println("== " + when);
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      for (LeafReaderContext ctx : r.leaves()) {
        SegmentReader sr = (SegmentReader) ctx.reader();
        String seg = sr.getSegmentName();
        long blm = 0;
        for (String file : sr.getSegmentInfo().files()) {
          if (file.endsWith(".blm")) blm += dir.fileLength(file);
        }
        System.out.printf(Locale.ROOT, "segment %s maxDoc=%d bloomFileBytes=%d%n", seg, sr.maxDoc(), blm);
        if (sr.maxDoc() >= 226_279_579 && blm < 1024) bad = true;
      }
    }
    return bad;
  }
}
