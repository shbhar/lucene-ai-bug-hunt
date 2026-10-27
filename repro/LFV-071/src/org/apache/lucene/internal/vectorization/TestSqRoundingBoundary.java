package org.apache.lucene.internal.vectorization;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.Constants;
import org.apache.lucene.util.quantization.ScalarQuantizer;

/**
 * Independent verification of the claim that the Panama minMaxScalarQuantize rounds
 * differently from DefaultVectorUtilSupport's Math.round(scale * dxc).
 *
 * <p>Approach (independent of the original repro): for several quantizer configurations (bits
 * 4/7/8, several quantile pairs, none of them the original witness) scan float neighbours of the
 * value whose scaled offset is 0.5, find inputs where Math.round(scale*dxc) differs from
 * (int)fma(dxc,scale,0.5f) (or the unfused variant), then feed those inputs through (a) the public
 * ScalarQuantizer API and (b) the two providers directly. Also checks the claim that this is the
 * ONLY kind of divergence (k=0 boundary) by an exhaustive scan near every k+0.5 boundary.
 */
public class TestSqRoundingBoundary extends LuceneTestCase {

  /** A quantizer configuration: quantiles plus bits, mirroring ScalarQuantizer's constructor. */
  record Cfg(float min, float max, int bits) {
    float scale() {
      return ((float) ((1 << bits) - 1)) / (max - min);
    }

    float alpha() {
      return (max - min) / ((float) ((1 << bits) - 1));
    }
  }

  static final Cfg[] CFGS = {
    new Cfg(-0.7321f, 0.8113f, 8),
    new Cfg(-0.7321f, 0.8113f, 7),
    new Cfg(-1f, 1f, 7),
    new Cfg(-0.25f, 3.5f, 8),
    new Cfg(0.013f, 0.987f, 4),
    new Cfg(-12.5f, 9.75f, 8),
  };

  static final boolean FMA = Constants.HAS_FAST_VECTOR_FMA;

  /** Deterministic sweep of quantizer configs (fixed java.util.Random seed, not the test seed). */
  static Cfg[] sweep(int n) {
    java.util.Random r = new java.util.Random(20261022L);
    int[] bits = {4, 7, 8};
    Cfg[] out = new Cfg[n];
    for (int i = 0; i < n; i++) {
      float min = r.nextFloat(-2f, 1f);
      float max = r.nextFloat(min + 1e-3f, 2f);
      out[i] = new Cfg(min, max, bits[i % 3]);
    }
    return out;
  }

  static final Cfg[] SWEEP = sweep(3000);

  /** What DefaultVectorUtilSupport.ScalarQuantizer.quantizeFloat computes for the byte. */
  static int defaultByte(Cfg c, float v) {
    float dxc = Math.max(c.min, Math.min(c.max, v)) - c.min;
    return Math.round(c.scale() * dxc);
  }

  /** What the Panama lane computes: fma(dxc, scale, 0.5f) (or mul+add) then F2I truncation. */
  static int panamaFormulaByte(Cfg c, float v) {
    float dxc = Math.max(c.min, Math.min(c.max, v)) - c.min;
    float s = c.scale();
    float r = FMA ? Math.fma(dxc, s, 0.5f) : dxc * s + 0.5f;
    return (int) r;
  }

  static float dxc(Cfg c, float v) {
    return Math.max(c.min, Math.min(c.max, v)) - c.min;
  }

  /** Scan +-n float neighbours of the point where scale*dxc == target; return divergent inputs. */
  static List<Float> scan(Cfg c, double target, int n) {
    List<Float> hits = new ArrayList<>();
    float v = (float) (c.min + target / c.scale());
    for (int i = 0; i < n; i++) v = Math.nextDown(v);
    for (int i = 0; i < 2 * n; i++, v = Math.nextUp(v)) {
      if (defaultByte(c, v) != panamaFormulaByte(c, v)) hits.add(v);
    }
    return hits;
  }

  static byte[] viaProvider(VectorUtilSupport p, Cfg c, float[] vec) {
    byte[] out = new byte[vec.length];
    p.minMaxScalarQuantize(vec, out, c.scale(), c.alpha(), c.min, c.max);
    return out;
  }

  public void testFindWitnessesAndCompareProviders() {
    VectorUtilSupport def = new DefaultVectorizationProvider().getVectorUtilSupport();
    VectorUtilSupport vec = VectorizationProvider.lookup(true).getVectorUtilSupport();
    System.out.println(
        "provider="
            + vec.getClass().getSimpleName()
            + " preferredBits="
            + PanamaVectorConstantsProbe.bits()
            + " HAS_FAST_VECTOR_FMA="
            + FMA);
    int totalHits = 0;
    int providerMismatches = 0;
    int modelMismatches = 0;
    int cfgsWithHit = 0, printed = 0;
    Cfg[] all = new Cfg[CFGS.length + SWEEP.length];
    System.arraycopy(CFGS, 0, all, 0, CFGS.length);
    System.arraycopy(SWEEP, 0, all, CFGS.length, SWEEP.length);
    for (Cfg c : all) {
      List<Float> hits = scan(c, 0.5, 256);
      totalHits += hits.size();
      if (hits.isEmpty() == false) cfgsWithHit++;
      for (float v : hits) {
        float d = dxc(c, v);
        BigDecimal exact = new BigDecimal(d).multiply(new BigDecimal(c.scale()));
        float[] in = new float[64]; // multiple of every lane count -> whole vector loop
        Arrays.fill(in, v);
        byte[] a = viaProvider(def, c, in);
        byte[] b = viaProvider(vec, c, in);
        if (a[0] != b[0]) providerMismatches++;
        if (b[0] != (byte) panamaFormulaByte(c, v)) modelMismatches++;
        if (printed++ < 12) System.out.printf(
            "cfg=%s v=%s (0x%08x) dxc=0x%08x fl(scale*dxc)=%s exact=%s default=%d panama=%d%n",
            c,
            v,
            Float.floatToRawIntBits(v),
            Float.floatToRawIntBits(d),
            c.scale() * d,
            exact.toPlainString(),
            a[0],
            b[0]);
      }
    }
    System.out.println("configs scanned=" + all.length + " configs with >=1 divergent input=" + cfgsWithHit);
    System.out.println(
        "totalFormulaHits="
            + totalHits
            + " providerMismatches="
            + providerMismatches
            + " modelMismatches="
            + modelMismatches);
    assertTrue("expected the scan to find boundary inputs", totalHits > 0);
    // Self-check of my lane model; only meaningful when the >=256-bit vector loop actually runs.
    String bits = PanamaVectorConstantsProbe.bits();
    boolean vectorLoopActive =
        vec.getClass().getSimpleName().equals("PanamaVectorUtilSupport")
            && bits.matches("\\d+")
            && Integer.parseInt(bits) >= 256;
    System.out.println("vectorLoopActive=" + vectorLoopActive);
    if (vectorLoopActive) {
      assertEquals("Panama kernel must agree with the fma/F2I lane model", 0, modelMismatches);
    }
    // The claim under test: Default and vectorized providers should produce identical bytes.
    assertEquals("Default vs vectorized minMaxScalarQuantize byte mismatches", 0, providerMismatches);
  }

  /**
   * End-to-end, kernel-level: for selected configs, quantize EVERY input v = u*(max-min)+min with u
   * on the 2^-24 lattice (the generator used by TestVectorUtilSupport), with both providers.
   * Checks that the real kernels diverge exactly where the lane formula predicts and nowhere else.
   */
  public void testExhaustiveLatticeKernelVsModel() {
    VectorUtilSupport def = new DefaultVectorizationProvider().getVectorUtilSupport();
    VectorUtilSupport vec = VectorizationProvider.lookup(true).getVectorUtilSupport();
    List<Cfg> cfgs = new ArrayList<>(Arrays.asList(CFGS));
    for (Cfg c : SWEEP) {
      if (cfgs.size() >= 40) break;
      if (scan(c, 0.5, 256).isEmpty() == false) cfgs.add(c);
    }
    final int chunk = 1 << 20;
    float[] in = new float[chunk];
    byte[] a = new byte[chunk], b = new byte[chunk];
    long elems = 0, provDiff = 0, predicted = 0, unpredictedDiff = 0, missedPrediction = 0;
    for (Cfg c : cfgs) {
      for (int base = 0; base < (1 << 24); base += chunk) {
        for (int j = 0; j < chunk; j++) {
          in[j] = ((base + j) * 0x1.0p-24f) * (c.max - c.min) + c.min;
        }
        def.minMaxScalarQuantize(in, a, c.scale(), c.alpha(), c.min, c.max);
        vec.minMaxScalarQuantize(in, b, c.scale(), c.alpha(), c.min, c.max);
        for (int j = 0; j < chunk; j++) {
          boolean diff = a[j] != b[j];
          boolean pred = defaultByte(c, in[j]) != panamaFormulaByte(c, in[j]);
          if (diff) provDiff++;
          if (pred) predicted++;
          if (diff && !pred) unpredictedDiff++;
          if (pred && !diff) missedPrediction++;
        }
        elems += chunk;
      }
    }
    System.out.println(
        "exhaustive lattice: configs="
            + cfgs.size()
            + " elements="
            + elems
            + " providerDiffs="
            + provDiff
            + " predictedByFormula="
            + predicted
            + " diffsNotPredicted="
            + unpredictedDiff
            + " predictedButNoDiff="
            + missedPrediction);
    assertEquals("provider differences outside the formula's prediction", 0, unpredictedDiff);
    assertEquals("Default vs vectorized byte differences over full lattice", 0, provDiff);
  }

  /** Public API path: ScalarQuantizer.quantize goes through VectorUtil -> provider singleton. */
  public void testPublicScalarQuantizerApi() {
    Cfg c = null;
    List<Float> hits = null;
    for (Cfg cand : SWEEP) {
      hits = scan(cand, 0.5, 256);
      if (hits.isEmpty() == false) {
        c = cand;
        break;
      }
    }
    assertNotNull("no witness found in sweep", c);
    System.out.println("public API cfg=" + c);
    float v = hits.get(0);
    ScalarQuantizer sq = new ScalarQuantizer(c.min, c.max, (byte) c.bits);
    float[] in = new float[32];
    Arrays.fill(in, v);
    byte[] dest = new byte[32];
    sq.quantize(in, dest, VectorSimilarityFunction.DOT_PRODUCT);
    int expected = defaultByte(c, v); // == Math.round(scale*dxc)
    System.out.println(
        "public API: v=" + v + " Math.round ref=" + expected + " got dest[0]=" + dest[0]);
    for (int i = 0; i < dest.length; i++) {
      assertEquals("dest[" + i + "]", (byte) expected, dest[i]);
    }
  }

  /**
   * Claim check: divergence only occurs at the k=0 (0.5) boundary, never at k+0.5 for k>=1. Scans
   * every boundary k+0.5 for k in [0, 2^bits-1) for each config, +-4000 floats.
   */
  public void testOnlyTheFirstBoundaryDiverges() {
    int k0 = 0, kOther = 0;
    for (Cfg c : Arrays.copyOf(SWEEP, 300)) {
      int maxK = (1 << c.bits) - 1;
      for (int k = 0; k < maxK; k++) {
        int h = scan(c, k + 0.5, 512).size();
        if (k == 0) k0 += h;
        else kOther += h;
      }
    }
    System.out.println("boundary scan: hits at k=0: " + k0 + ", hits at k>=1: " + kOther);
    assertEquals(0, kOther);
  }
}
