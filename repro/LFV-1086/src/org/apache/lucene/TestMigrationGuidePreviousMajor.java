package org.apache.lucene;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMigrationGuidePreviousMajor extends LuceneTestCase {

  // Expected "from Lucene N-1.x"; actual: main "Lucene 12; ... from Lucene 2.x", 10.6.0 "Lucene 10; ... from Lucene 0.x".
  public void testPreviousMajor() throws Exception {
    String index = Files.readString(Path.of(System.getProperty("docs.dir"), "index.html"));
    Matcher m = Pattern.compile("Lucene (\\d+); how to migrate code from\\s+Lucene (\\d+)\\.x").matcher(index);
    assertTrue(m.find());
    assertEquals(Integer.parseInt(m.group(1)) - 1, Integer.parseInt(m.group(2)));
  }
}
