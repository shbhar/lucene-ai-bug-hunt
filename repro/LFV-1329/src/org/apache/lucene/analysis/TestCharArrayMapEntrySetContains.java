package org.apache.lucene.analysis;

import java.util.AbstractMap.SimpleEntry;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCharArrayMapEntrySetContains extends LuceneTestCase {

  public void testEmptyMapEntrySetContainsNoNullValuedEntry() {
    // expected false (the map is empty), actual true
    assertFalse(new CharArrayMap<>(0, false).entrySet().contains(new SimpleEntry<>("missing", null)));
  }
}
