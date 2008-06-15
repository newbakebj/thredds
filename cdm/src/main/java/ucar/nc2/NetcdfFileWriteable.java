/*
 * Copyright 1997-2008 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package ucar.nc2;

import ucar.ma2.*;
import ucar.nc2.iosp.netcdf3.SPFactory;
import ucar.nc2.iosp.netcdf3.N3iosp;
import ucar.nc2.iosp.IOServiceProviderWriter;

import java.util.*;
import java.io.IOException;
import java.io.File;

/**
 * Create/Write netCDF-3 formatted files. <p>
 * When a file is first created, it is in is "define mode", where the header objects (Dimensions, Attributes and Variables)
 *  may be added, deleted and modified, but no data may be written.
 * Once create() is called, you can no longer modify the header, but you can now write data.
 * An existing file is opened in write mode.<p>
 *
 * If setRedefine(true) is called, the file goes into define mode, and header objects can be changed.
 * When setRedefine(false) is called, the new header is written, and the old file data is copied to the new file.
 * This can be quite costly. 
 *
 * @author caron
 * @see NetcdfFile
 */

public class NetcdfFileWriteable extends NetcdfFile {
  private IOServiceProviderWriter spiw;

  // modes
  private boolean defineMode;

  // state
  private boolean isLargeFile;
  private boolean fill;
  private int extraHeader;
  private long preallocateSize;

  /**
   * Open an existing Netcdf file for writing data. Fill mode is true.
   * Cannot add new objects, you can only read/write data to existing Variables.
   *
   * @param location name of existing file to open.
   * @return existing file that can be written to
   * @throws java.io.IOException on I/O error
   */
  static public NetcdfFileWriteable openExisting(String location) throws IOException {
    return new NetcdfFileWriteable(location, true, true);
  }

  /**
   * Open an existing Netcdf file for writing data.
   * Cannot add new objects, you can only read/write data to existing Variables.
   * Setting fill = false is more efficient, use when you know you will write all data.
   *
   * @param location name of existing file to open.
   * @param fill     if true, the data is first written with fill values.
   * @return existing file that can be written to
   * @throws IOException on I/O error
   */
  static public NetcdfFileWriteable openExisting(String location, boolean fill) throws IOException {
    return new NetcdfFileWriteable(location, fill, true);
  }

  /**
   * Create a new Netcdf file, with fill mode true.
   *
   * @param location name of new file to open; if it exists, will overwrite it.
   * @return new file that can be written to
   * @throws IOException on I/O error
   */
  static public NetcdfFileWriteable createNew(String location) throws IOException {
    return new NetcdfFileWriteable(location, true, false);
  }

  /**
   * Create a new Netcdf file, put it into define mode. Make calls to addXXX(), then
   * when all objects are added, call create(). You cannot read or write data until create() is called.
   * Setting fill = false is more efficient, use when you know you will write all data.
   *
   * @param location name of new file to open; if it exists, will overwrite it.
   * @param fill     if true, the data is first written with fill values.
   *                 Leave false if you expect to write all data values, set to true if you want to be
   *                 sure that unwritten data values have the fill value in it. (default is false)
   * @return new file that can be written to
   * @throws IOException on I/O error
   */
  static public NetcdfFileWriteable createNew(String location, boolean fill) throws IOException {
    return new NetcdfFileWriteable(location, fill, false);
  }

  /**
   * Create a new Netcdf file, put it into define mode.
   *
   * @param location open a new file at this location
   * @param fill set fill mode
   * @param isExisting true if file already exists
   * @throws IOException on I/O error
   */
  private NetcdfFileWriteable(String location, boolean fill, boolean isExisting) throws IOException {
    super();
    this.location = location;
    this.fill = fill;

    if (isExisting) {
      ucar.unidata.io.RandomAccessFile raf = new ucar.unidata.io.RandomAccessFile(location, "rw");
      spi = SPFactory.getServiceProvider();
      spiw = (IOServiceProviderWriter) spi;
      spiw.open(raf, this, null);
      spiw.setFill( fill);

    } else {
      defineMode = true;
    }
  }

  /**
   * Set the fill flag: call before calling create() or doing any data writing.
   * If true, the data is first written with fill values.
   * Default is fill = false.
   * Leave false if you expect to write all data values, set to true if you want to be
   * sure that unwritten data values have the fill value in it.
   *
   * @param fill set fill mode true or false
   */
  public void setFill(boolean fill) {
    this.fill = fill;
    if (spiw != null)
      spiw.setFill( fill);
  }

  /**
   * Preallocate the file size, for efficiency.
   * Must be in define mode
   * Must call before create() to have any affect.
   * @param size if set to > 0, set length of file to this upon creation - this (usually) pre-allocates contiguous storage.
   */
  public void setLength(long size) {
    if (!defineMode) throw new UnsupportedOperationException("not in define mode");
    this.preallocateSize = size;
  }


  /**
   * Set if this should be a "large file" (64-bit offset) format.
   * Must be in define mode
   * @param isLargeFile true if large file
   */
  public void setLargeFile(boolean isLargeFile) {
    if (!defineMode) throw new UnsupportedOperationException("not in define mode");
    this.isLargeFile = isLargeFile;
  }

  /**
   * Set extra bytes to reserve in the header.
   * This can prevent rewriting the entire file on redefine.
   * Must be in define mode
   * @param extraHeaderBytes # bytes extra for the header
   */
  public void setExtraHeaderBytes(int extraHeaderBytes) {
    if (!defineMode) throw new UnsupportedOperationException("not in define mode");
    this.extraHeader = extraHeaderBytes;
  }

  /**
   * Is the file in define mode, which allows objects to be added and changed?
   * @return true if the file in define mode
   */
  public boolean isDefineMode() { return defineMode; }

  ////////////////////////////////////////////
  //// use these calls in define mode

  /**
   * Add a Dimension to the file. Must be in define mode.
   *
   * @param dimName name of dimension
   * @param length  size of dimension.
   * @return the created dimension
   */
  public Dimension addDimension(String dimName, int length) {
    if (!defineMode) throw new UnsupportedOperationException("not in define mode");
    if (length <= 0) throw new IllegalArgumentException("length must be > 0");
    if (!N3iosp.isValidNetcdf3ObjectName(dimName)) throw new IllegalArgumentException("illegal netCDF-3 object name");
    Dimension dim = new Dimension(dimName, length, true, false, false);
    super.addDimension(null, dim);
    return dim;
  }

  /**
   * Add a Dimension to the file. Must be in define mode.
   *
   * @param dimName          name of dimension
   * @param length           size of dimension.
   * @param isShared         if dimension is shared
   * @param isUnlimited      if dimension is unlimited
   * @param isVariableLength if dimension is variable length
   * @return the created dimension
   */
  public Dimension addDimension(String dimName, int length, boolean isShared, boolean isUnlimited, boolean isVariableLength) {
    if (!defineMode) throw new UnsupportedOperationException("not in define mode");
    if (!N3iosp.isValidNetcdf3ObjectName(dimName)) throw new IllegalArgumentException("illegal netCDF-3 object name");
    Dimension dim = new Dimension(dimName, length, isShared, isUnlimited, isVariableLength);
    super.addDimension(null, dim);
    return dim;
  }

  /**
   * Add an unlimited Dimension to the file. Must be in define mode.
   *
   * @param dimName name of unlimited dimension
   * @return the created dimension
   */
  public Dimension addUnlimitedDimension(String dimName) {
    return addDimension(dimName, 0, true, true, false);
  }

  /**
   * Rename a Dimension. Must be in define mode.
   * @param oldName existing dimension has this name
   * @param newName rename to this
   * @return renamed dimension, or null if not found
   */
  public Dimension renameDimension(String oldName, String newName) {
    if (!defineMode) throw new UnsupportedOperationException("not in define mode");
    Dimension dim = findDimension(oldName);
    if (null != dim) dim.setName(newName);
    return dim;
  }

  /**
   * Add a Global attribute to the file. Must be in define mode.
   * @param att the attribute.
   * @return the created attribute
   */
  public Attribute addGlobalAttribute(Attribute att) {
    if (!defineMode) throw new UnsupportedOperationException("not in define mode");
    return super.addAttribute(null, att);
  }

  /**
   * Add a Global attribute of type String to the file. Must be in define mode.
   *
   * @param name  name of attribute.
   * @param value value of atribute.
   * @return the created attribute
   */
  public Attribute addGlobalAttribute(String name, String value) {
    if (!defineMode) throw new UnsupportedOperationException("not in define mode");
    return super.addAttribute(null, new Attribute(name, value));
  }

  /**
   * Add a Global attribute of type Number to the file. Must be in define mode.
   *
   * @param name  name of attribute.
   * @param value must be of type Float, Double, Integer, Short or Byte
   * @return the created attribute
   */
  public Attribute addGlobalAttribute(String name, Number value) {
    if (!defineMode) throw new UnsupportedOperationException("not in define mode");
    return super.addAttribute(null, new Attribute(name, value));
  }

  /**
   * Add a Global attribute of type Array to the file. Must be in define mode.
   *
   * @param name   name of attribute.
   * @param values Array of values
   * @return the created attribute
   */
  public Attribute addGlobalAttribute(String name, Array values) {
    if (!defineMode) throw new UnsupportedOperationException("not in define mode");
    return super.addAttribute(null, new Attribute(name, values));
  }

  /**
   * Delete a global Attribute. Must be in define mode.
   * @param attName existing Attribute has this name
   * @return deleted Attribute, or null if not found
   */
  public Attribute deleteGlobalAttribute(String attName) {
    if (!defineMode) throw new UnsupportedOperationException("not in define mode");
    Attribute att = findGlobalAttribute(attName);
    if (null == att) return null;

    rootGroup.remove(att);
    return att;
  }

  /**
   * Rename a global Attribute. Must be in define mode.
   * @param oldName existing Attribute has this name
   * @param newName rename to this
   * @return renamed Attribute, or null if not found
   */
  public Attribute renameGlobalAttribute(String oldName, String newName) {
    if (!defineMode) throw new UnsupportedOperationException("not in define mode");
    Attribute att = findGlobalAttribute(oldName);
    if (null == att) return null;

    rootGroup.remove(att);
    att = new Attribute( newName, att.getValues());
    rootGroup.addAttribute( att);
    return att;
  }

  /**
   * Add a variable to the file. Must be in define mode.
   *
   * @param varName  name of Variable, must be unique with the file.
   * @param dataType type of underlying element
   * @param dims     array of Dimensions for the variable, must already have been added. Use an array of length 0
   *                 for a scalar variable.
   * @return the Variable that has been added
   */
  public Variable addVariable(String varName, DataType dataType, Dimension[] dims) {
    ArrayList<Dimension> list = new ArrayList<Dimension>();
    list.addAll(Arrays.asList(dims));
    return addVariable(varName, dataType, list);
  }

  /**
   * Add a variable to the file. Must be in define mode.
   *
   * @param varName  name of Variable, must be unique with the file.
   * @param dataType type of underlying element
   * @param dims     names of Dimensions for the variable, blank seperated.
   *                 Must already have been added. Use an empty string for a scalar variable.
   * @return the Variable that has been added
   */
  public Variable addVariable(String varName, DataType dataType, String dims) {
    // parse the list
    ArrayList<Dimension> list = new ArrayList<Dimension>();
    StringTokenizer stoker = new StringTokenizer(dims);
    while (stoker.hasMoreTokens()) {
      String tok = stoker.nextToken();
      Dimension d = rootGroup.findDimension(tok);
      if (null == d)
        throw new IllegalArgumentException("Canat find dimension " + tok);
      list.add(d);
    }

    return addVariable(varName, dataType, list);
  }

  /**
   * Add a variable to the file. Must be in define mode.
   *
   * @param varName  name of Variable, must be unique with the file.
   * @param dataType type of underlying element
   * @param dims     list of Dimensions for the variable, must already have been added. Use a list of length 0
   *                 for a scalar variable.
   * @return the Variable that has been added
   */
  public Variable addVariable(String varName, DataType dataType, List<Dimension> dims) {
    if (!defineMode)
      throw new UnsupportedOperationException("not in define mode");
    if (!N3iosp.isValidNetcdf3ObjectName(varName)) throw new IllegalArgumentException("illegal netCDF-3 object name");

    Variable v = new Variable(this, rootGroup, null, varName);
    v.setDataType(dataType);
    v.setDimensions(dims);

    super.addVariable(null, v);
    return v;
  }

  /**
   * Add a variable with DataType = String to the file. Must be in define mode.
   * The variable will be stored in the file as a CHAR variable.
   * A new dimension with name "varName_strlen" is automatically added, with length max_strlen.
   *
   * @param varName    name of Variable, must be unique within the file.
   * @param dims       list of Dimensions for the variable, must already have been added. Use a list of length 0
   *                   for a scalar variable. Do not include the string length dimension.
   * @param max_strlen maximum string length.
   * @return the Variable that has been added
   */
  public Variable addStringVariable(String varName, List<Dimension> dims, int max_strlen) {
    if (!defineMode)
      throw new UnsupportedOperationException("not in define mode");
    if (!N3iosp.isValidNetcdf3ObjectName(varName)) throw new IllegalArgumentException("illegal netCDF-3 object name");

    Variable v = new Variable(this, rootGroup, null, varName);
    v.setDataType(DataType.CHAR);

    Dimension d = addDimension(varName + "_strlen", max_strlen);
    ArrayList<Dimension> sdims = new ArrayList<Dimension>(dims);
    sdims.add(d);
    v.setDimensions(sdims);

    super.addVariable(null, v);
    return v;
  }

  /**
   * Rename a Variable. Must be in define mode.
   * @param oldName existing Variable has this name
   * @param newName rename to this
   * @return renamed Variable, or null if not found
   */
  public Variable renameVariable(String oldName, String newName) {
    if (!defineMode) throw new UnsupportedOperationException("not in define mode");
    Variable v = findVariable(oldName);
    if (null != v) v.setName(newName);
    return v;
  }

  /**
   * Add an attribute to the named Variable. Must be in define mode.
   *
   * @param varName name of variable. must already have been added to the file.
   * @param att     Attribute to add.
   */
  public void addVariableAttribute(String varName, Attribute att) {
    if (!defineMode)
      throw new UnsupportedOperationException("not in define mode");
    if (!N3iosp.isValidNetcdf3ObjectName(att.getName())) throw new IllegalArgumentException("illegal netCDF-3 object name");

    Variable v = rootGroup.findVariable(varName);
    if (null == v)
      throw new IllegalArgumentException("addVariableAttribute variable name not found = <" + varName + ">");
    v.addAttribute(att);
  }

  /**
   * Add an attribute of type String to the named Variable. Must be in define mode.
   *
   * @param varName name of variable. must already have been added to the file.
   * @param attName name of attribute.
   * @param value   String value of atribute.
   */
  public void addVariableAttribute(String varName, String attName, String value) {
    addVariableAttribute(varName, new Attribute(attName, value));
  }


  /**
   * Add an attribute of type Number to the named Variable. Must be in define mode.
   *
   * @param varName name of variable. IllegalArgumentException if not valid name.
   * @param attName name of attribute.
   * @param value   must be of type Float, Double, Integer, Short or Byte
   */
  public void addVariableAttribute(String varName, String attName, Number value) {
    addVariableAttribute(varName, new Attribute(attName, value));
  }

  /**
   * Add an attribute of type Array to the named Variable. Must be in define mode.
   *
   * @param varName name of variable. IllegalArgumentException if not valid name.
   * @param attName name of attribute.
   * @param value   Array of valkues
   */
  public void addVariableAttribute(String varName, String attName, Array value) {
    Attribute att = new Attribute(attName, value);
    addVariableAttribute(varName, att);
  }

  /**
   * Delete a variable Attribute. Must be in define mode.
   * @param varName existing Variable name
   * @param attName existing Attribute has this name
   * @return deleted Attribute, or null if not found
   */
  public Attribute deleteVariableAttribute(String varName, String attName) {
    if (!defineMode) throw new UnsupportedOperationException("not in define mode");
    Variable v = findVariable(varName);
    if (v == null) return null;

    Attribute att = v.findAttribute(attName);
    if (null == att) return null;

    v.remove(att);
    return att;
  }


  /**
   * Rename a variable Attribute. Must be in define mode.
   * @param varName existing Variable name
   * @param attName existing Attribute has this name
   * @param newName rename to this
   * @return renamed Attribute, or null if not found
   */
  public Attribute renameVariableAttribute(String varName, String attName, String newName) {
    if (!defineMode) throw new UnsupportedOperationException("not in define mode");
    Variable v = findVariable(varName);
    if (v == null) return null;

    Attribute att = v.findAttribute(attName);
    if (null == att) return null;

    v.remove(att);
    att = new Attribute( newName, att.getValues());
    v.addAttribute( att);
    return att;
  }

  /**
   * Update the value of an existing attribute. Attribute is found by name, which must match exactly.
   * You cannot make an attribute longer, or change the number of values.
   * For strings: truncate if longer, zero fill if shorter.  Strings are padded to 4 byte boundaries, ok to use padding if it exists.
   * For numerics: must have same number of values.
   *
   * @param v2  variable, or null for global attribute
   * @param att replace with this value
   * @throws IOException if I/O error
   */
  public void updateAttribute(ucar.nc2.Variable v2, Attribute att) throws IOException {
    if (defineMode)
      throw new UnsupportedOperationException("in define mode");
    spiw.updateAttribute(v2, att);
  }

  /**
   * After you have added all of the Dimensions, Variables, and Attributes,
   *   call create() to actually create the file. You must be in define mode.
   * After this call, you are no longer in define mode.
   * @throws java.io.IOException if I/O error
   */
  public void create() throws java.io.IOException {
    if (!defineMode)
      throw new UnsupportedOperationException("not in define mode");

    spi = SPFactory.getServiceProvider();
    spiw = (IOServiceProviderWriter) spi;
    spiw.create(location, this, extraHeader, preallocateSize, isLargeFile);
    spiw.setFill( fill);

    defineMode = false;
  }

  ////////////////////////////////////////////
  // redefine

  /**
   * Set the redefine mode.
   * Designed to emulate nc_redef (redefineMode = true) and
   * nc_enddef (redefineMode = false)
   * @param redefineMode start or end define mode
   * @return true if it had to rewrite the entire file, false if it wrote the header in place
   * @throws java.io.IOException on read/write error
   */
  public boolean setRedefineMode(boolean redefineMode) throws IOException {
    if (redefineMode && !defineMode) {
      defineMode = true;

    } else if (!redefineMode && defineMode) {
      defineMode = false;
      finish();

      // try to rewrite header, if it fails, then we have to rewrite entire file
      boolean ok = spiw.rewriteHeader( isLargeFile);
      if (!ok)
        rewrite();
      return !ok;
    }
    return false;
  }

  // for now, assume we have to rewrite
  // should check if we can keep header (eg attributes only added)
  private void rewrite() throws IOException {
    // close existing file, rename and open as read-only
    close();
    File prevFile = new File(location);
    File tmpFile = new File(location+".tmp");
    if (tmpFile.exists()) tmpFile.delete();
    if (!prevFile.renameTo(tmpFile)) {
      System.out.println("prevFile.exists "+prevFile.exists()+" canRead = "+ prevFile.canRead());
      System.out.println("tmpFile.exists "+tmpFile.exists()+" canWrite "+ tmpFile.canWrite());
      throw new RuntimeException("Cant rename "+prevFile.getAbsolutePath()+" to "+ tmpFile.getAbsolutePath());
    }

    NetcdfFile oldFile = NetcdfFile.open(tmpFile.getPath());

    // use record dimension if it has one
    Structure recordVar = null;
    if (oldFile.hasUnlimitedDimension()) {
      oldFile.sendIospMessage(NetcdfFile.IOSP_MESSAGE_ADD_RECORD_STRUCTURE);
      recordVar = (Structure) oldFile.findVariable("record");
    }

    // create new file with current set of objects
    spiw.create(location, this, extraHeader, preallocateSize, isLargeFile);
    spiw.setFill( fill);
    isClosed = false;

    // copy old file to new
    List<Variable> oldList = new ArrayList<Variable>(getVariables().size());
    for (Variable v : getVariables()) {
      Variable oldVar = oldFile.findVariable(v.getName());
      if (oldVar != null)
        oldList.add(oldVar);
    }
    FileWriter.copyVarData(this, oldList, recordVar, 0);
    flush();

    // delete old
    oldFile.close();
    if (!tmpFile.delete())
      throw new RuntimeException("Cant delete "+location);
  }

  ////////////////////////////////////////////
  //// use these calls to write data to the file

  /**
   * Write data to the named variable, origin assumed to be 0. Must not be in define mode.
   *
   * @param varName name of variable. IllegalArgumentException if variable name does not exist.
   * @param values  write this array; must be same type and rank as Variable
   * @throws IOException if I/O error
   * @throws ucar.ma2.InvalidRangeException if values Array has illegal shape
   */
  public void write(String varName, Array values) throws java.io.IOException, InvalidRangeException {
    write(varName, new int[values.getRank()], values);
  }

  /**
   * Write data to the named variable. Must not be in define mode.
   *
   * @param varName name of variable. IllegalArgumentException if variable name does not exist.
   * @param origin  offset within the variable to start writing.
   * @param values  write this array; must be same type and rank as Variable
   * @throws IOException if I/O error
   * @throws ucar.ma2.InvalidRangeException if values Array has illegal shape
   */
  public void write(String varName, int[] origin, Array values) throws java.io.IOException, InvalidRangeException {
    if (defineMode)
      throw new UnsupportedOperationException("in define mode");
    ucar.nc2.Variable v2 = findVariable(varName);
    if (v2 == null)
      throw new IllegalArgumentException("NetcdfFileWriteable.write illegal variable name = " + varName);

    spiw.writeData(v2, new Section(origin, values.getShape()), values);
    v2.invalidateCache();
  }

  /**
   * Write String data to a CHAR variable, origin assumed to be 0. Must not be in define mode.
   *
   * @param varName name of variable, must be of type CHAR.
   * @param values  write this array; must be ArrayObject of String
   * @throws IOException if I/O error
   * @throws ucar.ma2.InvalidRangeException if values Array has illegal shape
   */
  public void writeStringData(String varName, Array values) throws java.io.IOException, InvalidRangeException {
    writeStringData(varName, new int[values.getRank()], values);
  }


  /**
   * Write String data to a CHAR variable. Must not be in define mode.
   *
   * @param varName name of variable, must be of type CHAR.
   * @param origin  offset to start writing, ignore the strlen dimension.
   * @param values  write this array; must be ArrayObject of String
   * @throws IOException if I/O error
   * @throws ucar.ma2.InvalidRangeException if values Array has illegal shape
   */
  public void writeStringData(String varName, int[] origin, Array values) throws java.io.IOException, InvalidRangeException {

    if (values.getElementType() != String.class)
      throw new IllegalArgumentException("Must be ArrayObject of String ");

    ucar.nc2.Variable v2 = findVariable(varName);
    if (v2 == null)
      throw new IllegalArgumentException("illegal variable name = " + varName);

    if (v2.getDataType() != DataType.CHAR)
      throw new IllegalArgumentException("variable " + varName + " is not type CHAR");
    int rank = v2.getRank();
    int strlen = v2.shape[rank-1];

    // turn it into an ArrayChar
    ArrayChar cvalues = ArrayChar.makeFromStringArray((ArrayObject) values, strlen);

    int[] corigin = new int[rank];
    System.arraycopy(origin, 0, corigin, 0, rank - 1);

    write(varName, corigin, cvalues);
  }

  /**
   * Flush anything written to disk.
   * @throws IOException if I/O error
   */
  public void flush() throws java.io.IOException {
    spiw.flush();
  }

  /**
   * close the file.
   * @throws IOException if I/O error
   */
  public synchronized void close() throws java.io.IOException {
    flush();
    spiw.close();
    isClosed = true;
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // deprecated

  /**
   * Create a new Netcdf file, put it into define mode.
   *
   * @param location open a new file at this location
   * @param fill set fill mode
   * @deprecated use createNew(String filename, boolean fill)
   */
  public NetcdfFileWriteable(String location, boolean fill) {
    super();
    this.location = location;
    this.fill = fill;
    defineMode = true;
  }

  /**
   * Open a new Netcdf file, put it into define mode.
   *
   * @deprecated use createNew(String filename, boolean fill)
   */
  public NetcdfFileWriteable() {
    super();
    defineMode = true;
  }

  /**
   * Open an existing Netcdf file for writing data.
   *
   * @param location open an existing file at this location
   * @deprecated use openExisting(String filename, boolean fill)
   * @throws java.io.IOException on read error
   */
  public NetcdfFileWriteable(String location) throws IOException {
    super();
    this.location = location;
    ucar.unidata.io.RandomAccessFile raf = new ucar.unidata.io.RandomAccessFile(location, "rw");
    spi = SPFactory.getServiceProvider();
    spiw = (IOServiceProviderWriter) spi;
    spiw.open(raf, this, null);
  }

  /**
   * Set the filename of a new file to be created: call before calling create().
   * @param filename name of new file to create.
   * @deprecated use NetcdfFileWriteable.createNew(String filename);
   */
  public void setName(String filename) {
    this.location = filename;
  }

  /**
   * Add a variable to the file. Must be in define mode.
   *
   * @param varName       name of Variable, must be unique with the file.
   * @param componentType type of underlying element: String, double or Double, etc.
   * @param dims          array of Dimensions for the variable, must already have been added.
   * @deprecated use addVariable(String varName, DataType dataType, ArrayList dims);
   * @return the Varible added
   */
  public Variable addVariable(String varName, Class componentType, Dimension[] dims) {
    List<Dimension> list = new ArrayList<Dimension>();
    list.addAll(Arrays.asList(dims));
    return addVariable(varName, DataType.getType(componentType), list);
  }

}