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

import java.util.HexFormat;
import java.util.TreeSet;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.util.IntsRefBuilder;
import org.apache.lucene.util.fst.Builder;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.PositiveIntOutputs;
import org.apache.lucene.util.fst.Util;

/**
 * Run against lucene-core 8.11.4: builds the BYTE2 FST used as the fixture of
 * TestFSTMetadataSaveVersion (keys in String order mapped to 1, 2, ...) and prints its metadata and
 * data bytes as hex.
 */
public class WriteV7Byte2Fst {
  public static void main(String[] args) throws Exception {
    String[] raw = {
      "ab", "ac", "ad", "b", "cat", "dog", "ü", "日本", "日本語"
    };
    TreeSet<String> keys = new TreeSet<>(); // String order == UTF-16 code unit order
    for (String s : raw) {
      keys.add(s);
    }
    Builder<Long> b = new Builder<>(FST.INPUT_TYPE.BYTE2, PositiveIntOutputs.getSingleton());
    IntsRefBuilder scratch = new IntsRefBuilder();
    long i = 1;
    for (String k : keys) {
      b.add(Util.toUTF16(k, scratch), i++);
    }
    FST<Long> fst = b.finish();
    ByteBuffersDataOutput meta = new ByteBuffersDataOutput();
    ByteBuffersDataOutput data = new ByteBuffersDataOutput();
    fst.save(meta, data);
    System.out.println("lucene " + org.apache.lucene.util.Version.LATEST);
    System.out.println("META " + HexFormat.of().formatHex(meta.toArrayCopy()));
    System.out.println("DATA " + HexFormat.of().formatHex(data.toArrayCopy()));
  }
}
