# MMapDirectory(Path, LockFactory, long) and the protected FSDirectory(Path, LockFactory) constructor document 'lockFactory ... or null for the default (NativeFSLockFactory)', but a null lockFactory throws NullPointerException 'LockFactory must not be null, use an explicit instance!' from BaseDirectory (javadoc only: rejecting null is intended since 5.0.0, LUCENE-5953)

Finding LFV-1441 · Area: store / FSDirectory and MMapDirectory constructors (lockFactory @param javadoc; BaseDirectory null check) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1441.html](../../bugs/LFV-1441.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You open an index directory with new MMapDirectory(path, null, MMapDirectory.DEFAULT_MAX_CHUNK_SIZE), or write an FSDirectory subclass whose constructor calls super(path, null), because both javadocs say '@param lockFactory the lock factory to use, or null for the default (NativeFSLockFactory)'. You expect a directory that locks with NativeFSLockFactory; instead the constructor throws NullPointerException 'LockFactory must not be null, use an explicit instance!' from BaseDirectory and no directory is returned. Lucene 5.0 made null illegal on purpose (LUCENE-5953) and removed the same clause from five sibling constructors, but missed these two, so only the javadoc is wrong (use FSLockFactory.getDefault() instead).

## Expected vs actual

- Expected: A directory whose lock factory is `NativeFSLockFactory.INSTANCE`, as the javadoc says.
- Actual: `NullPointerException: LockFactory must not be null, use an explicit instance!` at `BaseDirectory.<init>(BaseDirectory.java:37)`; no directory.

## Files

- [src/org/apache/lucene/store/TestMMapDirectoryNullLockFactory.java](src/org/apache/lucene/store/TestMMapDirectoryNullLockFactory.java) (JUnit test)

## Run it

- Tested on Lucene main f794ff5a505c (2026-11-06): reproduced.
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

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.store.TestMMapDirectoryNullLockFactory
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestMMapDirectoryNullLockFactory.java](TestMMapDirectoryNullLockFactory.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
