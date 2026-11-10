package org.apache.lucene.analysis;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFactoryLookupContextClassLoader extends LuceneTestCase {

  public static class OnlyOnContextLoaderFilterFactory extends TokenFilterFactory {
    public static final String NAME = "onlyOnContextLoader";

    public OnlyOnContextLoaderFilterFactory(Map<String, String> args) {
      super(args);
    }

    public OnlyOnContextLoaderFilterFactory() {
      throw defaultCtorException();
    }

    @Override
    public TokenStream create(TokenStream input) {
      return input;
    }
  }

  public void testForNameUsesContextClasspath() throws Exception {
    Path root = createTempDir();
    Path services = Files.createDirectories(root.resolve("META-INF/services"));
    Files.writeString(
        services.resolve(TokenFilterFactory.class.getName()),
        OnlyOnContextLoaderFilterFactory.class.getName());
    Thread thread = Thread.currentThread();
    ClassLoader saved = thread.getContextClassLoader();
    try (URLClassLoader loader =
        new URLClassLoader(new URL[] {root.toUri().toURL()}, getClass().getClassLoader())) {
      thread.setContextClassLoader(loader);
      // javadoc "looks up a tokenfilter by name from context classpath": expected the factory, actual IllegalArgumentException "does not exist"
      assertTrue(
          TokenFilterFactory.forName("onlyOnContextLoader", new HashMap<>())
              instanceof OnlyOnContextLoaderFilterFactory);
    } finally {
      thread.setContextClassLoader(saved);
    }
  }
}
