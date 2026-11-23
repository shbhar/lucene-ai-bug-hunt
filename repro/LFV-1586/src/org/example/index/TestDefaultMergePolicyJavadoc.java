package org.example.index;

import static org.junit.Assert.assertEquals;

import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogByteSizeMergePolicy;
import org.junit.Test;

public class TestDefaultMergePolicyJavadoc {

  // Class javadoc of IndexWriter: "The default is LogByteSizeMergePolicy"; actual: TieredMergePolicy.
  @Test
  public void testDefaultMergePolicy() {
    assertEquals(
        LogByteSizeMergePolicy.class, new IndexWriterConfig().getMergePolicy().getClass());
  }
}
