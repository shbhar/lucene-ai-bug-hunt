import org.apache.lucene.spatial.prefix.tree.Cell;
import org.apache.lucene.spatial.prefix.tree.CellIterator;
import org.apache.lucene.spatial.prefix.tree.PackedQuadPrefixTree;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.ShapeFactory;

/** Print-only: the cells PackedQuadPrefixTree(GEO) uses to index and to look up a point. Public API only. */
public class Plus180CellProbe {
  public static void main(String[] args) {
    SpatialContext ctx = SpatialContext.GEO;
    ShapeFactory f = ctx.getShapeFactory();
    System.out.println("level-1 cell rectangles vs Pt(180,10):");
    System.out.println("  NW [-180,0]x[0,90] relate = " + f.rect(-180, 0, 0, 90).relate(f.pointXY(180, 10)));
    System.out.println("  NE [0,180]x[0,90]  relate = " + f.rect(0, 180, 0, 90).relate(f.pointXY(180, 10)));
    for (double x : new double[] {180, -180}) {
      Point p = f.pointXY(x, 10);
      for (int maxLevels : new int[] {1, 2, 3, 8}) {
        PackedQuadPrefixTree grid = new PackedQuadPrefixTree(ctx, maxLevels);
        CellIterator it = grid.getTreeCellIterator(p, maxLevels);
        Cell last = null;
        while (it.hasNext()) {
          last = it.next();
        }
        // the query side: what a point query descends to (LegacyCell.getNextLevelCells(Point) -> getCell)
        Cell world = grid.getWorldCell();
        Cell q = world;
        for (int l = 0; l < maxLevels; l++) {
          CellIterator sub = q.getNextLevelCells(p);
          q = sub.next();
        }
        System.out.println(
            "Pt(" + x + ",10) maxLevels=" + maxLevels + "  index leaf=" + last.getShape()
                + "  query leaf=" + q.getShape() + "  same=" + last.getShape().equals(q.getShape()));
      }
    }
  }
}
