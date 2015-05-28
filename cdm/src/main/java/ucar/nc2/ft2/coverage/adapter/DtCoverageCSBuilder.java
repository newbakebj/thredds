/* Copyright */
package ucar.nc2.ft2.coverage.adapter;

import ucar.nc2.Dimension;
import ucar.nc2.constants.AxisType;
import ucar.nc2.dataset.*;
import ucar.nc2.ft2.coverage.grid.GridCoordSys;
import ucar.nc2.units.SimpleUnit;
import ucar.unidata.geoloc.ProjectionImpl;
import ucar.unidata.geoloc.projection.RotatedPole;

import java.util.*;

/**
 * Move functionality of ft.CoverageCSFactory
 *
 * @author caron
 * @since 5/26/2015
 */
public class DtCoverageCSBuilder {

  // classify based on largest coordinate system
  public static DtCoverageCSBuilder classify(NetcdfDataset ds, Formatter errlog) {
    if (errlog != null) errlog.format("CoverageFactory for '%s'%n", ds.getLocation());

    // sort by largest size first
    List<CoordinateSystem> css = new ArrayList<>(ds.getCoordinateSystems());
    Collections.sort(css, new Comparator<CoordinateSystem>() {
      public int compare(CoordinateSystem o1, CoordinateSystem o2) {
        return o2.getCoordinateAxes().size() - o1.getCoordinateAxes().size();
      }
    });

    DtCoverageCSBuilder fac = null;
    for (CoordinateSystem cs : css) {
      fac = new DtCoverageCSBuilder(ds, cs, errlog);
      if (fac.type != null) break;
    }
    if (fac == null) return null;
    if (errlog != null) errlog.format("coverage = %s%n", fac.type);
    return fac;
  }

  public static String describe(Formatter f, NetcdfDataset ds) {
    DtCoverageCSBuilder fac = classify(ds, f);
    return (fac == null || fac.type == null) ? "" : fac.toString();
  }

  ////////////////////////////////////////////////////////////////////////////////////
  GridCoordSys.Type type;

  boolean isLatLon;
  CoordinateAxis xaxis, yaxis, timeAxis;
  CoordinateAxis1D vertAxis, ensAxis, timeOffsetAxis;
  CoordinateAxis1DTime rtAxis;
  List<CoordinateAxis> independentAxes;
  List<CoordinateAxis> otherAxes;
  List<CoordinateAxis> standardAxes;
  List<CoordinateTransform> coordTransforms;
  ProjectionImpl orgProj;

  DtCoverageCSBuilder(NetcdfDataset ds, CoordinateSystem cs, Formatter errlog) {
    // this.cs = cs; // LOOK gc ??

    // must be at least 2 axes
    if (cs.getRankDomain() < 2) {
      if (errlog != null) errlog.format("CoordinateSystem '%s': domain rank < 2%n", cs.getName());
      return;
    }

    //////////////////////////////////////////////////////////////
    // horiz
    // must be lat/lon or have x,y and projection
    if (!cs.isLatLon()) {
      // do check for GeoXY
      if ((cs.getXaxis() == null) || (cs.getYaxis() == null)) {
        if (errlog != null) errlog.format("%s: NO Lat,Lon or X,Y axis%n", cs.getName());
        return;
      }
      if (null == cs.getProjection()) {
        if (errlog != null) errlog.format("%s: NO projection found%n", cs.getName());
        return;
      }
    }

    // obtain the x,y or lat/lon axes. x,y normally must be convertible to km
    if (cs.isGeoXY()) {
      xaxis = cs.getXaxis();
      yaxis = cs.getYaxis();

      ProjectionImpl p = cs.getProjection();
      if (!(p instanceof RotatedPole)) {
        if (!SimpleUnit.kmUnit.isCompatible(xaxis.getUnitsString())) {
          if (errlog != null) errlog.format("%s: X axis units are not convertible to km%n", cs.getName());
          //return false;
        }
        if (!SimpleUnit.kmUnit.isCompatible(yaxis.getUnitsString())) {
          if (errlog != null) errlog.format("%s: Y axis units are not convertible to km%n", cs.getName());
          //return false;
        }
      }
    } else {
      xaxis = cs.getLonAxis();
      yaxis = cs.getLatAxis();
      isLatLon = true;
    }

    // check x,y rank <= 2
    if ((xaxis.getRank() > 2) || (yaxis.getRank() > 2)) {
      if (errlog != null) errlog.format("%s: X and Y axis rank must be <= 2%n", cs.getName());
      return;
    }

    // check x,y with size 1
    if ((xaxis.getSize() < 2) || (yaxis.getSize() < 2)) {
      if (errlog != null) errlog.format("%s: X and Y axis size must be >= 2%n", cs.getName());
      return;
    }

    // check that the x,y have at least 2 dimensions between them ( this eliminates point data)
    List<Dimension> xyDomain = CoordinateSystem.makeDomain(new CoordinateAxis[]{xaxis, yaxis});
    if (xyDomain.size() < 2) {
      if (errlog != null) errlog.format("%s: X and Y axis must have 2 or more dimensions%n", cs.getName());
      return;
    }
    standardAxes = new ArrayList<>();
    standardAxes.add(xaxis);
    standardAxes.add(yaxis);

    independentAxes = new ArrayList<>();
    otherAxes = new ArrayList<>();
    for (CoordinateAxis axis : cs.getCoordinateAxes()) {
      if (axis.isCoordinateVariable()) independentAxes.add(axis);
      else otherAxes.add(axis);
    }
    Collections.sort(independentAxes, new Comparator<CoordinateAxis>() {  // sort by axis type
      public int compare(CoordinateAxis o1, CoordinateAxis o2) {
        AxisType t1 = o1.getAxisType();
        AxisType t2 = o2.getAxisType();
        if (t1 != null && t2 != null)
          return t1.axisOrder() - t2.axisOrder();
        return (t1 == null) ? ((t2 == null) ? 0 : -1) : 1;
      }
    });

    //////////////////////////////////////////////////////////////
    // vert
    CoordinateAxis zAxis = cs.getHeightAxis();
    if ((zAxis == null) || (zAxis.getRank() > 1)) {
      if (cs.getPressureAxis() != null) zAxis = cs.getPressureAxis();
    }
    if ((zAxis == null) || (zAxis.getRank() > 1)) {
      if (cs.getZaxis() != null) zAxis = cs.getZaxis();
    }
    if (zAxis != null && zAxis instanceof CoordinateAxis1D) {
      vertAxis = (CoordinateAxis1D) zAxis;
      standardAxes.add(vertAxis);
    }

    //////////////////////////////////////////////////////////////
    // time
    CoordinateAxis rt = cs.findAxis(AxisType.RunTime);
    if (rt != null) {
      if (!rt.isScalar() && !(rt instanceof CoordinateAxis1D)) {   // A runtime axis must be scalar or one-dimensional
        if (errlog != null) errlog.format("%s: RunTime axis must be 1D or scalar%n", cs.getName());
        return;
      }
      if (!(rt instanceof CoordinateAxis1DTime)) {    // convert to CoordinateAxis1DTime
        try {
          rtAxis = CoordinateAxis1DTime.factory(ds, rt, errlog);
        } catch (Exception e) {
          if (errlog != null)
            errlog.format("%s: Error reading runtime coord= %s err= %s%n", rt.getDatasetLocation(), rt.getFullName(), e.getMessage());
          return;
        }
      } else {
        rtAxis = (CoordinateAxis1DTime) rt;
      }
    }

    CoordinateAxis t = cs.getTaxis();
    if ((t != null) && t.getRank() > 1) {  // If time axis is two-dimensional...
      if (rtAxis != null && rtAxis.getRank() == 1) {
        // time first dimension must agree with runtime
        if (!rtAxis.getDimension(0).equals(t.getDimension(0))) {
          if (errlog != null) errlog.format("%s: 2D Time axis first dimension must be runtime%n", cs.getName());
          return;
        }
      }
    }

    if (t != null) {
      if (t instanceof CoordinateAxis1D && !(t instanceof CoordinateAxis1DTime)) {  // convert time axis into CoordinateAxis1DTime if possible
        try {
          timeAxis = CoordinateAxis1DTime.factory(ds, t, errlog);
        } catch (Exception e) {
          if (errlog != null)
            errlog.format("%s: Error reading time coord= %s err= %s%n", t.getDatasetLocation(), t.getFullName(), e.getMessage());
          return;
        }
      } else {
        timeAxis = t;
      }
    }
    if (timeAxis != null)
      standardAxes.add(t);
    if (rtAxis != null)
      standardAxes.add(rtAxis);

    CoordinateAxis toAxis = cs.findAxis(AxisType.TimeOffset);
    if (toAxis != null && toAxis.getRank() == 1) {
      timeOffsetAxis = (CoordinateAxis1D) toAxis;
      standardAxes.add(timeOffsetAxis);
    }

    if (t == null && rtAxis != null && timeOffsetAxis != null) {
      // LOOK create time coord ??
    }

    CoordinateAxis eAxis = cs.findAxis(AxisType.Ensemble);
    if (eAxis != null && eAxis instanceof CoordinateAxis1D) {
      ensAxis = (CoordinateAxis1D) eAxis;
      standardAxes.add(ensAxis);
    }

    this.type = classify(xyDomain);
    this.coordTransforms = new ArrayList<>(cs.getCoordinateTransforms());
    this.orgProj = cs.getProjection();
  }

  private GridCoordSys.Type classify (List<Dimension> xyDomain) {

    // now to classify
    boolean is2Dtime = (rtAxis != null) && (timeOffsetAxis != null || (timeAxis != null && timeAxis.getRank() == 2));
    if (is2Dtime) {
      return GridCoordSys.Type.Fmrc;   // LOOK this would allow 2d horiz
    }

    boolean is2Dhoriz = isLatLon && (xaxis.getRank() == 2) && (yaxis.getRank() == 2);
    if (is2Dhoriz) {
      if (timeAxis != null && CoordinateSystem.isSubset(timeAxis.getDimensionsAll(), xyDomain))
        return  GridCoordSys.Type.Swath;   // LOOK prob not exactly right
      else
        return  GridCoordSys.Type.Curvilinear;
    }

    boolean alloneD = true;
    for (CoordinateAxis axis : standardAxes) {  // LOOK prob not right
      if (!axis.isCoordinateVariable()) alloneD = false;
    }
    if (alloneD) {
      return GridCoordSys.Type.Grid;
    }

    // default
    return GridCoordSys.Type.Coverage;
  }

  public DtCoverageCS makeCoordSys() {
    switch (type) {
      case Grid:
        return new GridCS(this);
      case Fmrc:
        return new FmrcCS(this);
      case Curvilinear:
        return new CurvilinearCS(this);
      case Swath:
        return new SwathCS(this);
    }
    return new DtCoverageCS(this);
  }

  @Override
  public String toString() {
    Formatter f2 = new Formatter();
    f2.format("%s", type == null ? "" : type.toString());
    f2.format("%n vert=%s", vertAxis == null ? "" : vertAxis.getNameAndDimensions());
    f2.format("%n time=%s", timeAxis == null ? "" : timeAxis.getNameAndDimensions());
    f2.format("%n rtime=%s", rtAxis == null ? "" : rtAxis.getNameAndDimensions());
    f2.format("%n timeOffset=%s", timeOffsetAxis == null ? "" : timeOffsetAxis.getNameAndDimensions());
    f2.format("%n ensAxis=%s", ensAxis == null ? "" : ensAxis.getNameAndDimensions());
    f2.format("%n independentAxes=(");
    for (CoordinateAxis axis : independentAxes)
      f2.format("%s,", axis.getShortName());
    f2.format(") {");
    for (Dimension dim : CoordinateSystem.makeDomain(independentAxes))
      f2.format("%s,", dim.getShortName());
    f2.format("}");
    f2.format("%n otherAxes=(");
    for (CoordinateAxis axis : otherAxes)
      f2.format("%s,", axis.getShortName());
    f2.format(") {");
    for (Dimension dim : CoordinateSystem.makeDomain(otherAxes))
      f2.format("%s,", dim.getShortName());
    f2.format("}");
    f2.format("%n standardAxes=(");
    for (CoordinateAxis axis : standardAxes)
      f2.format("%s,", axis.getShortName());
    f2.format(") {");
    for (Dimension dim : CoordinateSystem.makeDomain(standardAxes))
      f2.format("%s,", dim.getShortName());
    f2.format("}%n");

    return f2.toString();
  }

  public String showSummary() {
    if (type == null) return "";

    Formatter f2 = new Formatter();
    f2.format("%s", type.toString());

    f2.format("(");
    int count = 0;
    for (CoordinateAxis axis : standardAxes) {
      if (count++ > 0) f2.format(",");
      f2.format("%s", axis.getAxisType() == null ? axis.getShortName() : axis.getAxisType().getCFAxisName());
    }
    f2.format(")");

    if (otherAxes.size() > 0) {
      f2.format(": ");
      count = 0;
      for (CoordinateAxis axis : otherAxes) {
        if (count++ > 0) f2.format(",");
        f2.format("%s", axis.getShortName());
      }
    }
    return f2.toString();
  }



}