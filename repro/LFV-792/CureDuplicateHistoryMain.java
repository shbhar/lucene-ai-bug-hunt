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
package org.apache.lucene.document;

import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;

/**
 * Version-portable check (compiles against 8.2.0 .. main, public API only): does
 * XYShape/LatLonShape.createIndexableFields index any triangle twice?
 *
 * <p>Uses only XYPolygon/Polygon, XYShape/LatLonShape.createIndexableFields and the encoded
 * triangle bytes, so the same source runs on every release that has these classes (the classes
 * live in the sandbox module up to 8.4.x and in core from 8.5.0). Exits non-zero (AssertionError)
 * when a witness indexes the same triangle more than once. A control polygon must index each
 * triangle once on every version.
 */
public class CureDuplicateHistoryMain {

  /** POLYGON((4 2, 3 0, 1 2, 0 0, 0 4, 4 2), (2 1, 3 2, 2 2, 2 1)): valid, hole touches shell */
  static XYPolygon validXY() {
    XYPolygon hole = new XYPolygon(new float[] {2, 3, 2, 2}, new float[] {1, 2, 2, 1});
    return new XYPolygon(new float[] {4, 3, 1, 0, 0, 4}, new float[] {2, 0, 2, 0, 4, 2}, hole);
  }

  /** the same polygon in degrees (lat = y, lon = x) */
  static Polygon validLatLon() {
    Polygon hole = new Polygon(new double[] {1, 2, 2, 1}, new double[] {2, 3, 2, 2});
    return new Polygon(new double[] {2, 0, 2, 0, 4, 2}, new double[] {4, 3, 1, 0, 0, 4}, hole);
  }

  /** POLYGON((6 0, 6 4, 5 1, 6 5, 6 1, 2 6, 2 0, 6 0)): self-intersecting ring */
  static XYPolygon selfIntersectingXY() {
    return new XYPolygon(new float[] {6, 6, 5, 6, 6, 2, 2, 6}, new float[] {0, 4, 1, 5, 1, 6, 0, 0});
  }

  /**
   * POLYGON((6 3, 0 6, 4 0, 3 4, 5 1, 3 2, 6 3)): a hole-free self-intersecting ring, found by the
   * seeded lattice search HoleFreeRingDuplicateAreaSearchMain; the tessellator of every release
   * from 8.2.0 on accepts it, and the cure step cuts off the triangle (3 2, 4 0, 3 4) of area 1
   */
  static XYPolygon selfIntersectingXY2() {
    return new XYPolygon(new float[] {6, 0, 4, 3, 5, 3, 6}, new float[] {3, 6, 0, 4, 1, 2, 3});
  }

  /** control: an L-shaped hexagon, no hole, no self-intersection, no cure step */
  static XYPolygon controlXY() {
    return new XYPolygon(new float[] {0, 4, 4, 2, 2, 0, 0}, new float[] {0, 0, 2, 2, 4, 4, 0});
  }

  /** returns the number of fields that repeat an earlier field's triangle bytes */
  static int report(String name, IndexableField[] fields) {
    Set<BytesRef> seen = new HashSet<>();
    int dups = 0;
    for (IndexableField f : fields) {
      if (seen.add(BytesRef.deepCopyOf(f.binaryValue())) == false) {
        dups++;
      }
    }
    System.out.println(
        name + ": indexed triangles=" + fields.length + " distinct=" + seen.size() + " repeated=" + dups);
    return dups;
  }

  public static void main(String[] args) {
    int control = report("control L-hexagon (XY)", XYShape.createIndexableFields("shape", controlXY()));
    if (control != 0) {
      throw new AssertionError("control polygon indexed a triangle twice: the check itself is broken");
    }
    StringBuilder failures = new StringBuilder();
    String[] names = {
      "valid touching-hole polygon (XY)",
      "valid touching-hole polygon (LatLon)",
      "self-intersecting ring (XY)",
      "self-intersecting ring 2 (XY)"
    };
    for (int i = 0; i < names.length; i++) {
      int dups;
      try {
        IndexableField[] fields;
        if (i == 0) {
          fields = XYShape.createIndexableFields("shape", validXY());
        } else if (i == 1) {
          fields = LatLonShape.createIndexableFields("shape", validLatLon());
        } else if (i == 2) {
          fields = XYShape.createIndexableFields("shape", selfIntersectingXY());
        } else {
          fields = XYShape.createIndexableFields("shape", selfIntersectingXY2());
        }
        dups = report(names[i], fields);
      } catch (IllegalArgumentException e) {
        System.out.println(names[i] + ": rejected: " + e.getMessage());
        continue;
      }
      if (dups > 0) {
        failures.append(names[i]).append(" indexes ").append(dups).append(" triangle(s) twice; ");
      }
    }
    if (failures.length() > 0) {
      throw new AssertionError("a tessellation must not contain the same triangle twice: " + failures);
    }
    System.out.println("OK: no triangle indexed twice");
  }
}
