package probe;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterCodecReader;
import org.apache.lucene.index.FilterMergePolicy;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;

/**
 * Plain main() (public API only, no test framework, the default codec, TieredMergePolicy as the
 * delegate): the V3 forceMerge scenario of this bug, to see which variant the default codec gives.
 *
 * <p>The delegate policy (used for the forceMerge) is TieredMergePolicy, the default, or
 * LogDocMergePolicy with -Dprobe.delegate=logdoc.
 *
 * <p>Source index (soft-deletes field "soft"): a (soft-deleted by a soft update, marked expired),
 * c, a2. addIndexes(CodecReader...) with a merge policy whose findMerges(CodecReader...) returns a
 * OneMerge whose wrapForMerge drops the expired document. Then one more document z, commit, and an
 * ordinary forceMerge(1) (no wrapper on that merge). Expected: 3 documents c, a2, z with distinct
 * doc IDs, each stored id equal to its term. Exit 1 if that is not so.
 */
public class DefaultCodecForceMergeAfterAddIndexes {

  static Document doc(String id, boolean expired) {
    Document d = new Document();
    d.add(new StringField("id", id, Field.Store.YES));
    if (expired) d.add(new StringField("expired", "true", Field.Store.NO));
    return d;
  }

  static CodecReader dropExpired(CodecReader reader) throws IOException {
    Bits liveDocs = reader.getLiveDocs();
    FixedBitSet newLive = new FixedBitSet(reader.maxDoc());
    for (int d = 0; d < reader.maxDoc(); d++) {
      if (liveDocs == null || liveDocs.get(d)) newLive.set(d);
    }
    PostingsEnum pe = reader.postings(new Term("expired", "true"));
    if (pe != null) {
      for (int d = pe.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = pe.nextDoc()) {
        newLive.clear(d);
      }
    }
    final int numDocs = newLive.cardinality();
    return new FilterCodecReader(reader) {
      @Override
      public Bits getLiveDocs() {
        return newLive;
      }

      @Override
      public int numDocs() {
        return numDocs;
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

  public static void main(String[] args) throws Exception {
    System.out.println("PROBE default codec: " + Codec.getDefault().getName());
    // -Dprobe.delegate=logdoc uses LogDocMergePolicy (as in the V3 probe); default: TieredMergePolicy
    boolean logDoc = "logdoc".equals(System.getProperty("probe.delegate"));
    MergePolicy delegate = logDoc ? new LogDocMergePolicy() : new TieredMergePolicy();
    System.out.println("PROBE delegate merge policy: " + delegate.getClass().getSimpleName());
    boolean ok = true;
    try (Directory src = new ByteBuffersDirectory();
        Directory dst = new ByteBuffersDirectory()) {
      IndexWriterConfig c1 = new IndexWriterConfig(new StandardAnalyzer());
      c1.setMergePolicy(NoMergePolicy.INSTANCE).setSoftDeletesField("soft");
      try (IndexWriter w = new IndexWriter(src, c1)) {
        w.addDocument(doc("a", true));
        w.addDocument(doc("c", false));
        w.softUpdateDocument(new Term("id", "a"), doc("a2", false), new NumericDocValuesField("soft", 1));
      }
      IndexWriterConfig c2 = new IndexWriterConfig(new StandardAnalyzer());
      c2.setMergeScheduler(new SerialMergeScheduler()).setSoftDeletesField("soft");
      c2.setMergePolicy(
          new FilterMergePolicy(delegate) {
            @Override
            public MergeSpecification findMerges(CodecReader... readers) {
              MergeSpecification spec = new MergeSpecification();
              spec.add(
                  new MergePolicy.OneMerge(readers) {
                    @Override
                    public CodecReader wrapForMerge(CodecReader reader) throws IOException {
                      return dropExpired(reader);
                    }
                  });
              return spec;
            }
          });
      IndexWriter w = new IndexWriter(dst, c2);
      try (DirectoryReader r = DirectoryReader.open(src)) {
        CodecReader[] readers = new CodecReader[r.leaves().size()];
        for (int i = 0; i < readers.length; i++) {
          readers[i] = (CodecReader) r.leaves().get(i).reader();
        }
        w.addIndexes(readers);
      }
      w.commit();
      for (SegmentCommitInfo sci : SegmentInfos.readLatestCommit(dst)) {
        System.out.println("PROBE after addIndexes: " + sci + " softDel=" + sci.getSoftDelCount());
      }
      w.addDocument(doc("z", false));
      w.commit();
      try {
        w.forceMerge(1);
        w.commit();
      } catch (Throwable t) {
        System.out.println("PROBE forceMerge threw " + t);
        ok = false;
      }
      for (SegmentCommitInfo sci : SegmentInfos.readLatestCommit(dst)) {
        System.out.println("PROBE after forceMerge: " + sci + " softDel=" + sci.getSoftDelCount());
      }
      try {
        w.close();
      } catch (Throwable t) {
        System.out.println("PROBE close threw " + t);
        ok = false;
      }
      try (DirectoryReader dr = DirectoryReader.open(dst)) {
        IndexSearcher s = new IndexSearcher(dr);
        System.out.println("PROBE reopened numDocs=" + dr.numDocs() + " maxDoc=" + dr.maxDoc());
        if (dr.maxDoc() != 3) ok = false;
        for (String id : new String[] {"c", "a2", "z"}) {
          TopDocs td = s.search(new TermQuery(new Term("id", id)), 10);
          StringBuilder sb = new StringBuilder();
          for (ScoreDoc sd : td.scoreDocs) {
            String stored;
            try {
              stored = s.storedFields().document(sd.doc).get("id");
            } catch (Throwable t) {
              stored = "EXC:" + t.getClass().getSimpleName();
            }
            if (!id.equals(stored)) ok = false;
            sb.append(" doc").append(sd.doc).append("->").append(stored);
          }
          if (td.totalHits.value() != 1) ok = false;
          System.out.println("PROBE id:" + id + " hits=" + td.totalHits.value() + sb);
        }
      }
      for (int level :
          new int[] {
            CheckIndex.Level.MIN_LEVEL_FOR_CHECKSUM_CHECKS,
            CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS,
            CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS
          }) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        CheckIndex.Status st;
        try (CheckIndex ci = new CheckIndex(dst)) {
          ci.setInfoStream(new PrintStream(bos, true, StandardCharsets.UTF_8));
          ci.setLevel(level);
          st = ci.checkIndex();
        }
        String firstError = "";
        for (String line : bos.toString(StandardCharsets.UTF_8).split("\n")) {
          if (firstError.isEmpty() && (line.contains("ERROR") || line.contains("FAILED"))) {
            firstError = " first error: " + line.trim();
          }
        }
        System.out.println("PROBE checkindex level=" + level + " clean=" + st.clean + firstError);
      }
    }
    System.out.println("PROBE result " + (ok ? "OK" : "CORRUPT"));
    System.exit(ok ? 0 : 1);
  }
}
