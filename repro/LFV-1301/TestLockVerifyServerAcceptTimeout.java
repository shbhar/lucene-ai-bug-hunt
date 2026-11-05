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
package org.apache.lucene.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLockVerifyServerAcceptTimeout extends LuceneTestCase {

  public void testAcceptTimeoutReleasesConnectedClient() throws Exception {
    try (Socket client = new Socket()) {
      expectThrows(
          SocketTimeoutException.class,
          () ->
              LockVerifyServer.run(
                  "127.0.0.1",
                  2,
                  addr -> {
                    try {
                      client.connect(addr);
                      client.getOutputStream().write(1);
                    } catch (IOException e) {
                      throw new UncheckedIOException(e);
                    }
                  }));
      client.setSoTimeout(5000);
      // expected: after the timeout the server closes the connected client (-1); actual: no reply
      assertEquals(-1, client.getInputStream().read());
    }
  }
}
