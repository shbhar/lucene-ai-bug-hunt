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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.NIOFSDirectory;

/** Version scan (no test framework): exits 1 if a read after a failed refill returns a byte. */
public class NIOFSReadAfterFailedRefillMain {
  static byte expected(long pos) {
    return (byte) (pos ^ (pos >>> 8) ^ 0x5A);
  }

  public static void main(String[] args) throws Exception {
    Path tmp = Files.createTempDirectory("niofs-refill");
    int len = 3 * 1024;
    byte[] b = new byte[len];
    for (int i = 0; i < len; i++) b[i] = expected(i);
    int bad = 0;
    try (Directory dir = new NIOFSDirectory(tmp)) {
      try (IndexOutput out = dir.createOutput("f", IOContext.DEFAULT)) {
        out.writeBytes(b, len);
      }
      // 1. clone read after the original is closed
      IndexInput in = dir.openInput("f", IOContext.DEFAULT);
      IndexInput clone = in.clone();
      clone.readByte();
      clone.seek(2048);
      in.close();
      String first, second;
      try {
        clone.readByte();
        first = "returned a byte";
      } catch (IOException | RuntimeException e) {
        first = e.getClass().getSimpleName();
      }
      try {
        byte v = clone.readByte();
        second = "returned " + v + " for position 2048 (file has " + expected(2048) + ")";
        bad++;
      } catch (IOException | RuntimeException e) {
        second = e.getClass().getSimpleName();
      }
      System.out.println("clone-after-close: first read " + first + "; second read " + second);
      // 2. interrupt
      try (IndexInput in2 = dir.openInput("f", IOContext.DEFAULT)) {
        in2.readByte();
        in2.seek(2048);
        Thread.currentThread().interrupt();
        try {
          in2.readByte();
          first = "returned a byte";
        } catch (IOException | RuntimeException e) {
          first = e.getClass().getSimpleName();
        }
        Thread.interrupted();
        try {
          byte v = in2.readByte();
          second = "returned " + v + " for position 2048 (file has " + expected(2048) + ")";
          bad++;
        } catch (IOException | RuntimeException e) {
          second = e.getClass().getSimpleName();
        }
        System.out.println("after-interrupt: first read " + first + "; second read " + second);
      } catch (IOException e) {
        // close after the interrupt may complain on some versions
      }
    }
    System.out.println(bad == 0 ? "OK: no stale bytes" : "BUG: " + bad + " stale read(s)");
    System.exit(bad == 0 ? 0 : 1);
  }
}
