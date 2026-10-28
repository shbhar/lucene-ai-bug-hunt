import java.text.ParseException;
import org.apache.lucene.expressions.js.JavascriptCompiler;

/** Release-compatible variant (compile(String) only): exits 1 on an undocumented exception. */
public class ShallowLongSumMain {
  static String balancedSum(int lo, int hi) {
    if (lo == hi) return "x";
    int mid = (lo + hi) >>> 1;
    return "(" + balancedSum(lo, mid) + "+" + balancedSum(mid + 1, hi) + ")";
  }

  public static void main(String[] args) {
    String source = balancedSum(0, 9_999);
    try {
      JavascriptCompiler.compile(source);
      System.out.println("RESULT compiled");
    } catch (ParseException | IllegalStateException documented) {
      System.out.println("RESULT documented " + documented.getClass().getName());
    } catch (RuntimeException e) {
      System.out.println("RESULT undocumented " + e.getClass().getName() + ": " + e.getMessage());
      System.exit(1);
    }
  }
}
