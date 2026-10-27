import java.util.Comparator;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefArray;
import org.apache.lucene.util.BytesRefIterator;
import org.apache.lucene.util.Counter;

/** Exits 1 if BytesRefArray.iterator((Comparator) null) does not iterate in append order. */
public class BytesRefArrayNullComparatorMain {
  public static void main(String[] args) throws Exception {
    BytesRefArray array = new BytesRefArray(Counter.newCounter());
    for (String s : new String[] {"c", "a", "b"}) {
      array.append(new BytesRef(s));
    }
    StringBuilder got = new StringBuilder();
    try {
      BytesRefIterator it = array.iterator((Comparator<BytesRef>) null);
      for (BytesRef b = it.next(); b != null; b = it.next()) {
        got.append(b.utf8ToString());
      }
    } catch (RuntimeException e) {
      System.out.println("iterator(null) threw " + e);
      System.exit(1);
    }
    System.out.println("iterator(null) order: " + got);
    System.exit("cab".contentEquals(got) ? 0 : 1);
  }
}
