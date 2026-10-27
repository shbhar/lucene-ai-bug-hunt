// Release sweep probe (no test-framework dependency): exits 1 if removeAllAttributes() leaves the
// cached state chain behind (captureState() non-null after removing every attribute).
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.FlagsAttribute;
import org.apache.lucene.util.AttributeSource;

public class RemoveAllAttributesProbe {
  public static void main(String[] args) {
    AttributeSource src = new AttributeSource();
    src.addAttribute(CharTermAttribute.class).append("foo");
    src.addAttribute(FlagsAttribute.class).setFlags(5);
    src.clearAttributes();
    src.removeAllAttributes();
    AttributeSource.State st = src.captureState();
    String refl = src.reflectAsString(false);
    System.out.println("hasAttributes=" + src.hasAttributes() + " captureState=" + st + " reflect='" + refl + "' hash=" + src.hashCode());
    if (st != null || !refl.isEmpty()) {
      System.out.println("BUG: removed attributes still visible through the cached state");
      System.exit(1);
    }
    System.out.println("OK");
  }
}
