package org.apache.lucene.index;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestChangesCheckIndexEntryIssue extends LuceneTestCase {

  public void testCheckIndexEntryCitesIssue7820() throws Exception {
    // -Dchanges.txt=<lucene/CHANGES.txt>; expected GITHUB#7820 (the issue CheckIndex.java links); actual in 10.6.0 RC1: GITHUB#17820 (404).
    String entry =
        Files.readAllLines(Path.of(System.getProperty("changes.txt"))).stream()
            .filter(line -> line.contains("CheckIndex now reports which segment's .si file could not be read"))
            .findFirst()
            .orElseThrow();
    assertEquals("GITHUB#7820", entry.substring(2, entry.indexOf(':')));
  }
}
