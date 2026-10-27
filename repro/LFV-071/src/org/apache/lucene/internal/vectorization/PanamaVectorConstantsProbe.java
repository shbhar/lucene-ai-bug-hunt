package org.apache.lucene.internal.vectorization;

/** Reads the preferred vector bit size reflectively (PanamaVectorConstants is a java25 class). */
final class PanamaVectorConstantsProbe {
  static String bits() {
    try {
      var cls = Class.forName("org.apache.lucene.internal.vectorization.PanamaVectorConstants");
      var f = cls.getDeclaredField("PREFERRED_VECTOR_BITSIZE");
      f.setAccessible(true);
      return String.valueOf(f.getInt(null));
    } catch (Throwable t) {
      return "n/a(" + t.getClass().getSimpleName() + ")";
    }
  }
}
