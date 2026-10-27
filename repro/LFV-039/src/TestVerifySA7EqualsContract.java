import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.lucene.analysis.tokenattributes.FlagsAttributeImpl;
import org.apache.lucene.analysis.tokenattributes.KeywordAttributeImpl;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttributeImpl;
import org.apache.lucene.document.ShapeField;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.mutable.MutableValue;
import org.apache.lucene.util.mutable.MutableValueBool;
import org.apache.lucene.util.mutable.MutableValueDate;
import org.apache.lucene.util.mutable.MutableValueDouble;
import org.apache.lucene.util.mutable.MutableValueFloat;
import org.apache.lucene.util.mutable.MutableValueInt;
import org.apache.lucene.util.mutable.MutableValueLong;
import org.apache.lucene.util.mutable.MutableValueStr;

/**
 * Independent verification of the finding (equals(null) / equals(foreign type) throws).
 *
 * <p>Every "testBug*" method asserts the java.lang.Object#equals contract ("for any non-null x,
 * x.equals(null) should return false"; symmetry with a foreign type, which always returns false
 * from its own equals). They are expected to FAIL on main if the candidate is real. Every
 * "testControl*" method is expected to PASS and shows the harness itself is fine.
 *
 * <p>Deterministic: no randomness is used.
 */
public class TestVerifySA7EqualsContract extends LuceneTestCase {

  /** Calls a.equals(b); returns "false"/"true" or the simple name of the thrown exception. */
  private static String outcome(Object a, Object b) {
    try {
      return String.valueOf(a.equals(b));
    } catch (Throwable t) {
      return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
  }

  private static void assertContractFalse(String what, Object a, Object b) {
    String got = outcome(a, b);
    System.out.println("[SA7] " + what + " -> " + got);
    assertEquals(what, "false", got);
  }

  private static ShapeField.DecodedTriangle realTriangle() {
    byte[] bytes = new byte[7 * Integer.BYTES];
    // a real (non-degenerate) triangle, round-tripped through the public encode/decode API
    ShapeField.encodeTriangle(bytes, 0, 0, true, 0, 10, true, 10, 0, true);
    ShapeField.DecodedTriangle t = new ShapeField.DecodedTriangle();
    ShapeField.decodeTriangle(bytes, t);
    return t;
  }

  // ---------------------------------------------------------------- KeywordAttributeImpl

  public void testBugKeywordAttributeImplEqualsNull() {
    KeywordAttributeImpl kw = new KeywordAttributeImpl();
    kw.setKeyword(true);
    assertContractFalse("KeywordAttributeImpl.equals(null)", kw, null);
  }

  /** Realistic call site: java.util.Objects.equals(nonNull, null) delegates to a.equals(null). */
  public void testBugKeywordAttributeImplViaObjectsEquals() {
    KeywordAttributeImpl kw = new KeywordAttributeImpl();
    String got;
    try {
      got = String.valueOf(Objects.equals(kw, null));
    } catch (Throwable t) {
      got = t.getClass().getSimpleName();
    }
    System.out.println("[SA7] Objects.equals(KeywordAttributeImpl, null) -> " + got);
    assertEquals("false", got);
  }

  // ---------------------------------------------------------------- MutableValue (all subclasses)

  public void testBugMutableValueEqualsNullAllSubclasses() {
    MutableValue[] all = {
      new MutableValueBool(),
      new MutableValueDate(),
      new MutableValueDouble(),
      new MutableValueFloat(),
      new MutableValueInt(),
      new MutableValueLong(),
      new MutableValueStr()
    };
    StringBuilder failures = new StringBuilder();
    for (MutableValue mv : all) {
      String got = outcome(mv, null);
      System.out.println("[SA7] " + mv.getClass().getSimpleName() + ".equals(null) -> " + got);
      if (!"false".equals(got)) {
        failures.append(mv.getClass().getSimpleName()).append(' ');
      }
    }
    assertEquals("subclasses whose equals(null) does not return false", "", failures.toString());
  }

  /** Realistic call site: a List that contains a null element; contains() calls o.equals(null). */
  public void testBugMutableValueViaListContains() {
    MutableValueInt mv = new MutableValueInt();
    mv.value = 42;
    List<Object> list = Arrays.asList(null, "x");
    String got;
    try {
      got = String.valueOf(list.contains(mv));
    } catch (Throwable t) {
      got = t.getClass().getSimpleName();
    }
    System.out.println("[SA7] Arrays.asList(null, \"x\").contains(MutableValueInt) -> " + got);
    assertEquals("false", got);
  }

  // ---------------------------------------------------------------- ShapeField.DecodedTriangle

  public void testBugDecodedTriangleEqualsForeignType() {
    assertContractFalse("DecodedTriangle.equals(\"x\")", realTriangle(), "x");
  }

  public void testBugDecodedTriangleEqualsNull() {
    assertContractFalse("DecodedTriangle.equals(null)", realTriangle(), null);
  }

  /** Symmetry: "x".equals(t) is false, so t.equals("x") must be false too (not throw). */
  public void testBugDecodedTriangleViaImmutableListContains() {
    ShapeField.DecodedTriangle t = realTriangle();
    System.out.println("[SA7] \"x\".equals(triangle) -> " + "x".equals(t));
    String got;
    try {
      got = String.valueOf(List.of("x").contains(t));
    } catch (Throwable th) {
      got = th.getClass().getSimpleName();
    }
    System.out.println("[SA7] List.of(\"x\").contains(triangle) -> " + got);
    assertEquals("false", got);
  }

  // ---------------------------------------------------------------- controls (expected to pass)

  public void testControlSiblingAttributeImplsHandleNull() {
    assertContractFalse("FlagsAttributeImpl.equals(null)", new FlagsAttributeImpl(), null);
    assertContractFalse(
        "PositionIncrementAttributeImpl.equals(null)", new PositionIncrementAttributeImpl(), null);
    assertContractFalse("BytesRef.equals(null)", new BytesRef("a"), null);
  }

  public void testControlPositiveEqualityStillWorks() {
    KeywordAttributeImpl a = new KeywordAttributeImpl();
    KeywordAttributeImpl b = new KeywordAttributeImpl();
    a.setKeyword(true);
    b.setKeyword(true);
    assertTrue(a.equals(b));
    MutableValueInt m1 = new MutableValueInt();
    MutableValueInt m2 = new MutableValueInt();
    m1.value = m2.value = 7;
    assertTrue(m1.equals(m2));
    assertFalse(m1.equals(new MutableValueLong())); // different class, non-null: fine
    assertTrue(realTriangle().equals(realTriangle()));
  }
}
