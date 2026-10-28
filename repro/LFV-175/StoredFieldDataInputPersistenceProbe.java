/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;

/**
 * A main() probe with plain default settings: {@code new IndexWriterConfig()} (default codec,
 * default merge policy) and {@code FSDirectory}. Public API only.
 *
 * <ul>
 *   <li>{@code persist}: stores the 8-byte slice buf[4..11] of a 16-byte array, commits, adds a
 *       second segment, runs forceMerge(1), reads the value back and runs CheckIndex. Exits 1 if
 *       the stored value is not the 8-byte slice (the documented behaviour).
 *   <li>{@code write DIR}: stores the same slice as document 1 in DIR and commits. Meant for
 *       unfixed Lucene.
 *   <li>{@code read DIR}: meant for Lucene with the fix. Prints the length the constructor
 *       computes, appends the same slice as document 2, reads both values back and runs CheckIndex.
 *       Exits 1 unless document 1 (written before the fix) still holds 12 bytes and document 2
 *       holds the 8-byte slice.
 * </ul>
 */
public final class StoredFieldDataInputPersistenceProbe {

  private static byte[] numbered(int n) {
    byte[] b = new byte[n];
    for (int i = 0; i < n; i++) {
      b[i] = (byte) (i + 1);
    }
    return b;
  }

  private static String hex(BytesRef r) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < r.length; i++) {
      if (i > 0) {
        sb.append(' ');
      }
      sb.append(String.format("%02x", r.bytes[r.offset + i] & 0xff));
    }
    return sb.toString();
  }

  private static Document sliceDoc(int id) {
    StoredFieldDataInput value =
        new StoredFieldDataInput(new ByteArrayDataInput(numbered(16), 4, 8));
    System.out.println(
        "PROBE doc "
            + id
            + ": new StoredFieldDataInput(new ByteArrayDataInput(buf16, 4, 8)).getLength() = "
            + value.getLength());
    Document doc = new Document();
    doc.add(new StoredField("id", id));
    doc.add(new StoredField("f", value));
    return doc;
  }

  private static TreeMap<Integer, BytesRef> readBack(Directory dir, String label)
      throws IOException {
    TreeMap<Integer, BytesRef> values = new TreeMap<>();
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      System.out.println(
          "PROBE " + label + ": segments=" + r.leaves().size() + " numDocs=" + r.numDocs());
      StoredFields storedFields = r.storedFields();
      for (int d = 0; d < r.maxDoc(); d++) {
        Document doc = storedFields.document(d);
        int id = doc.getField("id").numericValue().intValue();
        BytesRef v = BytesRef.deepCopyOf(doc.getBinaryValue("f"));
        values.put(id, v);
        System.out.println(
            "PROBE " + label + ": doc " + id + " f length=" + v.length + " bytes=" + hex(v));
      }
    }
    return values;
  }

  private static boolean checkIndex(Directory dir) throws IOException {
    try (CheckIndex checker = new CheckIndex(dir)) {
      CheckIndex.Status status = checker.checkIndex();
      System.out.println("PROBE CheckIndex clean=" + status.clean);
      return status.clean;
    }
  }

  private static int persist() throws IOException {
    Path path = Files.createTempDirectory("sfdi-persist");
    try {
      try (Directory dir = FSDirectory.open(path)) {
        try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
          w.addDocument(sliceDoc(1));
          w.commit();
          Document control = new Document();
          control.add(new StoredField("id", 2));
          control.add(new StoredField("f", new BytesRef(numbered(8))));
          w.addDocument(control);
          w.commit();
          readBack(dir, "before forceMerge");
          w.forceMerge(1);
          w.commit();
        }
        BytesRef stored = readBack(dir, "after forceMerge(1)").get(1);
        boolean clean = checkIndex(dir);
        BytesRef expected = new BytesRef(numbered(16), 4, 8);
        System.out.println("PROBE expected doc 1: length=8 bytes=" + hex(expected));
        if (stored.bytesEquals(expected) == false) {
          System.out.println("PROBE RESULT: the stored value is not the 8-byte slice");
          return 1;
        }
        return clean ? 0 : 1;
      }
    } finally {
      IOUtils.rm(path);
    }
  }

  private static int write(Path path) throws IOException {
    Files.createDirectories(path);
    try (Directory dir = FSDirectory.open(path);
        IndexWriter w =
            new IndexWriter(
                dir, new IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.CREATE))) {
      w.addDocument(sliceDoc(1));
      w.commit();
    }
    try (Directory dir = FSDirectory.open(path)) {
      readBack(dir, "written");
    }
    return 0;
  }

  private static int read(Path path) throws IOException {
    try (Directory dir = FSDirectory.open(path)) {
      try (IndexWriter w =
          new IndexWriter(
              dir, new IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.APPEND))) {
        w.addDocument(sliceDoc(2));
        w.commit();
      }
      TreeMap<Integer, BytesRef> values = readBack(dir, "after appending doc 2");
      boolean clean = checkIndex(dir);
      boolean oldKeepsExtraBytes = values.get(1).length == 12;
      boolean newIsSlice = values.get(2).bytesEquals(new BytesRef(numbered(16), 4, 8));
      System.out.println(
          "PROBE RESULT: doc 1 (written before) length="
              + values.get(1).length
              + ", doc 2 (written now) length="
              + values.get(2).length);
      return oldKeepsExtraBytes && newIsSlice && clean ? 0 : 1;
    }
  }

  public static void main(String[] args) throws IOException {
    int exit;
    switch (args.length == 0 ? "persist" : args[0]) {
      case "persist" -> exit = persist();
      case "write" -> exit = write(Path.of(args[1]));
      case "read" -> exit = read(Path.of(args[1]));
      default -> throw new IllegalArgumentException("usage: persist | write DIR | read DIR");
    }
    System.exit(exit);
  }
}
