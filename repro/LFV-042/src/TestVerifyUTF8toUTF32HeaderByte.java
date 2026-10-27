import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRefBuilder;
import org.apache.lucene.util.UnicodeUtil;
import org.apache.lucene.util.automaton.Automata;

/**
 * Independent verification of the finding: UnicodeUtil.UTF8toUTF32 documents
 * "@throws IllegalArgumentException If invalid codepoint header byte occurs", but for lead bytes
 * 0xF8..0xFF it is claimed to throw ArrayIndexOutOfBoundsException.
 *
 * <p>Tests named testContract* encode the documented contract and are EXPECTED TO FAIL on main if
 * the candidate is real. Tests named testCharacterize* / testControl* / testReach* pin down the
 * observed behaviour and are expected to PASS.
 */
public class TestVerifyUTF8toUTF32HeaderByte extends LuceneTestCase {

  private static BytesRef ref(int... bytes) {
    byte[] b = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++) b[i] = (byte) bytes[i];
    return new BytesRef(b);
  }

  /** Lead byte b followed by exactly the number of continuation bytes its class needs. */
  private static BytesRef wellSized(int b) {
    int len;
    if (b < 0xC0) len = 1;
    else if (b < 0xE0) len = 2;
    else if (b < 0xF0) len = 3;
    else len = 4; // 0xF0..0xF7 need 4; 0xF8..0xFF padded to 4 so nothing is "truncated"
    int[] v = new int[len];
    v[0] = b;
    for (int i = 1; i < len; i++) v[i] = 0x80;
    return ref(v);
  }

  private static Throwable thrownBy(Runnable r) {
    try {
      r.run();
      return null;
    } catch (Throwable t) {
      return t;
    }
  }

  // ---------------------------------------------------------------- documented contract (should FAIL)

  /** Javadoc of UTF8toUTF32: "@throws IllegalArgumentException If invalid codepoint header byte". */
  public void testContract_UTF8toUTF32_leadF8_throwsIAE() {
    expectThrows(
        IllegalArgumentException.class, () -> UnicodeUtil.UTF8toUTF32(ref(0xF8), new int[8]));
  }

  public void testContract_UTF8toUTF32_leadFF_inMiddle_throwsIAE() {
    expectThrows(
        IllegalArgumentException.class,
        () -> UnicodeUtil.UTF8toUTF32(ref('a', 0xFF, 'b'), new int[8]));
  }

  // ---------------------------------------------------------------- characterization (should PASS)

  /** Exhaustive over all 256 lead bytes, each given a full-length tail so truncation is excluded. */
  public void testCharacterize_allLeadBytes() {
    StringBuilder table = new StringBuilder();
    for (int b = 0; b <= 0xFF; b++) {
      final BytesRef r = wellSized(b);
      final int[] out = new int[8];
      Throwable cnt = thrownBy(() -> UnicodeUtil.codePointCount(r));
      Throwable u32 = thrownBy(() -> UnicodeUtil.UTF8toUTF32(r, out));
      table.append(String.format("0x%02X len=%d codePointCount=%s UTF8toUTF32=%s%n", b, r.length,
          cnt == null ? "ok" : cnt.getClass().getSimpleName(),
          u32 == null ? "ok" : u32.getClass().getSimpleName() + "(" + u32.getMessage() + ")"));

      if (b < 0x80) {
        assertNull(cnt);
        assertNull(u32);
        assertEquals(b, out[0]);
      } else if (b < 0xC0) {
        // continuation bytes as lead: documented IAE branch IS reached (control)
        assertTrue("0x" + Integer.toHexString(b), cnt instanceof IllegalArgumentException);
        assertTrue("0x" + Integer.toHexString(b), u32 instanceof IllegalArgumentException);
        assertTrue(u32.getMessage().startsWith("Invalid UTF8 header byte: 0x"));
      } else if (b < 0xF8) {
        assertNull(cnt);
        assertNull(u32);
      } else {
        // the candidate: 0xF8..0xFF
        assertTrue("0x" + Integer.toHexString(b), cnt instanceof IllegalArgumentException);
        assertSame(ArrayIndexOutOfBoundsException.class, u32.getClass());
        // Message is null when HotSpot's OmitStackTraceInFastThrow kicks in (hot throw site);
        // run with -XX:-OmitStackTraceInFastThrow to always see it.
        if (u32.getMessage() != null) {
          assertEquals("Index " + b + " out of bounds for length 248", u32.getMessage());
        }
      }
    }
    System.out.println("=== lead-byte table (main) ===\n" + table);
  }

  /** The lookup table has 248 entries (0x00..0xF7), so indices 0xF8..0xFF are out of bounds. */
  public void testCharacterize_tableLength() throws Exception {
    Field f = UnicodeUtil.class.getDeclaredField("utf8CodeLength");
    f.setAccessible(true);
    int[] table = (int[]) f.get(null);
    System.out.println("utf8CodeLength.length = " + table.length);
    assertEquals(248, table.length);
  }

  /** codePointAt is the shared decoder; its javadoc says invalid input is "undefined behavior". */
  public void testCharacterize_codePointAt() {
    Throwable t = thrownBy(() -> UnicodeUtil.codePointAt(new byte[] {(byte) 0xFE}, 0, null));
    assertSame(ArrayIndexOutOfBoundsException.class, t.getClass());
    Throwable c = thrownBy(() -> UnicodeUtil.codePointAt(new byte[] {(byte) 0x80}, 0, null));
    assertSame(IllegalArgumentException.class, c.getClass());
  }

  // ---------------------------------------------------------------- reachability through public APIs

  /** IntsRefBuilder.copyUTF8Bytes delegates to UTF8toUTF32 (IntsRefBuilder.java:101-104). */
  public void testReach_IntsRefBuilder_copyUTF8Bytes() {
    IntsRefBuilder b = new IntsRefBuilder();
    Throwable bad = thrownBy(() -> b.copyUTF8Bytes(ref('x', 0xFE)));
    Throwable ctl = thrownBy(() -> b.copyUTF8Bytes(ref('x', 0x80)));
    System.out.println("IntsRefBuilder.copyUTF8Bytes: 0xFE -> " + bad + " ; 0x80 -> " + ctl);
    assertSame(ArrayIndexOutOfBoundsException.class, bad.getClass());
    assertSame(IllegalArgumentException.class, ctl.getClass());
  }

  /** Automata.makeStringUnion decodes with codePointAt (StringsToAutomaton.java:295,315). */
  public void testReach_Automata_makeStringUnion() {
    Throwable bad = thrownBy(() -> Automata.makeStringUnion(List.of(ref(0xF8))));
    Throwable ctl = thrownBy(() -> Automata.makeStringUnion(List.of(ref(0x80))));
    Throwable bin = thrownBy(() -> Automata.makeBinaryStringUnion(List.of(ref(0xF8))));
    System.out.println(
        "makeStringUnion: 0xF8 -> " + bad + " ; 0x80 -> " + ctl + " ; binary(0xF8) -> " + bin);
    assertSame(ArrayIndexOutOfBoundsException.class, bad.getClass());
    assertSame(IllegalArgumentException.class, ctl.getClass());
    assertNull(bin); // binary union never decodes UTF-8
  }

  // ---------------------------------------------------------------- control: valid input is fine

  public void testControl_validRoundTrip() {
    for (int iter = 0; iter < 2000; iter++) {
      String s = TestUtil.randomRealisticUnicodeString(random(), 0, 20);
      BytesRef r = new BytesRef(s.getBytes(StandardCharsets.UTF_8));
      int[] out = new int[r.length];
      int n = UnicodeUtil.UTF8toUTF32(r, out);
      int[] expected = s.codePoints().toArray();
      assertEquals(expected.length, n);
      for (int i = 0; i < n; i++) assertEquals(expected[i], out[i]);
      assertEquals(expected.length, UnicodeUtil.codePointCount(r));
    }
  }
}
