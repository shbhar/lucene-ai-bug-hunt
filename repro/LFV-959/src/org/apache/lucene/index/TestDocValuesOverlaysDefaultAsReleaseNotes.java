/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.lucene.index;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The only release note for incremental doc-values updates is the Lucene 10.6.0 entry of
 * lucene/CHANGES.txt (New Features):
 *
 * <pre>
 * GITHUB#16418: Incremental doc-values updates. ... Opt-in; enable with
 *   IndexWriterConfig#setMaxDocValuesOverlays(n).
 * </pre>
 *
 * No later CHANGES section and no MIGRATE.md entry says that the default changed. This test checks
 * the documented default on the jars it runs on: a default IndexWriterConfig keeps the feature off, and
 * a doc-values update with the default config writes the same files as with the feature explicitly
 * disabled (setMaxDocValuesOverlays(0), the classic full-column rewrite).
 */
public class TestDocValuesOverlaysDefaultAsReleaseNotes extends LuceneTestCase {

  private static final String NOTE =
      "CHANGES.txt (GITHUB#16418) says incremental doc-values updates are 'Opt-in; enable with"
          + " IndexWriterConfig#setMaxDocValuesOverlays(n)', and no later CHANGES or MIGRATE entry"
          + " records a default change";

  public void testDefaultIsOff() {
    assertEquals(NOTE, 0, new IndexWriterConfig().getMaxDocValuesOverlays());
  }

  /** Bytes of the doc-values update files after updating one document of a 1000-doc segment. */
  private long updateFileBytes(IndexWriterConfig iwc) throws Exception {
    try (Directory dir = newDirectory()) {
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 1000; i++) {
          Document d = new Document();
          d.add(new StringField("id", Integer.toString(i), Field.Store.NO));
          d.add(new NumericDocValuesField("n", 1_000_000L + 7L * i));
          w.addDocument(d);
        }
        w.commit();
        w.updateNumericDocValue(new Term("id", "17"), "n", 42L);
        w.commit();
      }
      SegmentInfos infos = SegmentInfos.readLatestCommit(dir);
      assertEquals(1, infos.size());
      long bytes = 0;
      for (String f : infos.info(0).getDocValuesUpdatesFiles().values().stream()
          .flatMap(java.util.Set::stream).toList()) {
        bytes += dir.fileLength(f);
      }
      assertTrue("an update must write update files", bytes > 0);
      return bytes;
    }
  }

  public void testDefaultUpdateWritesFullColumn() throws Exception {
    long defaults = updateFileBytes(new IndexWriterConfig());
    long disabled = updateFileBytes(new IndexWriterConfig().setMaxDocValuesOverlays(0));
    assertEquals(
        NOTE
            + "; with the default config an update of 1 of 1000 documents wrote "
            + defaults
            + " bytes of update files, with setMaxDocValuesOverlays(0) "
            + disabled
            + " bytes",
        disabled,
        defaults);
  }
}
