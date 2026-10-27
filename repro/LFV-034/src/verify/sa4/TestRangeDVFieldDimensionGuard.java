package verify.sa4;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import org.apache.lucene.document.DoubleRange;
import org.apache.lucene.document.DoubleRangeDocValuesField;
import org.apache.lucene.document.FloatRangeDocValuesField;
import org.apache.lucene.document.IntRangeDocValuesField;
import org.apache.lucene.document.LongRangeDocValuesField;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check of the finding: the explicit guard in
 * {Double,Float,Int,Long}RangeDocValuesField.getMin/getMax is
 * "if (dimension > 4 || dimension > min.length) throw IAE", so dimension == numDims and
 * dimension < 0 are not caught by it and fail at the raw array access instead.
 *
 * <p>Deterministic: no randomness is used; every (type, accessor, numDims, dimension) cell is
 * enumerated.
 */
@LuceneTestCase.SuppressSysoutChecks(bugUrl = "prints the full outcome grid on purpose")
public class TestRangeDVFieldDimensionGuard extends LuceneTestCase {

  /** One accessor (getMin or getMax) of one field instance. */
  private record Accessor(String name, int numDims, IntFunction<Number> fn) {}

  private static List<Accessor> allAccessors() {
    List<Accessor> out = new ArrayList<>();
    for (int n = 1; n <= 4; n++) {
      double[] dmin = new double[n], dmax = new double[n];
      float[] fmin = new float[n], fmax = new float[n];
      int[] imin = new int[n], imax = new int[n];
      long[] lmin = new long[n], lmax = new long[n];
      for (int i = 0; i < n; i++) {
        dmin[i] = 10 * i;
        dmax[i] = 10 * i + 5;
        fmin[i] = 10 * i;
        fmax[i] = 10 * i + 5;
        imin[i] = 10 * i;
        imax[i] = 10 * i + 5;
        lmin[i] = 10 * i;
        lmax[i] = 10 * i + 5;
      }
      DoubleRangeDocValuesField d = new DoubleRangeDocValuesField("d", dmin, dmax);
      FloatRangeDocValuesField f = new FloatRangeDocValuesField("f", fmin, fmax);
      IntRangeDocValuesField i = new IntRangeDocValuesField("i", imin, imax);
      LongRangeDocValuesField l = new LongRangeDocValuesField("l", lmin, lmax);
      out.add(new Accessor("DoubleRangeDocValuesField.getMin", n, d::getMin));
      out.add(new Accessor("DoubleRangeDocValuesField.getMax", n, d::getMax));
      out.add(new Accessor("FloatRangeDocValuesField.getMin", n, f::getMin));
      out.add(new Accessor("FloatRangeDocValuesField.getMax", n, f::getMax));
      out.add(new Accessor("IntRangeDocValuesField.getMin", n, i::getMin));
      out.add(new Accessor("IntRangeDocValuesField.getMax", n, i::getMax));
      out.add(new Accessor("LongRangeDocValuesField.getMin", n, l::getMin));
      out.add(new Accessor("LongRangeDocValuesField.getMax", n, l::getMax));
    }
    return out;
  }

  private static String outcome(Accessor a, int dim) {
    try {
      return "value " + a.fn().apply(dim);
    } catch (Throwable t) {
      return t.getClass().getSimpleName() + "(" + t.getMessage() + ")";
    }
  }

  /** In-range dimensions must return the stored value (no wrong results). */
  public void testInRangeValuesAreCorrect() {
    for (Accessor a : allAccessors()) {
      for (int dim = 0; dim < a.numDims(); dim++) {
        double expected = a.name().endsWith("getMin") ? 10 * dim : 10 * dim + 5;
        assertEquals(a.name() + " n=" + a.numDims() + " dim=" + dim,
            expected, a.fn().apply(dim).doubleValue(), 0.0);
      }
    }
  }

  /**
   * Full outcome grid for out-of-range dimensions. Prints every cell, then asserts the guard's
   * own intended contract (IllegalArgumentException "Dimension out of valid range") for every
   * out-of-range dimension, collecting all violations first so the output shows all of them.
   */
  public void testOutOfRangeDimensionsHitTheGuard() {
    List<String> violations = new ArrayList<>();
    for (Accessor a : allAccessors()) {
      int n = a.numDims();
      java.util.TreeSet<Integer> probes =
          new java.util.TreeSet<>(List.of(-1, Integer.MIN_VALUE, n, n + 1, 4, 5, Integer.MAX_VALUE));
      StringBuilder row = new StringBuilder(a.name() + " numDims=" + n + ":");
      for (int dim : probes) {
        if (dim < n && dim >= 0) {
          continue;
        }
        String o = outcome(a, dim);
        row.append("  [").append(dim).append("]=").append(o);
        if (!o.startsWith("IllegalArgumentException(Dimension out of valid range)")) {
          violations.add(a.name() + " numDims=" + n + " dim=" + dim + " -> " + o);
        }
      }
      System.out.println(row);
    }
    System.out.println("violations=" + violations.size());
    for (String v : violations) {
      System.out.println("  VIOLATION " + v);
    }
    assertTrue("guard missed " + violations.size() + " out-of-range calls; first: "
        + (violations.isEmpty() ? "" : violations.get(0)), violations.isEmpty());
  }

  /** Minimal single-case form of the claim. */
  public void testMinimalOneDimGetMinOne() {
    DoubleRangeDocValuesField f =
        new DoubleRangeDocValuesField("f", new double[] {1}, new double[] {2});
    Throwable t = expectThrows(Throwable.class, () -> f.getMin(1));
    System.out.println("DoubleRangeDocValuesField(1 dim).getMin(1) threw " + t);
    assertTrue("expected IllegalArgumentException, got " + t, t instanceof IllegalArgumentException);
  }

  /**
   * Control: the sibling indexed field DoubleRange.getMin uses Objects.checkIndex, which throws
   * IndexOutOfBoundsException for both dim == numDims and dim < 0 (consistently). Recorded to
   * compare the DV field's behaviour against the closest public API with the same signature.
   */
  public void testControlSiblingPointFieldUsesCheckIndex() {
    DoubleRange r = new DoubleRange("r", new double[] {1}, new double[] {2});
    Throwable atN = expectThrows(Throwable.class, () -> r.getMin(1));
    Throwable neg = expectThrows(Throwable.class, () -> r.getMin(-1));
    System.out.println("CONTROL DoubleRange(1 dim).getMin(1) threw " + atN);
    System.out.println("CONTROL DoubleRange(1 dim).getMin(-1) threw " + neg);
    assertEquals(IndexOutOfBoundsException.class, atN.getClass());
    assertEquals(IndexOutOfBoundsException.class, neg.getClass());
  }
}
