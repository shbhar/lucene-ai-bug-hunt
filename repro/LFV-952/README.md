# Polygon.fromGeoJSON keeps a full key-path String alive at every nesting level, so its heap use grows with nesting depth times key length: a valid 750 KB GeoJSON Feature (900 nested arrays under one long property key), or a 1 MB one (1,000 nested objects with 1,000-char keys), runs out of a 512 MiB heap (every release since 6.2.0)

Bug LFV-952 · Area: core / geo GeoJSON parsing (Polygon.fromGeoJSON, SimpleGeoJSONPolygonParser) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You call Polygon.fromGeoJSON on a valid GeoJSON Feature whose properties hold one member with a very long key (about 745,000 chars) above 900 nested arrays, or 1,000 nested objects each under a 1,000-char key (about 1 MB of text), and expect the polygon back. Instead the call fails with OutOfMemoryError: Java heap space in a 512 MiB heap, because SimpleGeoJSONPolygonParser builds a new full key path (path + "." + key, path + ".") for every nesting level and each recursive frame keeps its copy alive, so live memory grows with depth times path length, although the parser only ever compares the path with five short constants and a "crs" prefix. The same text as a string value, or the same nesting under a short key, parses fine, so neither a size cap nor a usual depth cap protects the caller.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The call fails with OutOfMemoryError: Java heap space in a 512 MiB heap, because SimpleGeoJSONPolygonParser builds a new full key path (path + "." + key, path + ".") for every nesting level and each recursive frame keeps its copy alive, so live memory grows with depth times path length, although the parser only ever compares the path with five short constants and a "crs" prefix.

## Files

- [src/org/apache/lucene/geo/TestGeoJSONPathMemory.java](src/org/apache/lucene/geo/TestGeoJSONPathMemory.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed 383DEE9
java -da -dsa -Dtests.asserts=false -Xmx512m --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=383DEE9 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.geo.TestGeoJSONPathMemory
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/geo-text-parsers-deep-nesting-vm-errors-siblings-of-16423/](../../models/geo-text-parsers-deep-nesting-vm-errors-siblings-of-16423/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
