import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermFrequencyAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FieldStats;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermStats;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.AxiomaticF2EXP;
import org.apache.lucene.search.similarities.AxiomaticF2LOG;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.SmallFloat;

/**
 * An independent test (independent of the reporter's TestAxiomaticF2PruningRepro).
 *
 * <p>Uses the DEFAULT constructors AxiomaticF2EXP() (s=0.25, k=0.35) and AxiomaticF2LOG()
 * (s=0.25), NO query boost, and the default production codec. Statistics were found by the
 * verifier's own search tool (src/F2Search.java) and are produced by a real index here. The only
 * unusual inputs are huge per-document term frequencies (2^29) and field lengths (~1e9 tokens),
 * produced via the public TermFrequencyAttribute API with IndexOptions.DOCS_AND_FREQS.
 *
 * <p>fLo = 2^29 - 32 and fHi = 2^29 are adjacent float values (float freqs are what
 * SimScorer#score receives).
 */
public class TestVerifyAxiomaticF2Monotonicity extends LuceneTestCase {

  static final int F_HI = 1 << 29; // 536870912
  static final int F_LO = (int) Math.nextDown((float) F_HI); // 536870880

  /** One realizable corpus: docCount, docFreq of "t", norm byte of the crucial docs, sumTTF. */
  record Corpus(int numDocs, int docFreq, int normByte, long sumTotalTermFreq) {
    int crucialLen() {
      return SmallFloat.byte4ToInt((byte) normByte);
    }
  }

  // found by src/F2Search.java (EXP, s=0.25, k=0.35, boost=1, e=29, seed=7)
  static final Corpus EXP_CORPUS = new Corpus(611, 589, 248, 937_166_864_135L);
  // found by src/F2Search.java (LOG, s=0.25, boost=1, e=29, seed=26)
  static final Corpus LOG_CORPUS = new Corpus(644, 582, 244, 944_092_194_137L);

  // ---------------------------------------------------------------- scorer-level

  private static void checkScorer(Similarity sim, Corpus c) {
    FieldStats fs =
        new FieldStats("body", c.numDocs(), c.numDocs(), c.sumTotalTermFreq(), 2L * c.numDocs());
    TermStats ts = new TermStats(new BytesRef("t"), c.docFreq(), c.docFreq());
    Similarity.SimScorer scorer = sim.scorer(1f, fs, ts);
    float lo = scorer.score(F_LO, c.normByte());
    float hi = scorer.score(F_HI, c.normByte());
    System.out.println(
        sim
            + ": score(freq="
            + F_LO
            + ")="
            + lo
            + " [0x"
            + Integer.toHexString(Float.floatToIntBits(lo))
            + "]  score(freq="
            + F_HI
            + ")="
            + hi
            + " [0x"
            + Integer.toHexString(Float.floatToIntBits(hi))
            + "]");
    // Similarity.SimScorer#score javadoc: freq1 > freq2 ==> score(freq1, norm) >= score(freq2, norm)
    assertTrue(
        sim + ": score(" + F_HI + ")=" + hi + " < score(" + F_LO + ")=" + lo, hi >= lo);
  }

  public void testF2EXPDefaultScorerMonotonicInFreq() {
    checkScorer(new AxiomaticF2EXP(), EXP_CORPUS);
  }

  public void testF2LOGDefaultScorerMonotonicInFreq() {
    checkScorer(new AxiomaticF2LOG(), LOG_CORPUS);
  }

  // ---------------------------------------------------------------- index-level

  /** Emits ("t", tf) if tf > 0 and ("pad", len - tf) if len - tf > 0, as custom term freqs. */
  static final class TwoTermStream extends TokenStream {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final TermFrequencyAttribute freqAtt = addAttribute(TermFrequencyAttribute.class);
    private final int tf, len;
    private int state;

    TwoTermStream(int tf, int len) {
      this.tf = tf;
      this.len = len;
    }

    @Override
    public boolean incrementToken() {
      clearAttributes();
      while (state < 2) {
        int which = state++;
        int f = which == 0 ? tf : len - tf;
        if (f > 0) {
          termAtt.append(which == 0 ? "t" : "pad");
          freqAtt.setTermFrequency(f);
          return true;
        }
      }
      return false;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      state = 0;
    }
  }

  private static void checkPruning(Similarity sim, Corpus c) throws IOException {
    final int crucialLen = c.crucialLen();
    assertEquals(c.normByte(), Byte.toUnsignedInt(SmallFloat.intToByte4(crucialLen)));
    // doc 0: fHi (block 0 = docs 0..255); doc 300: fLo, the true top hit; doc 301: fHi
    // (block 1 = docs 256..511, Lucene104 BLOCK_SIZE = 256). All three have length crucialLen.
    final int[] crucial = {0, 300, 301};
    final long others = c.numDocs() - crucial.length;
    final long rest = c.sumTotalTermFreq() - (long) crucial.length * crucialLen;
    final long base = rest / others, rem = rest % others;
    assertTrue(base >= 1 && base + 1 <= Integer.MAX_VALUE);

    FieldType ft = new FieldType();
    ft.setTokenized(true);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
    ft.freeze();
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig((Analyzer) null);
      iwc.setCodec(TestUtil.getDefaultCodec()); // Lucene104Codec, not the randomized test codec
      iwc.setSimilarity(sim);
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        int otherIdx = 0;
        for (int i = 0; i < c.numDocs(); i++) {
          int tf, len;
          if (i == 0 || i == 301) {
            tf = F_HI;
            len = crucialLen;
          } else if (i == 300) {
            tf = F_LO;
            len = crucialLen;
          } else {
            tf = i < c.docFreq() ? 1 : 0;
            len = (int) (base + (otherIdx++ < rem ? 1 : 0));
          }
          Document doc = new Document();
          doc.add(new Field("body", new TwoTermStream(tf, len), ft));
          w.addDocument(doc);
        }
        w.commit();
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        LeafReader leaf = r.leaves().get(0).reader();
        IndexSearcher searcher = new IndexSearcher(r);
        searcher.setSimilarity(sim);
        searcher.setQueryCache(null);
        // the index really has the intended statistics
        FieldStats cs = searcher.fieldStats("body");
        assertEquals(c.numDocs(), cs.docCount());
        assertEquals(c.sumTotalTermFreq(), cs.sumTotalTermFreq());
        assertEquals(c.docFreq(), r.docFreq(new Term("body", "t")));
        NumericDocValues norms = leaf.getNormValues("body");
        for (int d : crucial) {
          assertEquals(d, norms.advance(d));
          assertEquals(c.normByte(), Byte.toUnsignedInt((byte) norms.longValue()));
        }

        Query q = new TermQuery(new Term("body", "t")); // no boost
        float s0 = searcher.explain(q, 0).getValue().floatValue();
        float s300 = searcher.explain(q, 300).getValue().floatValue();
        float s301 = searcher.explain(q, 301).getValue().floatValue();
        System.out.println(
            sim + ": explain doc0=" + s0 + " doc300=" + s300 + " doc301=" + s301);

        Weight weight = searcher.createWeight(searcher.rewrite(q), ScoreMode.TOP_SCORES, 1f);
        Scorer scorer = weight.scorer(r.leaves().get(0));
        int upTo = scorer.advanceShallow(256);
        float blockMax = scorer.getMaxScore(upTo);
        System.out.println(
            sim + ": advanceShallow(256)=" + upTo + " getMaxScore(" + upTo + ")=" + blockMax);

        TopDocs exhaustive =
            searcher.search(q, new TopScoreDocCollectorManager(1, Integer.MAX_VALUE));
        TopDocs pruned = searcher.search(q, new TopScoreDocCollectorManager(1, 1));
        System.out.println(
            sim
                + ": exhaustive top1 doc="
                + exhaustive.scoreDocs[0].doc
                + " score="
                + exhaustive.scoreDocs[0].score
                + " | pruned top1 doc="
                + pruned.scoreDocs[0].doc
                + " score="
                + pruned.scoreDocs[0].score);
        // Scorer#getMaxScore(upTo): upper bound of the scores of all docs up to upTo (inclusive)
        boolean boundOk = !(upTo >= 300 && blockMax < s300);
        boolean topOk = exhaustive.scoreDocs[0].doc == pruned.scoreDocs[0].doc;
        assertTrue(
            sim
                + ": getMaxScore("
                + upTo
                + ")="
                + blockMax
                + " < score(doc300)="
                + s300
                + "; exhaustive top1="
                + exhaustive.scoreDocs[0].doc
                + " pruned top1="
                + pruned.scoreDocs[0].doc,
            boundOk && topOk);
      }
    }
  }

  public void testF2EXPDefaultPruningMatchesExhaustive() throws IOException {
    checkPruning(new AxiomaticF2EXP(), EXP_CORPUS);
  }

  public void testF2LOGDefaultPruningMatchesExhaustive() throws IOException {
    checkPruning(new AxiomaticF2LOG(), LOG_CORPUS);
  }
}
