# CharArrayMap and CharArraySet iterators never throw NoSuchElementException: next() past the end returns a phantom Map.Entry from entrySet() (whose getKey/getValue/toString throw ArrayIndexOutOfBoundsException) and throws ArrayIndexOutOfBoundsException from CharArraySet.iterator() and the keySet() and values() iterators (since 3.1.0)

Finding LFV-1200 · Area: analysis / CharArrayMap and CharArraySet iterators (stop-word, keyword and stem-override sets and maps) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1200.html](../../bugs/LFV-1200.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You iterate a CharArrayMap's entrySet() past its last entry: new CharArrayMap&lt;&gt;(1, false), put("a", 1), it.next(), and then it.next() again after it.hasNext() returned false. You expect NoSuchElementException, as java.util.Iterator.next() specifies and as HashMap's iterator does. Instead the second next() returns a Map.Entry for a slot past the end of the table, whose getKey(), getValue() and toString() throw ArrayIndexOutOfBoundsException, while CharArraySet.iterator(), the keySet() and values() iterators and Collections.max/min of an empty CharArraySet throw ArrayIndexOutOfBoundsException ('Index 8 out of bounds for length 8') at once.

## Expected vs actual

- Expected: The second `it.next()` throws `NoSuchElementException`.
- Actual: `Expected exception NoSuchElementException but no exception was thrown`: `next()` returned a fake entry for slot 8 (`TestCharArrayMapIteratorExhausted.java:33`).

## Files

- [src/org/apache/lucene/analysis/TestCharArrayMapIteratorExhausted.java](src/org/apache/lucene/analysis/TestCharArrayMapIteratorExhausted.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): still reproduces.
- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- The test under src/ is the same test without the license header, so its failure is reported at TestCharArrayMapIteratorExhausted.java:17; the copy at the top of this folder, shown on the finding page, has the header and fails the same way at line 33.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.TestCharArrayMapIteratorExhausted
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCharArrayMapIteratorExhausted.java](TestCharArrayMapIteratorExhausted.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
