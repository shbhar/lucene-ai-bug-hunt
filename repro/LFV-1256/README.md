# Tessellator.tessellate(XYPolygon, boolean, Monitor) hands every Cartesian vertex to the Monitor as a geographic Point(y, x), so attaching a Monitor to an XY polygon with a vertex at |x| &gt; 180 or |y| &gt; 90 throws 'invalid longitude/latitude', although the same polygon tessellates without one (every release since 9.3.0)

Finding LFV-1256 · Area: geo / Tessellator debugging Monitor (XYPolygon tessellation) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1256.html](../../bugs/LFV-1256.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You tessellate a Cartesian XYPolygon, for example the square (0,0) (500,0) (500,50) (0,50), with Tessellator.tessellate(polygon, false, monitor) and a no-op Tessellator.Monitor to watch the algorithm, and expect the same 2 triangles that tessellate(polygon, false) returns, since the Monitor is documented as an observer that 'will receive calls with internal data at each step of the triangulation algorithm'. Instead the call throws IllegalArgumentException 'invalid longitude 500.0; must be between -180.0 and 180.0': getPoints, which builds the point lists passed to the callbacks, wraps every node as a geographic new Point(node.getY(), node.getX()), and the Point constructor range-checks latitude and longitude. Any XY vertex with |x| &gt; 180 or |y| &gt; 90 fails this way, so the XY overload with a monitor works only when the Cartesian coordinates happen to lie inside the latitude/longitude ranges.

## Expected vs actual

- Expected: 1 triangle, the same as `Tessellator.tessellate(triangle, false)` returns.
- Actual: `IllegalArgumentException: invalid longitude 500.0; must be between -180.0 and 180.0` from `Tessellator.getPoints`, while the first monitor callback's point list is built (the monitor is never called).

## Files

- [src/org/apache/lucene/geo/TestXYTessellatorMonitor.java](src/org/apache/lucene/geo/TestXYTessellatorMonitor.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.geo.TestXYTessellatorMonitor
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestXYTessellatorMonitor.java](TestXYTessellatorMonitor.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
