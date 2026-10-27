/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.lucene.index;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestParallelLeafReaderStoredCopyIntentReview extends LuceneTestCase {

  private static void writeOne(Directory directory, String name, String value) throws IOException {
    try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
      Document document = new Document();
      document.add(new StoredField(name, value));
      writer.addDocument(document);
    }
  }

  private static void copyParallel(Directory first, Directory second, Directory target)
      throws IOException {
    try (DirectoryReader firstReader = DirectoryReader.open(first);
        DirectoryReader secondReader = DirectoryReader.open(second)) {
      try (ParallelLeafReader parallel =
          new ParallelLeafReader(
              false, getOnlyLeafReader(firstReader), getOnlyLeafReader(secondReader))) {
        Document beforeCopy = parallel.storedFields().document(0);
        assertEquals("alpha-value", beforeCopy.get("alpha"));
        assertEquals("omega-value", beforeCopy.get("omega"));
        try (IndexWriter writer = new IndexWriter(target, new IndexWriterConfig())) {
          writer.addIndexes(SlowCodecReaderWrapper.wrap(parallel));
        }
      }
    }
  }

  public void testEmptyTargetRenamesLaterStoredField() throws Exception {
    try (Directory first = newDirectory();
        Directory second = newDirectory();
        Directory target = newDirectory()) {
      writeOne(first, "alpha", "alpha-value");
      writeOne(second, "omega", "omega-value");

      copyParallel(first, second, target);

      try (DirectoryReader copied = DirectoryReader.open(target)) {
        Document actual = copied.storedFields().document(0);
        assertEquals("copied document: " + actual, "omega-value", actual.get("omega"));
        assertArrayEquals(
            "omega must not become a second alpha value",
            new String[] {"alpha-value"},
            actual.getValues("alpha"));
      }
    }
  }

  public void testShiftedTargetNumbersEnableNameRemapping() throws Exception {
    try (Directory first = newDirectory();
        Directory second = newDirectory();
        Directory target = newDirectory()) {
      writeOne(first, "alpha", "alpha-value");
      writeOne(second, "omega", "omega-value");
      writeOne(target, "padding", "padding-value");

      copyParallel(first, second, target);

      try (DirectoryReader copied = DirectoryReader.open(target)) {
        Document actual = copied.storedFields().document(1);
        assertEquals("alpha-value", actual.get("alpha"));
        assertEquals("omega-value", actual.get("omega"));
        assertArrayEquals(new String[] {"alpha-value"}, actual.getValues("alpha"));
      }
    }
  }
}
