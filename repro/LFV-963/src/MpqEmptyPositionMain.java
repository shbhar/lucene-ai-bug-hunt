import org.apache.lucene.index.Term;
import org.apache.lucene.search.MultiPhraseQuery;

/** Core-only probe for older releases: exits 1 when either leg fails. */
public class MpqEmptyPositionMain {
  public static void main(String[] args) {
    int failures = 0;
    try {
      new MultiPhraseQuery.Builder().add(new Term[0]);
      System.out.println("first position: accepted");
    } catch (RuntimeException e) {
      System.out.println("first position: " + e);
      if (!(e instanceof IllegalArgumentException)) failures++;
    }
    MultiPhraseQuery q =
        new MultiPhraseQuery.Builder().add(new Term("body", "a")).add(new Term[0]).build();
    try {
      System.out.println("later position accepted; toString = " + q.toString());
    } catch (RuntimeException e) {
      System.out.println("later position accepted; toString threw " + e);
      failures++;
    }
    System.exit(failures == 0 ? 0 : 1);
  }
}
