/*
 *    Problem.java
 *    Author: Jay Modi
 *    Date: Aug 28 2001
 *
 *    Specifies a DCO Problem.
 *    Methods for computing local cost (delta), creating different
 *    priority orderings. 
 */

package adopt.problem;
import adopt.common.*;

import java.io.*;
import java.util.*;
import java.net.*;

public abstract class Problem {

  /*** Class variables ***/
  
  String problemName;
  /* list of variables */
  public Vector vars;
  /* list of agents names*/
  public Vector agents;

  /* Represents the maximum cost possible */ 
  public static int MAX_VALUE = 10000;
  /* 0: linear ordering using heuristic.
     1: tree ordering using heuristic.
     2: linear ordering based on depth-first traversal of tree from [1].
     3: random linear ordering. 
     */
  int _orderSwitch;
  public static int CHAIN = 0;
  public static int TREE = 1;
  public static int DFSCHAIN = 2;
  public static int RANDOMCHAIN = 3;
  /* Chain priority ordering
     -----------------------
     pOrderChain[i] holds the priority of variable i (var.varID == i) 
     Note: varIDs must be [0...n]
     if pOrderChain[i] < pOrderChain[j], then j is higher priority than i
     */ 
  int[] pOrderChain = null;
  /* Tree priority ordering 
     ----------------------
     pOrder.getCost() holds the priority of pOrder.root. 
   */
  PriorityTree pOrderTree = null;
  /*** End class variables ***/

  /*** Class Methods ***/

  /* Constructor */
  public Problem(String name){
    problemName = name;
    vars = new Vector();
    agents = new Vector();
  }
  /** Abstract Methods **/
  /* evaluate unary constraints */
  public abstract int evaluate(Variable v1, Value val1);
  /* evaluate binary constraints */
  public abstract int evaluate(Variable v1, Value val1, Variable v2, Value val2);
  /* evaluate n-ary constraints */
  public abstract int evaluate(Variable v1, Value val1, Context vw);
  /* return true if priority(v1) > priority(v2), false otherwise */
  public abstract boolean comparePriority(Variable v1,Variable v2);
  /** End abstract methods **/

  /** Static Methods **/
  /* returns a negative integer, zero, or a positive integer as d1
     is less than, equal to, or greater d2.*/
  public static int compareDeltas(int d1, int d2){
    
    if(d1 == Problem.MAX_VALUE &&
       d2 != Problem.MAX_VALUE)
      /* d1 > d2 */
      return 1;
    else if(d1 != Problem.MAX_VALUE &&
	    d2 == Problem.MAX_VALUE)
      /* d2 < d1 */
      return -1;
    else if(d1 == d2)
      return 0;
    else{
      if(d1 > d2)
	return 1;
      else  /* d2 > d1 */
	return -1;
    }
  }

  /* return d1 - d2 */ 
  public static int subDeltas(int d1, int d2){
    /* inf - inf = 0 */
    if(d1 == Problem.MAX_VALUE &&
       d2 == Problem.MAX_VALUE)
      return 0;
    /* inf - n = inf */
    if(d1 == Problem.MAX_VALUE ||
       d2 == Problem.MAX_VALUE)
      return Problem.MAX_VALUE;
    else
      return d1 - d2;
  }

  /* return d1 + d2 */ 
  public static int sumDeltas(int d1, int d2){
    if(d1 == Problem.MAX_VALUE ||
       d2 == Problem.MAX_VALUE)
      return Problem.MAX_VALUE;
    else
      return d1+d2;
  }
  /** End static methods **/


  /* DELTA(): Computes local cost delta(v1) with respect to all
     variables in given context */
  public int delta(Variable v1, Context vw){
    /*debug*/Utility.Dprint("Entering Problem.delta():....", Utility.TRACE_RIDICULOUS_LEVEL);      
    int delta = 0;
    /*debug*/    Utility.Dprint("    Variable: " + v1.uniqueNameOf(), Utility.TRACE_RIDICULOUS_LEVEL);
    /*debug*/    Utility.Dprint("    Context: " + vw.toString(), Utility.TRACE_RIDICULOUS_LEVEL);      
    Value val1 = vw.valueOf(v1);
    if(val1 == null){
      Utility.Dprint("Problem.delta(): Unknown variable", Utility.DEBUG);      
      System.exit(0);
    }
    /* evaluate unary constraint*/
    delta = sumDeltas(delta, evaluate(v1,val1));
    
    /* evaluate binary constraints*/
    Variable v2;
    for(int i=0;i<vars.size();i++){
      v2 = (Variable) vars.elementAt(i);
      Value val2 = vw.valueOf(v2);
      if(val2 != null){
	if(connected(v1, v2)){
	  int rc = evaluate(v1, val1, v2, val2);
	  delta = sumDeltas(delta, rc);
	}
      }
    }
    /* evaluate nary constraints */
    delta = sumDeltas(delta, evaluate(v1,val1, vw));
    /*debug*/    Utility.Dprint("...leaving Problem.delta() " + delta, 
				Utility.TRACE_RIDICULOUS_LEVEL);      
    return delta;
  }


  /* set agents field */ 
  public void setAgents(Vector agnts){
    agents = agnts;
  }

  /*** Accessor methods ***/
  public int numAgents(){
    return agents.size();
  }
  public int numVars(){
    return vars.size();
  }
  /* return variable in this problem with given uniqueName */
  public Variable getVariableFromUniqueVarName(String uname){

    for(int i=0;i<vars.size();i++) {
      Variable v2 = (Variable) vars.elementAt(i);
      if(v2.isUniqueNameOf(uname))
	return v2;
    }
    
    /* debug */ Utility.Dprint("Problem.getVariableFromUniqueVarName(): Unknown variable: " + uname,
			       Utility.TRACE_RIDICULOUS_LEVEL);      
    return null;
  }
  /* are the given variables connected by a constraint? */
  public boolean connected(Variable q1, Variable q2){
    return (q1.isNeighbor(q2) && q2.isNeighbor(q1));
  }
  /* return the variables that are connected to the given one*/
  public Vector getLinks(Variable v){
    Utility.Dprint("Entering getLinks()...", Utility.TRACE_RIDICULOUS_LEVEL);      
    Utility.Dprint(" v = " + v.uniqueNameOf(), Utility.TRACE_RIDICULOUS_LEVEL);      
    Vector result = new Vector();
    /* find neighboring variables */
    for(int j=0;j<vars.size();j++){
      Variable v2 = (Variable) vars.elementAt(j);
      if(connected(v,v2)){
	/* record the variable */
	result.add(v2);
      }
    }
    for(int i=0;i<result.size();i++){
      Variable v2 = (Variable) result.elementAt(i);
      Utility.Dprint(" v2 = " + v2.uniqueNameOf(), Utility.TRACE_RIDICULOUS_LEVEL);      
    }
    Utility.Dprint("...leaving getLinks()", Utility.TRACE_RIDICULOUS_LEVEL);      
    return result;
  }
  /* return variable in this problem with given varID */
  public Variable getVariableFromID(int v1){
    for(int j=0;j<vars.size();j++){
      Variable v = (Variable) vars.elementAt(j);
      if(v.varID == v1){
        return v;
      }
    }
    return null;
  }
  /* return variables owned to given agent */
  public Vector getVariablesFromAgentID(int agentID){
    /* debug */ Utility.Dprint("Entering getVariablesFromAgentID("+agentID+")...", 
			       Utility.TRACE_RIDICULOUS_LEVEL);      
    /* variables owned by given agent */
    Vector myVars = new Vector();
    for(int i=0;i<vars.size();i++){
      Variable v = (Variable) vars.elementAt(i);
      if(v.agentIDof() == agentID)
	myVars.add(v);
    }
    /* debug */ Utility.Dprint("...leaving getVariablesFromAgentName()  "+myVars.size(), 
			       Utility.TRACE_RIDICULOUS_LEVEL);      
    return myVars;
  }
  /* return all children of given variable */
  public Vector getChildrenVar(Variable v){
    Vector result = new Vector();
    if(isChainOrder()){
      Variable vv = nextVariable(v);
      if(vv != null)
	result.add(vv);
    }
    else{
      PriorityTree t = pOrderTree.getSubTree(v);
      if(t != null){
	for(int i=0;i<t.children.size();i++){
	  PriorityTree st = (PriorityTree) t.children.elementAt(i);
	  Variable vv = st.getRootVariable();
	  result.add(vv);
	}
      }
    }
    return result;
  }
  /*** End accessor methods ***/


  /*** Methods for creating/accessing variable ordering ***/
  public boolean isTreeOrder(){
    if(_orderSwitch == TREE){
      return true;
    }
    return false;
  }
  public boolean isChainOrder(){
    if(_orderSwitch == CHAIN ||
       _orderSwitch == DFSCHAIN ||
       _orderSwitch == RANDOMCHAIN
       ){ return true; }
    else if(_orderSwitch == TREE)
      return false;
    else{
      System.out.println("Error in Problem.isChainOrder()..." +
			 "unknown _orderSwitch!!");
      System.exit(0);
    }
    return false;
  }

  /* if a tree-order, return the depth of the tree */
  public int orderDepth(){
    if(_orderSwitch != TREE){
      System.out.println("Error in Problem.treeDepth()..." +
			 "current order is not a tree!!");
      System.exit(0);
    }
    return pOrderTree.depth();
  }

  /* return the priority number of given variable */
  public int priorityOf(Variable v){
    if(_orderSwitch == TREE){
      return pOrderTree.getCost(v);
    }
    else if(isChainOrder()){
      return pOrderChain[v.varID];
    }
    else{
      System.out.println("Problem(): Unknown _orderSwitch: " + _orderSwitch);
      System.exit(0);
    }
    return -1;
  }
  /* write the priority ordering to a log file */
  public void logPriorityOrder(Logger nLog){

    nLog.printToLogln("\nPriority Order");
    nLog.printToLogln("--------------------");

    if(isTreeOrder()){
      nLog.printToLogln(pOrderTree.toString());
      nLog.printToLogln("depth = " + orderDepth());
    }
    for(int j=0;j<vars.size();j++){
      Variable v1 = (Variable) vars.elementAt(j);
      nLog.printToLogln(v1.uniqueNameOf() + " " + priorityOf(v1));
    }
  }

  /* return the variable that is immediately previous (higher) in
     priority order to the given one -- i.e., its parent.  */
  public Variable previousVariable(Variable v){
    if(_orderSwitch == TREE){
      PriorityTree st = pOrderTree.getParent(v);
      if(st == null)
	return null;
      else{
	Variable result = st.getRootVariable();
	return result;
      }
    }
    else{
      Variable minPrtyVar = null;
      for(int i=0;i<vars.size();i++){
	Variable vvar = (Variable) vars.elementAt(i);
	/* we are trying to find the highest lower priority variable */
	if(comparePriority(vvar, v) && (minPrtyVar == null ||
					comparePriority(minPrtyVar, vvar))){
	  minPrtyVar = vvar;
	}
      }
      return minPrtyVar;
    }
  }

  /* if a chain order, return the variable that is next (lower) in
     priority order to the given one */
  public Variable nextVariable(Variable v){
    if(!isChainOrder()){
      return null;
    }
    Variable maxPrtyVar = null;
    for(int i=0;i<vars.size();i++){
      Variable vvar = (Variable) vars.elementAt(i);
      if(comparePriority(v, vvar) && (maxPrtyVar == null ||
				      comparePriority(vvar, maxPrtyVar))){
	maxPrtyVar = vvar;
      }
    }
    return maxPrtyVar;
  }

  /* Convert a tree-order into a chain-order through a depth-first traversal of the tree */
  public void convertToChain(){
    if(_orderSwitch != TREE){
      System.out.println("Error in Problem.convertToChain()..." +
			 "converting non-tree to chain!!");
      System.exit(0);
    }
    else{
      pOrderChain = convertToChainHelper(pOrderTree);
      _orderSwitch = DFSCHAIN;
      pOrderTree = null;
      Utility.Dprint("  Priorities: ", Utility.MSG_LEVEL2);
      for(int j=0;j<vars.size();j++){
	Variable v1 = (Variable) vars.elementAt(j);
	Utility.Dprint(v1.varID + " " + priorityOf(v1), Utility.MSG_LEVEL2);
      }
    }
  }
  public int[] convertToChainHelper(PriorityTree st){
    int[] map2 = new int[vars.size()];
    int i=vars.size()-1;
    PriorityTree tOrder = st;
    String s = tOrder.toDFSString();
    StringTokenizer stok = new StringTokenizer(s, ",");
    while(stok.hasMoreTokens()){
      String ss = stok.nextToken().trim();
      int varID = (new Integer(ss)).intValue();
      map2[varID] = i--;
    }
    return map2;
  }


  /** Methods for Tree Priority Order **/

  /* create a tree ordering  according to following heuristic:
     choose variable that has the most links with already chosen vars.
     In case of tie, it should have the most links with unchosen vars.
     */
  public PriorityTree priorityTree(){
    /* new tree order */
    PriorityTree order; 
    /* map2 will keep track of already chosen variables */
    int[] map2 = new int[vars.size()];
    for(int i=0;i<vars.size();i++){
      map2[i] = -1;
    }
    Variable root = findNextPriority(null, map2);
    order = new PriorityTree(root, vars.size()-1);
    map2[root.varID] = 1;
    PriorityTree currRoot = order;

    /* while we havent ordered all variables */
    while(order.size() < vars.size() ){
      int q = order.getCost(currRoot.getRootVariable()) - 1;
      Variable v = findNextPriority(currRoot.getRootVariable(), map2);
      /* order as many variables as we can until we hit a leaf*/
      while(v != null){
    	PriorityTree s = new PriorityTree(v, q);
	map2[v.varID] = 1;
	currRoot.addSubTree(s);
	currRoot = s;
	q--;
	v = findNextPriority(currRoot.getRootVariable(), map2);
      }

      /* back up one node before restarting loop */
      currRoot = order.getParent(currRoot.getRootVariable());
      if(currRoot == null &&
	 order.size() != vars.size()){
	root = findNextPriority(null, map2);
	PriorityTree st = new PriorityTree(root, vars.size()-2);
	map2[root.varID] = 1;
	order.addSubTree(st);
	currRoot = st;
      }
    }

    /* error check */
    for(int i=0;i<vars.size();i++){
      Variable v1 = (Variable) vars.elementAt(i);
      for(int j=0;j<vars.size();j++){
	Variable v2 = (Variable) vars.elementAt(j);
	if(connected(v1, v2) &&
	   !order.isDescendent(v1, v2) &&
	   !order.isDescendent(v2, v1)){
	  System.out.println("Error in ordering!!!");
	  System.out.println(v1.varID + " is connected to " + v2.varID 
			     + " but are in different subtrees!");
	}
      }
    }
    return order;
  }
  /* for a tree ordering, is v1 higher priority than v2? */
  public boolean comparePriorityTree(Variable v1,  Variable v2){
    if(pOrderTree.isDescendent(v1,v2))
      return true;
    else
      return false;
  }


  /** Methods for Random Chain priority ordering **/

  /* prioritize variables based on ID */
  public int[] priorityRandom(){
    int[] map = new int[vars.size()];

    /* initialize */
    for(int i=0;i<vars.size();i++){
      map[i] = -1;
    }
    /* current priority to be assigned */
    int p = vars.size()-1;
    /* while there are more vars to be assigned priority */
    while(p >= 0){
      /* nextVar to assign priority to */
      Variable nextVar = null;
      for(int i=0;i<vars.size();i++){
	Variable v = (Variable) vars.elementAt(i);
	/* v has not been assigned a priority yet */
	if(map[v.varID] < 0){
	  /* does v have higher ID than current nextvar? */
	  if(nextVar == null ||
	     compareVarID(v,nextVar)){
	    nextVar = v;
	  }
	}
      }
      /* set priority */
      map[nextVar.varID] = p--;
    }
    return map;
  }
  /* helper function for priorityRandom method */
  private boolean compareVarID(Variable v1, Variable v2){
    if(v1.varID < v2.varID)
      return true;
    /* same varID?, use agent id */
    else if(v1.varID == v2.varID)
      if(v1.agentID < v2.agentID)
	return true;
    return false;
  }

  /** Methods for Chain Ordering **/

  /* prioritize variables according to following heuristic:
     choose a variable as top priority.
     choose next variable that has most links with already chosen 
     variables.
     --in case of tie, choose variable with smallest domain.
     */
  public int[] priorityChain(){
    int[] map = new int[vars.size()];
    /* initialize */
    for(int i=0;i<vars.size();i++){
      map[i] = -1;
    }
    /* current priority to be assigned */
    int p = vars.size()-1;
    /* while there are more vars to be assigned priority */
    while(p >= 0){
      /* We choose a nextVar sucht that it has
	 the most links with already prioritized variables */
      Variable nextVar = findNextPriority(null, map);
      if(nextVar != null)
	/* set priority */
	map[nextVar.varID] = p--;
      else{
	System.out.println("Problem.priorityChain(): ");
	System.out.println("  findNextPriority returned null, but i still have" + 
			   "  variables to prioritize!");
	System.exit(0);
      }
    }
    return map;
  }


  /* for a chain ordering, is v1 higher priority than v2? */
  public boolean comparePriorityChain(Variable v1,  Variable v2){
    if(pOrderChain[v1.varID] > pOrderChain[v2.varID])
      return true;
    if(pOrderChain[v1.varID] == pOrderChain[v2.varID] && v1.varID != v2.varID){
      System.out.println("Error: Equal priorities!! (" + v1.varID + " " + v2.varID+")");
      Utility.Dprint("  Priorities: ", Utility.MSG_LEVEL2);
      for(int i=0;i<vars.size();i++){
	Variable v = (Variable) vars.elementAt(i);
	Utility.Dprint(v.varID + " " + pOrderChain[v.varID], Utility.MSG_LEVEL2);
      }
    }
    return false;
  }


  /** Helper functions for creating priority orderings **/

  /* how many links does the given variable have with unchosen variables? */
  public int numLinksUnChosen(Variable v, int[] l){
    int result = 0;
    for(int i=0;i<vars.size();i++){
      Variable v1 = (Variable) vars.elementAt(i);
      if(connected(v1, v) && l[v1.varID] == -1 )
	result++;
    }
    return result;
  }
  /* how many links does the given variable have with Chosen variables? */
  public int numLinksChosen(Variable v, int[] l){
    int result = 0;
    for(int i=0;i<vars.size();i++){
      Variable v1 = (Variable) vars.elementAt(i);
      if(connected(v1, v) && l[v1.varID] != -1 )
	result++;
    }
    return result;
  }
  /* find a variable t such that l[t.varID] == -1 (it is unchosen), 
     and it has the most links with already chosen vars.
     In case of tie, it should have the most links with unchosen vars.
     the chosen variable must be connected to the given variable v.
     */
  public Variable findNextPriority(Variable v, int[] l){
    Variable nextVar = null;
    for(int i=0;i<vars.size();i++){
      Variable vvar = (Variable) vars.elementAt(i);
      /* is this variable currently unchosen?*/
      if(l[vvar.varID] == -1){
	/* is it connected to v? */
	if(v == null || connected(v,vvar)){
	  if(nextVar == null)
	    nextVar = vvar;
	  else{
	    int vvarNumLinks = numLinksChosen(vvar, l);
	    int nextVarNumLinks = numLinksChosen(nextVar, l);
	    if(vvarNumLinks > nextVarNumLinks)
	      nextVar = vvar;
	    else if(vvarNumLinks == nextVarNumLinks){
	      if(numLinksUnChosen(vvar, l) > numLinksUnChosen(nextVar, l)){
		nextVar = vvar;
	      }
	    }
	  }
	}
      }
    }	    
    return nextVar;
  }

}


