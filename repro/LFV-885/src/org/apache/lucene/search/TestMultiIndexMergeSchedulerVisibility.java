/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.lucene.search;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.apache.lucene.index.MergeScheduler;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * CHANGES 10.3.0 'New Features': "GITHUB#15015: MultiIndexMergeScheduler: a production multi-tenant
 * merge scheduler". Its javadoc: "The multi-tenant merge scheduling can be used easily by creating a
 * MultiIndexMergeScheduler instance for each index", and the constructor is documented as "The main
 * MultiIndexMergeScheduler constructor -- use this one". The class itself is package-private, so code
 * outside org.apache.lucene.index (any application) cannot create one.
 */
public class TestMultiIndexMergeSchedulerVisibility extends LuceneTestCase {

  public void testClassIsPublic() throws Exception {
    Class<?> c = Class.forName("org.apache.lucene.index.MultiIndexMergeScheduler");
    assertTrue(MergeScheduler.class.isAssignableFrom(c));
    assertTrue(
        "the advertised merge scheduler is not visible outside its package",
        Modifier.isPublic(c.getModifiers()));
  }

  public void testMainConstructorUsableFromApplicationCode() throws Exception {
    Class<?> c = Class.forName("org.apache.lucene.index.MultiIndexMergeScheduler");
    Constructor<?> main = c.getConstructor(Directory.class);
    assertTrue(Modifier.isPublic(main.getModifiers()));
    try (Directory dir = newDirectory()) {
      // what an application does with "a MultiIndexMergeScheduler instance for each index"
      MergeScheduler ms = (MergeScheduler) main.newInstance(dir);
      ms.close();
    }
  }
}
