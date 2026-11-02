package org.apache.lucene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Test;

public class TestFrontPageJavadocLinks {
  // Expected: every "API Javadocs" link in index.html exists in -Ddocs.dir; actual: benchmark-jmh/index.html is missing.
  @Test
  public void testListedModuleJavadocsExist() throws Exception {
    Path docs = Path.of(System.getProperty("docs.dir"));
    String index = Files.readString(docs.resolve("index.html"));
    List<String> links =
        Pattern.compile("<strong><a href=\"([^\"]+)\">").matcher(index).results().map(m -> m.group(1)).toList();
    assertTrue(links.size() > 0);
    assertEquals(List.of(), links.stream().filter(l -> Files.isRegularFile(docs.resolve(l)) == false).toList());
  }
}
