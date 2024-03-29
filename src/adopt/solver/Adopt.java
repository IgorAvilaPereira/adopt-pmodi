/**********************************************************************
 * File Name: Adopt.java
 * Author: Jay Modi (modi@isi.edu)
 * Date: Nov 11 2002
 *
 * Implements the Adopt algorithm for a single variable.
 **********************************************************************/

package adopt.solver;

import adopt.common.*;
import adopt.problem.*;

import java.lang.*;
import java.io.*;
import java.util.*;

public class Adopt{

  public static String VALUE = "VALUE";
  public static String COST = "COST";
  public static String TERM = "TERMINATE";

  /* my current Context of other variables */
  Context CurrentContext;
  /* my variable */
  public Variable x_i;
  /* current value of my variable */
  Value d_i;
  /* list of variables I'm connected to */
  Vector links;
  /* problem to be solved */
  Problem pbm;
  /* upper bound, lower bound, threshold */
  int UB;
  int LB;
  int THRESHOLD;
  /* flags */
  boolean parentHasTerminated = false;
  boolean iWantToTerminate = false;
  boolean iWantToBacktrack = false;
  boolean valueChanged = false;
  /* used for communication */
  MessageSender mSender;

  /*Instrumentation Variables*/
  /* Log messages sent */
  int okmsgcnt=0;
  int costmsgcnt=0;
  /* for logging output */
  Logger nLog = null;
  /* true means use flimits (threshold).
     false means dont use flimits (threshold). */
  boolean limitFlag = true;
  /* true means use context(d, x_l).
     false means dont use context(d, x_l). */
  boolean contextFlag = true;

  /******* Class to hold cost information ********/
  class Cost{
    /* my child x_l */
    Variable x_l;
    /* my value d */
    Value d;
    /* ub(d,x_l): upper bound */
    int ub;
    /* lb(d,x_l): lower bound */
    int lb;
    /* context (d,x_l): context relevant to lb(d,x_l), ub(d,x_l) */
    Context context;
    /* t(d,x_l): threshold I can send to child */
    int t;
    
    /* class constructor */
    public Cost(){
      x_l = null;
      d = null;
      lb = 0;
      ub = Problem.MAX_VALUE;
      context = new Context();
      t = 0;
    }
  }

  /* list of costs */
  Vector costs;
  /* my parent */
  Variable parentVar;
  /* my children */
  Vector childrenVar;

  public Adopt(Problem p, Variable vvar, boolean useLimit, boolean useContext, 
	       MessageSender mSndr){
     
    /*Instrumentation Code*/
    /* log msgs cnts */    
    PrintWriter pwtr = null;
    String fname = "Logs/"+vvar.uniqueNameOf()+".log";
    try{
      FileWriter ffile = new FileWriter(fname); 
      pwtr = new PrintWriter(ffile);   
    }catch(IOException e){
      System.out.println("Error in opening "+fname+" for logging");
      System.out.println("Exiting.");
      System.exit(1);
    }
    nLog = new Logger(pwtr);
    nLog.printToLogln("# <time> <cycles> <totalmsg> <LB> <UB> <THRESHOLD> <iWantToTerminate>");
    /*End Instrumentation Code*/

    /* initialize variables */
    mSender = mSndr;
    pbm = p;
    CurrentContext = new Context();
    x_i = vvar;
    links = pbm.getLinks(x_i);
    limitFlag = useLimit;
    contextFlag = useContext;
    costs = new Vector();
    Value[] Di = x_i.domain();
    if(Di.length > 0)
      d_i = Di[0];
    valueChanged = true;
    parentVar = pbm.previousVariable(x_i); 
    childrenVar = pbm.getChildrenVar(x_i);
    UB = Problem.MAX_VALUE;
    LB = 0;
    THRESHOLD = 0;
    /* done variable initialization */

    /* must be linked to parent */
    if(parentVar != null &&
       !isLinked(parentVar))
      links.addElement(parentVar);

    /* must be linked to all children */
    for(int i=0;i<childrenVar.size();i++){
      Variable childVar = (Variable) childrenVar.elementAt(i);
      if(!isLinked(childVar))
 	links.addElement(childVar);
      /* initialize all child costs */
      for(int j=0;j<Di.length;j++){
	Value val = Di[j];
	Cost c = new Cost();
	c.x_l = childVar;
	c.d = val;
	costs.add(c);
      }
    }
    /* special root initialization */
    if(parentVar == null){
      parentHasTerminated = true;
      /* set initial threshold to given error bounded */
      THRESHOLD = Algorithm.BOUND;
      maintainAllocationInvariant();
    }
    else
      THRESHOLD = 0;
  }

  public void init(){
    SEND_VALUE();
  }

  /* is given variable in 'links'? */
  public boolean isLinked(Variable v){
    boolean linked = false;
    for(int j=0;j<links.size();j++){
      Variable v2 = (Variable)links.elementAt(j);
      if(v.equalVar(v2) || v.equalVar(x_i)){
	linked = true;
	break;
      }
    }
    return linked;
  }


  /**************Message Sending**************************/

  
  /* send value to each lower priority variable in 'links' */
  public void SEND_VALUE(){
    boolean valueSent = false;

    for(int i=0;i<links.size();i++){
      Variable vvar = (Variable) links.elementAt(i);
      if(pbm.comparePriority(x_i, vvar)){
	sendOneValue(vvar);
	valueSent = true;
      }
    }
  }

  public void sendOneValue(Variable x_l){
    String aname = "agent" + x_l.agentIDof();
    String header;
    header = this.VALUE;
    int t = 0;

    /* If I'm sending to my child x_l, get t(d,x_l) */
    Cost c = getCost(x_l, d_i);
    if(c != null)
      t = c.t;
    
    /* Msg format: "VALUE  <destvariable> <THRESHOLD> <variable> <value> " */
    String msg = header + " " + x_l.uniqueNameOf() + " " + t + 
      " " + x_i.uniqueNameOf() + " " + d_i.toString();

    mSender.sendMessage(new Message(x_l.agentID, x_i.agentID, msg), aname, true);    
    
    /*Instrumentation Code*/
    okmsgcnt++;
    logData();
  }

  public void SEND_TERMINATE(){
    for(int i=0;i<childrenVar.size();i++){
      Variable x_l = (Variable) childrenVar.elementAt(i);
      String aname = "agent" + x_l.agentIDof();
      String header = this.TERM;
      
      /* Msg format: "TERMINATE  <destvar> <sourcevariable> " */
      String msg = header + " " + x_l.uniqueNameOf() + " " + x_i.uniqueNameOf();
      mSender.sendMessage(new Message(x_l.agentID, x_i.agentID, msg), aname, true);    
    }
  }

  public void SEND_COST(Context vw, int lb, int ub){

    if(parentVar != null){
      String header = this.COST;

      /*debug*/ Utility.Dprint(x_i.uniqueNameOf() + " to " + parentVar.uniqueNameOf() + 
		     " " + vw.toString(),     Utility.MSG_LEVEL2);

      /* Msg format: "COST <destvariable> <sourcevariable> <lowerbound> <upperbound> <Context>" */
      String msg = header + " " + parentVar.uniqueNameOf() + " " + 
	x_i.uniqueNameOf() + " " + lb + " " + ub + " " + vw.toMsg();
      
      mSender.sendMessage(new Message(parentVar.agentID, x_i.agentID, msg), 
			  "agent"+parentVar.agentIDof(), true);    
      
      /*Instrumentation Code*/
      costmsgcnt++;
      logData();
    }
  }

  /**************Message Receiving**************************/

  public void whenReceivedVALUE(Variable vvar, Value vval, int thresh){

    /*debug*/ Instrumentation Itn2 = new Instrumentation();  
    /*debug*/ Itn2.start("         begin VALUE msg ", Utility.TimeMS());
    /*debug*/ Utility.Dprint(" whenReceivedVALUE(): ", Utility.MSG_LEVEL2);
    /*debug*/ Utility.Dprint("    "+vvar.uniqueNameOf()+"="+ vval.toString() + 
			     " ,THRESHOLD = " + thresh, Utility.MSG_LEVEL2);

    updateContexts(vvar, vval);

    /*debug*/    Itn2.end("         end update context ", Utility.TimeMS());
    /*debug*/    Itn2.timeElapsed("           time elapsed");
    /*debug*/    Itn2.start("          begin splitThreshold", Utility.TimeMS());

    if(vvar.equalVar(parentVar)){
      THRESHOLD = thresh;
      maintainAllocationInvariant();
    }
    iWantToBacktrack = true;


    /*debug*/     Itn2.end("          end splitThreshold", Utility.TimeMS());
    /*debug*/     Itn2.timeElapsed("           time elapsed");
    /*debug*/     Itn2.start("          begin printing", Utility.TimeMS());
    //     /*debug*/     printCosts(Utility.MSG_LEVEL2);
    //     /*debug*/     printCurrentState(Utility.MSG_LEVEL2);
    /*debug*/     Itn2.end("          end printing", Utility.TimeMS());
    /*debug*/     Itn2.timeElapsed("           time elapsed");
  }

  public void whenReceivedCOST(Context vw, int lb, int ub, String childName){

    /*debug*/    Utility.Dprint(" whenReceivedCOST(): ", Utility.MSG_LEVEL2);
    
    /* add to CurrentContext if necessary */
    for(int i=0;i<vw.vars.size();i++){
      Variable v = (Variable) vw.vars.elementAt(i);
      /* if not my neighbor, update CurrentContext */
      if(!pbm.connected(v, x_i) &&
	 !x_i.equalVar(v))
	updateContexts(v, vw.valueOf(v));
    }
    
    /* print debug info */
    //    printCosts(Utility.MSG_LEVEL2);
    /*debug*/    Utility.Dprint("  Context  = " + vw.toString(), Utility.MSG_LEVEL2);
    /*debug*/    Utility.Dprint("  sender  = " + childName, Utility.MSG_LEVEL2);
    /* end print debug info */

    Value val = vw.valueOf(x_i);
    Variable childVar = pbm.getVariableFromUniqueVarName(childName);

    if(val != null){
      Context cvw = new Context();
      cvw.addVarToContext(x_i, val);
      vw = vw.setMinus(cvw);
      if(vw.compatible(CurrentContext)){
	Cost c = getCost(childVar, val);
	if(c==null){
	  System.out.println("I got a Context from a non-child!");
	  System.exit(0);
	}
	else{
	  if(pbm.compareDeltas(lb, c.lb) > 0 ||
	     pbm.compareDeltas(c.ub, ub) > 0 ){
	    /* is d_i changing? */
	    if(c.d.equal(d_i)){
	      iWantToBacktrack = true;
	    }
	    c.lb = lb;
	    c.ub = ub;
	    if(contextFlag)
	      c.context = vw;
	  }
	  if (pbm.compareDeltas(c.lb, c.t) > 0 ||
	      pbm.compareDeltas(c.t, c.ub) > 0)
	    maintainChildThresholdInvariant(c);
	}
      }
    }
    else{
      /* val is null, meaning my value isnt in vw.
	 so update costs for all my values */
      if(vw.compatible(CurrentContext)){ 
	for(int i=0;i<costs.size();i++){
	  Cost c = (Cost) costs.elementAt(i);
	  if(c.x_l.isUniqueNameOf(childName)){
	    if(pbm.compareDeltas(lb, c.lb) > 0 ||
	       pbm.compareDeltas(c.ub, ub) > 0 ){
	      /* is d_i changing? */
	      if(c.d.equal(d_i)){
		iWantToBacktrack = true;
	      }
	      c.lb = lb;
	      c.ub = ub;
	      if(contextFlag)
		c.context = vw;
	    }
	    if (pbm.compareDeltas(c.lb, c.t) > 0 ||
		pbm.compareDeltas(c.t, c.ub) > 0)
	      maintainChildThresholdInvariant(c);
	  }
	}
      }
    }
    iWantToBacktrack = true;    


    /********** DEBUG ***************/
    if(false && (x_i.varID == 5 ||
		 x_i.varID == 90)){
      Utility.Dprint(x_i.uniqueNameOf() + " to ", 
		     Utility.MSG_LEVEL2);
      printCosts(0);
      printCurrentState(0);
      System.out.println(" received Context: " + vw.toString());
      Algorithm.waitForKey(true);
    }
//     printCosts(Utility.MSG_LEVEL2);
//     printCurrentState(Utility.MSG_LEVEL2);
    /* end DEBUG */
  }

  public void whenReceivedTERMINATE(){
    /*debug*/ Utility.Dprint("  " + x_i.uniqueNameOf() + " parent has TERMINATED", 
		   Utility.MSG_LEVEL3);

    parentHasTerminated = true;
    iWantToBacktrack = true;
  }

  public void backtrack(){

    /* debugging consistency check */
    for(int i=0; i< costs.size();i++){
      Cost c = (Cost)costs.elementAt(i);
      if(!c.context.compatible(CurrentContext)){
	System.out.println(" stored context not consistent with current Context! ");
	System.out.println(" context: " + c.context.toString());
	printCosts(0);
	printCurrentState(0);
	System.exit(0);
      }
      if(pbm.compareDeltas(c.lb, c.t) > 0){
	System.out.println(" lb greater than threshold! ");
	System.out.println(" x_l: " + c.x_l.uniqueNameOf());
	printCosts(0);
	printCurrentState(0);
	Algorithm.waitForKey(true);
	System.exit(0);
      }
      if(pbm.compareDeltas(c.t, c.ub) > 0){
	System.out.println(" ub less than threshold! ");
	System.out.println(" x_l: " + c.x_l.uniqueNameOf());
	printCosts(0);
	printCurrentState(0);
	Algorithm.waitForKey(true);
	System.exit(0);
      }
    }
    /* end debugging ********************************* */
    
    if(iWantToBacktrack){
      iWantToBacktrack = false; 
      /*debug*/ Utility.Dprint("---" + x_i.uniqueNameOf() + "---", Utility.MSG_LEVEL2);
      
      /* delta(d_i)*/
      int delta;
      /* LB(d_i) */
      int LB_di;
      /* UB(d_i) */
      int UB_di;
      /* min{d in Di} LB(d) */
      int minLB;
      /* min{d in Di} UB(d) */
      int minUB;
      /* d that minimizes LB(d) */
      Value min_d;
      /* d that minimizes UB(d) */
      Value min_d2;

      /* LB(d_i)  = delta(d_i) + \sum{x_l in children} lb(d_i, x_l)*/
      /* UB(d_i)  = delta(d_i) + \sum{x_l in children} ub(d_i, x_l)*/
      delta = computeDelta(d_i);
      int lb = computeLB(d_i);
      int ub = computeUB(d_i);
      LB_di = pbm.sumDeltas(delta, lb);
      UB_di = pbm.sumDeltas(delta, ub);

      /*debug*/  Utility.Dprint("  LB(d_i) " + LB_di, Utility.MSG_LEVEL2);
      /*debug*/  Utility.Dprint("  UB(d_i) " + UB_di, Utility.MSG_LEVEL2);
      /*debug*/  Utility.Dprint("  current THRESHOLD " + THRESHOLD, Utility.MSG_LEVEL2);
      /*debug*/  Utility.Dprint("  ...searching for better value" , Utility.MSG_LEVEL3);

      /* COMPUTE LB = min{d in D_i} LB(d),
	         UB = min{d in D_i} UB(d)  */
      minLB = LB_di;
      minUB = UB_di;
      min_d = d_i;
      min_d2 = d_i;
      Value[] Di = x_i.domain();
      for(int j=0;j<Di.length;j++){
	Value d = Di[j];
	int delta_d =  computeDelta(d);
	/* compute lower bound */
	int temp = computeLB(d);
	int lb_d = pbm.sumDeltas(delta_d, temp);
	/* is it lower? */
	if(pbm.compareDeltas(minLB, lb_d) > 0){
	  min_d = d;
	  minLB = lb_d;
	}
	/* compute upper bound */
	temp = computeUB(d);
	int ub_d = pbm.sumDeltas(delta_d, temp);
	/* is it lower? */
	if(pbm.compareDeltas(minUB, ub_d) > 0){
	  min_d2 = d;
	  minUB = ub_d;
	}
      }
      /* update LB, UB */
      LB = minLB;
      UB = minUB;
      /* error check */
      /* if I am a leaf, and LB != UB, then we have a problem. */
      int n = childrenVar.size();
      /* I am a leaf */
      if(n == 0){
	if( LB != UB){
	  System.out.println(" Adopt error!!!");
	  System.out.println(" I am a leaf and LB != UB.");
	  Utility.Dprint(" LB = " + LB,0);
	  Utility.Dprint(" UB = " + UB,0);
	  Utility.Dprint(" THRESHOLD = " + THRESHOLD,0);
	  printCurrentState(0);
	  printCosts(0);
	  System.exit(0);
	}
      }

      /* debug */ Utility.Dprint(" LB = " + LB,Utility.MSG_LEVEL);
      /* debug */ Utility.Dprint(" UB = " + UB,Utility.MSG_LEVEL);
      /* debug */ Utility.Dprint(" THRESHOLD = " + THRESHOLD,Utility.MSG_LEVEL);

      /* compute the context of the COST message will send */
      Context CostContext = new Context();
      if(!contextFlag){
	CostContext = new Context(CurrentContext);
      }
      else{
	Context unionContext = new Context();
	/* unionContext = U_{d} context(d) */
	for(int i=0;i<costs.size();i++){
	  Cost c = (Cost) costs.elementAt(i);
	  unionContext = unionContext.union(c.context);
	}
	for(int i =0; i< CurrentContext.vars.size(); i++){
	  Variable v = (Variable) CurrentContext.vars.elementAt(i);
	  if(pbm.connected(x_i, v))
	    CostContext.addVarToContext(v, CurrentContext.valueOf(v));
	}
	CostContext = CostContext.union(unionContext);
      }


      /* Maintain Threshold Invariant */
      if(pbm.compareDeltas(LB, THRESHOLD) > 0){
	THRESHOLD = LB;
      }
      if(pbm.compareDeltas(THRESHOLD, UB) > 0){
	THRESHOLD = UB;
      }

      if(!limitFlag){
	/* always switch to best value */
	if(!d_i.equal(min_d))
	  valueChanged = true;
	d_i = min_d;
      }
      else{
	/* Switch variable value if
	   a) UB = THRESHOLD, or
	   b) the LB(d_i) exceeds the current THRESHOLD.
	   */
	if(UB == THRESHOLD) {
	  if(!d_i.equal(min_d2))
	    valueChanged = true;
	  d_i = min_d2;
	}else if(pbm.compareDeltas(LB_di, THRESHOLD) > 0) {
	  if(!d_i.equal(min_d))
	    valueChanged = true;
	  d_i = min_d;
	}
	/* debug prints */
	else{
	  Utility.Dprint(" Current Val: " + x_i.uniqueNameOf() 
			 + " =  (" + d_i.toString() + ") ",
			 Utility.TRACE_FUNCTION_LEVEL);
	  Utility.Dprint(" currentCost = " + LB_di,Utility.TRACE_FUNCTION_LEVEL);
	  Utility.Dprint(" Best Val: " + x_i.uniqueNameOf() 
			 + " =  (" + min_d.toString() + ") ",
			 Utility.TRACE_FUNCTION_LEVEL);
	  Utility.Dprint(" Not Switching...!",Utility.TRACE_FUNCTION_LEVEL);
	}
	/* end debug prints */
      }

      /* send messages */
      SEND_COST(CostContext, LB, UB);
      printCurrentState(Utility.MSG_LEVEL2);
      SEND_VALUE();
      
      /* termination check */
      if (parentHasTerminated){
	if(UB == THRESHOLD) {
	  SEND_TERMINATE(); 
	  iWantToTerminate = true;
	  System.out.println("    " + x_i.uniqueNameOf() + " is done..." + d_i.toString());
	}
      }
    }
  }

  
  public void maintainAllocationInvariant(){
    splitThresholdOneChild();
  }


  /* Give all threshold to first child */
  public void splitThresholdOneChild(){
    int n = childrenVar.size();
    if(n == 0)
      return;
    /* if THRESHOLD is infinity, set all thresholds to infinity */
    if(pbm.compareDeltas(THRESHOLD, Problem.MAX_VALUE) == 0){
      for(int j=0; j<costs.size();j++){
	Cost c = (Cost)costs.elementAt(j);
	c.t = Problem.MAX_VALUE;
      }
    }
    else{
      Value[] Di = x_i.domain();
      /* different threshold for each choice of local value */
      for(int k=0;k<Di.length;k++){
	Value val = Di[k];
	int bb = pbm.subDeltas(THRESHOLD, computeDelta(val));

	Utility.Dprint(" THRESHOLD = "+ THRESHOLD, Utility.MSG_LEVEL2);
	Utility.Dprint(" SplitThreshold(): bb("+ val.toString() + ") = " + bb, 
		       Utility.MSG_LEVEL2);
	/* how much threshold do we currently have given out? */
	int total_lb = 0;
	int total_t = 0;
	int total_ub = 0;
	for(int i=0;i<childrenVar.size();i++){
	  Variable childVar = (Variable) childrenVar.elementAt(i);
	  Cost c = getCost(childVar, val);
	  total_t = pbm.sumDeltas(total_t, c.t);
	}
	
	/* positive threshold to give out */
	if(pbm.compareDeltas(bb, total_t) > 0 ){
	  bb = pbm.subDeltas(bb, total_t);
	  int i=0;
	  /* while we have more threshold to give out */
	  while(pbm.compareDeltas(bb,0) > 0 &&
		pbm.compareDeltas(Problem.MAX_VALUE, bb) > 0 &&
		i < childrenVar.size()){
	    Variable childVar = (Variable) childrenVar.elementAt(i++);
	    Cost c = getCost(childVar, val);
	    bb = increaseThreshold(c, bb);
	  }
	}
	/* need to take away thresholds */
	else if(pbm.compareDeltas(total_t, bb) > 0){
	  bb = pbm.subDeltas(total_t, bb);
	  int i=0;
	  /* while we have more threshold take away */
	  while(pbm.compareDeltas(bb,0) > 0 &&
		pbm.compareDeltas(Problem.MAX_VALUE, bb) > 0 &&
		i < childrenVar.size()){
	    Variable childVar = (Variable) childrenVar.elementAt(i++);
	    Cost c = getCost(childVar, val);
	    bb = decreaseThreshold(c, bb);
	  }
	}
      }
    }
  }

  /* evenly distribute Threshold across all children */
  public void splitThresholdEven(){
    int n = childrenVar.size();
    
    if(n == 0)
      return;

    /* if THRESHOLD is infinity, set all thresholds to infinity */
    if(pbm.compareDeltas(THRESHOLD, Problem.MAX_VALUE) == 0){
      for(int j=0; j<costs.size();j++){
	Cost c = (Cost)costs.elementAt(j);
	c.t = Problem.MAX_VALUE;
      }
    }
    else{

      Value[] Di = x_i.domain();
      /* different threshold for each choice of local value */
      for(int k=0;k<Di.length;k++){
	Value val = Di[k];
	int bb = pbm.subDeltas(THRESHOLD, computeDelta(val));

	/* split up threshold for children */
	for(int i=0;i<childrenVar.size();i++){
	  Variable childVar = (Variable) childrenVar.elementAt(i);
	  for(int j=0; j<costs.size();j++){
	    Cost c = (Cost)costs.elementAt(j);
	    if(childVar.equalVar(c.x_l) &&
	       c.d.equal(val)){
	      if(bb > 0){
		c.t = 1;
	      }
	      else{
		c.t = 0;
	      }
	    }
	  }
	  bb--;
	}
	/* there is more threshold to give out */
	while(bb > 0){
	  for(int i=0;i<childrenVar.size();i++){
	    Variable childVar = (Variable) childrenVar.elementAt(i);
	    for(int j=0; j<costs.size();j++){
	      Cost c = (Cost)costs.elementAt(j);
	      if(childVar.equalVar(c.x_l) &&
		 c.d.equal(val)){
		if(bb > 0){
		  c.t++;
		}
	      }
	    }
	    bb--;
	  }
	}
      }
    }

    /* debug prints */
    Utility.Dprint(" THRESHOLD = " + THRESHOLD, Utility.MSG_LEVEL  );
    printCosts(Utility.MSG_LEVEL);
    //Algorithm.waitForKey(true);
  }


  /* when some child's lower or upper bound changes,
     we may need to adjust thresholds. 

     Example: total threshold = 10. two children xl, xk.
     gave threhold 10 to xl, threshold 0 to xk.
     
     case 1) xk reports lb = 2. 
     
          a) Need to raise xk's threshold to 2.
          b) Deduct 2 from xl's threshold.

     case 2) xl reports ub = 4.
     
          a) Need to lower xl's threshold to 4.
	  b) Need to increase xk's threshold to 6.

     */
  public void maintainChildThresholdInvariant(Cost cst){

    printCosts(Utility.MSG_LEVEL2);

    int i = 0;

    /* Case 1: the child's lower bound has exceeded its threshold */
    if(pbm.compareDeltas(cst.lb, cst.t) > 0){
      int bb = pbm.subDeltas(cst.lb, cst.t);
      /* 1a: raise its threshold to its lower bound */
      cst.t = cst.lb;
      /* 1b: must lower the threshold of children with overestimates by
	 'bb' amount */
      while(pbm.compareDeltas(bb,0) > 0 &&
	    pbm.compareDeltas(Problem.MAX_VALUE, bb) > 0 &&
	    i < childrenVar.size()){
	Variable childVar = (Variable) childrenVar.elementAt(i++);
	Cost c = getCost(childVar, cst.d);
	bb = decreaseThreshold(c, bb);
      }
    }
    /* Case 2: the child's upper bound is below threshold */
    else if(pbm.compareDeltas(cst.t, cst.ub) > 0){
      int bb = pbm.subDeltas(cst.t, cst.ub);
      /* 2a: lower its threshold to its upper bound */
      cst.t = cst.ub;

      /* 2b: must increase threshold of children with possible underestimates by 
	 'bb' amount */
      while(pbm.compareDeltas(bb,0) > 0 &&
	    pbm.compareDeltas(Problem.MAX_VALUE, bb) > 0 &&
	    i < childrenVar.size()){
	Variable childVar = (Variable) childrenVar.elementAt(i++);
	Cost c = getCost(childVar, cst.d);
	bb = increaseThreshold(c, bb);
      }
    }
    iWantToBacktrack = true;
//     Algorithm.waitForKey(true);
//     printCosts(Utility.MSG_LEVEL2);
//     Algorithm.waitForKey(true);
  }
  

  /*==============Helper Funcs =======================*/

  public void updateContexts(Variable vvar, Value vval){
    /* use context(d) */
    if(contextFlag){
      CurrentContext.addVarToContext(vvar, vval);
      
      Context cvw = new Context();
      cvw.addVarToContext(vvar, vval);
      
      for(int i=0; i< costs.size();i++){
	Cost c = (Cost)costs.elementAt(i);
	/* if contexts are incompatible, zero out costs */
	if(!c.context.compatible(cvw)){
	  c.lb = 0;
	  c.t = 0;
	  c.ub = Problem.MAX_VALUE;
	  c.context = new Context();
	}
      }
    }
    else{
      Value v = CurrentContext.valueOf(vvar);
      if(v == null){
	CurrentContext.addVarToContext(vvar, vval);
      }
      /* has current Context changed? */
      else if(!v.equal(vval)){
	/* we are not using context(d,x_l), so zero out all costs */
	for(int i=0; i< costs.size();i++){
	  Cost c = (Cost)costs.elementAt(i);
	  c.lb = 0;
	  c.ub = Problem.MAX_VALUE;
	  c.t = 0;
	  c.context = new Context();
	}
	CurrentContext.addVarToContext(vvar, vval);
      }
    }
  }
    
  /* attempt to decrease given child's threshold by 'amt'.
     return any unused threshold */
  public int decreaseThreshold(Cost c, int amt){

    int result= amt;
    int excess = pbm.subDeltas(c.t, c.lb);
    /* does this child have any room to lower it's threshold? */
    if(pbm.compareDeltas(excess, 0) > 0 ){
      /* it's not enough (or its exactly enough), just lower it
	 as much as we can */
      if(pbm.compareDeltas(amt, excess) >= 0){
	c.t = c.lb;
	result = pbm.subDeltas(amt, excess);
      }
      /* it's enough, raise it as far as we need to */
      else{
	c.t = pbm.subDeltas(c.t, amt);
	result = 0;
      }
    }
    return result;
  }

  /* attempt to increase given child's threshold by 'amt'.
     return any unused threshold */
  public int increaseThreshold(Cost c, int amt){
    int result= amt;
    int excess = pbm.subDeltas(c.ub, c.t);

    /* does this child have any room to raise it's threshold? */
    if(pbm.compareDeltas(excess, 0) > 0 ){
      /* it's not enough (or its exactly enough), just raise it
	 as much as we can */
      if(pbm.compareDeltas(amt, excess) >= 0){
	/* raise it */
	c.t = c.ub;
	result = pbm.subDeltas(amt,excess);
      }
      /* its enough, just raise it as far as
	 we need */
      else {
	c.t = pbm.sumDeltas(c.t, amt);
	result = 0;
      }
    }
    return result;
  }

  public int computeDelta(Value val){
    Context vw = new Context(CurrentContext);
    vw.addVarToContext(x_i, val);
    int delta =  pbm.delta(x_i,vw);

    return delta;
  }

  public int computeLB(Value val){
    
    int e = 0;
    for(int i=0;i<costs.size();i++){
      Cost c = (Cost) costs.elementAt(i);
      if(val.equal(c.d))
	e = pbm.sumDeltas(e, c.lb);
    }
    
    /* print debug info */
    Utility.Dprint("  lb(" + val.toString() + ") = " + computeDelta(val) + " " + e, 
		   Utility.MSG_LEVEL2);
    /* end print debug info */
    
    return e;
  }

  public int computeUB(Value val){
    
    int e = 0;
    for(int i=0;i<costs.size();i++){
      Cost c = (Cost) costs.elementAt(i);
      if(val.equal(c.d))
	e = pbm.sumDeltas(e, c.ub);
    }
    /* print debug info */
    Utility.Dprint("  ub(" + val.toString() + ") = " + computeDelta(val) + " " + e, 
		   Utility.MSG_LEVEL2);
    /* end print debug info */
    return e;
  }

  Cost getCost(Variable vv, Value vval){
    for(int i=0;i<costs.size();i++){
      Cost c = (Cost) costs.elementAt(i);
      if(c.x_l.equalVar(vv) &&
	 c.d.equal(vval))
	return c;
    }
    return null;
  }

  public void printCurrentState(int level){
    String valS = d_i.toString();
    Utility.Dprint("  Current Context: " + CurrentContext.toString(), 
		   level);
    Utility.Dprint("  Current Value: " + x_i.uniqueNameOf() + " =  " + valS, 
		   level);

  }

  public void printCosts(int level){
    Utility.Dprint("----" + x_i.uniqueNameOf() + "----", level); 
    Utility.Dprint(" LB = " + LB, level);
    Utility.Dprint(" UB = " + UB, level);
    Utility.Dprint(" THRESHOLD = " + THRESHOLD, level);
    for(int i=0;i<costs.size();i++){
      Cost c = (Cost) costs.elementAt(i);
      Utility.Dprint(" c(" + c.x_l.uniqueNameOf() + ", " + c.d.toString() 
		     + "): " + "ub=" + c.ub + ", " + "lc=" + computeDelta(c.d) 
		     + ", " +  "lb=" + c.lb + ", " + "thresh=" + c.t + 

		     ", " + "context=" + c.context.toString(), level);
    }
  }
  
  public void updateContexts(){
    for(int i=0; i< costs.size();i++){
      Cost c = (Cost)costs.elementAt(i);
      c.lb = 0;
    }
  }

  public void logData(){
    /* total number of messages sent */
    int total = okmsgcnt+costmsgcnt;
    
    nLog.printToLogln(Utility.TimeStringMS() + " " + "0" 
		      + " " + total + " " + LB + " " + UB + " " + THRESHOLD + " " + 
		      iWantToTerminate);
    
  }

  public static void main(String args[]){
  }
}
  
