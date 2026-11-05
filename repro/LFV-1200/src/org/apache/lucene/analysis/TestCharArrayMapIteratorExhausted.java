package org.apache.lucene.analysis;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCharArrayMapIteratorExhausted extends LuceneTestCase {

  // Expected: next() past the end throws NoSuchElementException; actual: it returns a phantom entry.
  public void testNextAfterLastEntry() {
    CharArrayMap<Integer> map = new CharArrayMap<>(1, false);
    map.put("a", 1);
    Iterator<Map.Entry<Object, Integer>> it = map.entrySet().iterator();
    it.next();
    assertFalse(it.hasNext());
    expectThrows(NoSuchElementException.class, it::next);
  }
}
