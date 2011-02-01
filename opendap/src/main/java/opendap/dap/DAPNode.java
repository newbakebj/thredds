/////////////////////////////////////////////////////////////////////////////
// This file is part of the "Java-DAP" project, a Java implementation
// of the OPeNDAP Data Access Protocol.
//
// Copyright (c) 2010, OPeNDAP, Inc.
// Copyright (c) 2002,2003 OPeNDAP, Inc.
// 
// Author: James Gallagher <jgallagher@opendap.org>
// 
// All rights reserved.
// 
// Redistribution and use in source and binary forms,
// with or without modification, are permitted provided
// that the following conditions are met:
// 
// - Redistributions of source code must retain the above copyright
//   notice, this list of conditions and the following disclaimer.
// 
// - Redistributions in binary form must reproduce the above copyright
//   notice, this list of conditions and the following disclaimer in the
//   documentation and/or other materials provided with the distribution.
// 
// - Neither the name of the OPeNDAP nor the names of its contributors may
//   be used to endorse or promote products derived from this software
//   without specific prior written permission.
// 
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
// IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
// TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
// PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
// PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
// LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
// NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
/////////////////////////////////////////////////////////////////////////////


package opendap.dap;

import opendap.dap.Server.ServerMethods;
import opendap.util.EscapeStrings;

import java.io.Serializable;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * The DAPNode class is the common parent type for
 * all nodes in the DDS and the DAS. It is used to manage
 * the following elements.
 * 1. Names - both encoded and clear
 * 2. Cloning - it implements the single clone procedure
 *    and converts it to calls to cloneDAG.
 * 3. Parent - this was moved here from BaseType
 *    because it (should) represent the only cyclic pointer
 *    in the tree.
 * 4. projection - this really only for server nodes.
 *    it should be removed when we quit using cloning.
 *
 * @author dmh (Dennis Heimbigner, Unidata)
 * @version $Revision: 22951 $
 */

public class DAPNode implements Cloneable, Serializable
{
    
    static final long serialVersionUID = 1;

    // Define a singleton value for which we can test with ==
    static DAPNode NULLNODE = new DAPNode("null",false);


    /**
     * The name of this variable - not www enccoded
     */
    protected String _name;
    protected String _nameEncoded; // www encoded form

    /**
     * The parent (container class) of this object, if one exists
     */
    private DAPNode _myParent;

    /**
     * Track if this variable is being used as part of a projection.
     */
    private boolean projected = false;

    /**
      * CEEValuator.markstackedvariables()
      * automatically marks all fields of a constructor as projected.
      * This causes problems with printDecl, which automatically recurses
      * on it fields. This means that constructor fields end up printed twice.
      * The original code, with the bad cloning, did not do this for some reason.
      * The solution taken here is mark which nodes were marked by constructor
      * recursion and which were marked directly. This info is then used in
      * printDecl to properly print the fields once and only once.
      */
    private boolean ctorProjected = false;
       
    /**
     * The Attribute Table used to contain attributes specific to this
     * instance of a BaseType variable. This is the repository for
     * "Semantic Metadata"
     */
    private Attribute _attr;
    private AttributeTable _attrTbl;

    /**
     * Constructs a new <code>DAPNode</code> with no name.
     */
    public DAPNode() {
        this(null);
    }

    /**
     * Constructs a new <code>DAPNode</code> with name <code>n</code>.
     *
     * @param n the name of the variable.
     */
    public DAPNode(String n) {
        this(n, true);
    }

    /**
     * Constructs a new <code>DAPNode</code> with name <code>n</code>.
     *
     * @param n the name of the variable.
     * @param decodeName true if the name is www encoded
     */
    public DAPNode(String n, boolean decodeName)
    {
        _myParent = null;
        if (decodeName)
            _name = EscapeStrings.www2id(n);
        else
            _name = n;
        _nameEncoded = EscapeStrings.www2id(_name);
    }

    public void setProjected(boolean tf)
    {
        projected = tf;
    }


    /**
     * Check the projection state of this variable.
     * Is the given variable marked as projected? If the variable is listed
     * in the projection part of a constraint expression, then the CE parser
     * should mark it as <em>projected</em>. When this method is called on
     * such a variable it should return <code>true</code>, otherwise it
     * should return <code>false</code>.
     *
     * @return <code>true</code> if the variable is part of the current
     *         projections, <code>false</code> otherwise.
     * @see opendap.dap.Server.CEEvaluator
     */
    public boolean isProject() {
        return (projected);
    }

     /**
      * Set the state of this variable's projection. <code>true</code> means
      * that this variable is part of the current projection as defined by
      * the current constraint expression, otherwise the current projection
      * for this variable should be <code>false</code>.
      *
      * @param state <code>true</code> if the variable is part of the current
      *              projection, <code>false</code> otherwise.
      * @param all   If <code>true</code>, set the Project property of all the
      *              members (and their children, and so on).
      * @see opendap.dap.Server.CEEvaluator
      */
     public void setProject(boolean state, boolean all) {
         setProjected(state);
     }

     /**
      * Set the state of this variable's projection. <code>true</code> means
      * that this variable is part of the current projection as defined by
      * the current constraint expression, otherwise the current projection
      * for this variable should be <code>false</code>. <p>
      * This is equivalent to setProjection(<code>state</code>,
      * <code>true</code>).
      *
      * @param state <code>true</code> if the variable is part of the current
      *              projection, <code>false</code> otherwise.
      * @see opendap.dap.Server.CEEvaluator
      */
     public void setProject(boolean state) {
         setProject(state, true);
     }


    /**
     * Access the ctorProjected field.
     *
     * @return <code>true</code> if the variable was projected
     * as a result of constructor recursions
     * <code>false</code> otherwise.
     * @see opendap.dap.Server.CEEvaluator
     */
    public boolean isCtorProjected() {
        return (ctorProjected);
    }

     /**
      * Set the state of this variable's ctorProjected fiel
      *
      * @param state <code>true</code> if the variable was recursively projected,
      *              <code>false</code> otherwise.
      * @see opendap.dap.Server.CEEvaluator
      */
     public void setCtorProjected(boolean state) {
         ctorProjected = (state);
     }


    public void setParent(DAPNode bt) {
        _myParent = bt;
    }

    public DAPNode getParent() {
        return (_myParent);
    }

    /**
     * Returns the unencoded name of the class instance.
     *
     * @return the name of the class instance.
     */
    public final String getClearName() {
        return _name;
    }

    /**
     * Returns the WWW encoded name of the class instance.
     *
     * @return the name of the class instance.
     */
    public final String getName() {
        return _nameEncoded;
    }

    /**
     * Sets the name of the class instance.
     *
     * @param n the name of the class instance.
     */
    public final void setName(String n)
    {
	_nameEncoded = n;
        setClearName(EscapeStrings.www2id(n));
    }

    /**
     * Sets the unencoded name of the class instance.
     *
     * @param n the unencoded name of the class instance.
     */
    public  void setClearName(String n) {
        _name = n;
        _nameEncoded = EscapeStrings.id2www(n);
        if(_attr != null) _attr.setClearName(n);
        if(_attrTbl !=  null) _attrTbl.setClearName(n);
    }


    /**
     *  Clone interface. Note that in order to do this properly,
     *  we need to be prepared to clone a DAG rather than just
     *  a tree. This means we need two additional procedures: cloneDAG(CloneMap, DAPNode)
     *  and cloneDAG(CloneMap).   These functions carry along a map of already cloned
     *  nodes in order to avoid re-cloning.
     */

    // Define a class for holding all the clone mapping information.
   // Members are kept public for direct access.
   static public class CloneMap
   {
       Map<DAPNode,DAPNode> nodes = new HashMap<DAPNode,DAPNode>(); // map base object to clone
       DAPNode root = null; // initial cloned object
   }

        /**
     * Returns a clone of this <code>DAPNode</code>.  A deep copy is performed
     * on all data inside the variable.
     *
     * @return a clone of this <code>DAPNode</code>.
     */

    public Object clone() {
        try {
	    CloneMap map = new CloneMap();
        map.root = this;
	    return cloneDAG(map);
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError();
        }
    }


    /**
     * Returns a clone of this <code>DAPNode</code>.
     * on all data inside the variable. Uses a set of already
     * cloned nodes to avoid re-cloning. All sub classes of DAPNode
     * should implement and invoke cloneDAG, not clone.
     *
     * @param map The set of already cloned nodes.
     * @return a clone of this <code>DAPNode</code>.
     */

    /**
     *  This version of cloneDAG() is the primary
     *  point of cloning. If the src is already cloned,
     *  then that existing clone is immediately returned.
     *  Otherwise cloneDAG(map) is called to have the object
     *  clone itself. Note this is static because it uses no
     *  existing state.
     * @param map list of previously cloned nodes
     * @return  the clone of the src node
     * @throws CloneNotSupportedException
     */
    static public DAPNode cloneDAG(CloneMap map, DAPNode src)
        throws CloneNotSupportedException
    {
        DAPNode bt = map.nodes.get(src);
        if(bt == null)
            bt = src.cloneDAG(map);
        return bt;
    }

    /**
     * This procedure does the actual recursive clone.
     * @param map  list of previously cloned nodes
     * @return  clone of this node
     * @throws CloneNotSupportedException
     */
    public DAPNode cloneDAG(CloneMap map)
        throws CloneNotSupportedException
    {
        DAPNode node = (DAPNode)super.clone(); // Object.clone
	    map.nodes.put(this,node);
        if(_myParent != null) {
            if(_myParent != map.root) // make sure we do not clone above initial starting point
                node._myParent = cloneDAG(map,_myParent);
        }
        return node;
    }

}