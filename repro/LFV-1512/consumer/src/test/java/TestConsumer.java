import org.apache.lucene.tests.util.LuceneTestCase;
import org.junit.Test;

public class TestConsumer extends LuceneTestCase {
  // expected: "mvn test" runs this test (Tests run: 1, as with 10.6.0); actual with 11.0.0-SNAPSHOT: Tests run: 0, BUILD SUCCESS
  @Test
  public void testIsRun() {}
}
