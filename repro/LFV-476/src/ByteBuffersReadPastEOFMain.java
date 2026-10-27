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
import java.io.EOFException;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

/** Version scan: exits 1 unless reads past EOF of a 1030-byte ByteBuffersDirectory file throw EOF. */
public class ByteBuffersReadPastEOFMain {
  interface R {
    void run(IndexInput in) throws Exception;
  }

  public static void main(String[] args) throws Exception {
    int bad = 0;
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexOutput out = dir.createOutput("f", IOContext.DEFAULT)) {
        out.writeBytes(new byte[1030], 1030);
      }
      String[] names = {"readShort@1029", "readInt@1028", "readLong@1025", "readInt@1027"};
      long[] at = {1029, 1028, 1025, 1027};
      R[] ops = {IndexInput::readShort, IndexInput::readInt, IndexInput::readLong, IndexInput::readInt};
      for (int i = 0; i < ops.length; i++) {
        try (IndexInput in = dir.openInput("f", IOContext.DEFAULT)) {
          in.seek(at[i]);
          String r;
          try {
            ops[i].run(in);
            r = "no exception";
            bad++;
          } catch (EOFException e) {
            r = "EOFException";
          } catch (Throwable t) {
            r = t.getClass().getSimpleName();
            bad++;
          }
          System.out.println(names[i] + " -> " + r);
        }
      }
    }
    System.out.println(bad == 0 ? "OK" : "BUG: " + bad);
    System.exit(bad == 0 ? 0 : 1);
  }
}
