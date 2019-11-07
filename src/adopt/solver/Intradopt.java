/**********************************************************************
 * File Name: Intradopt.java
 * Author: Jay Modi (modi@isi.edu)
 * Date: Aug 27 2001
 *
 * Implements the Adopt algorithm for multiple variables per agent.
 **********************************************************************/

package adopt.solver;

import adopt.common.*;
import adopt.problem.*;

import java.lang.*;
import java.io.*;
import java.util.*;

public class Intradopt extends Algorithm{

  /* list of pseudo-agents, one for each variable I own */
  Vector pseudoAgents;
  
  /* input message queues for each pseudoAgent.  */
  Hashtable msgQs = null;
  /* input message queue buffer */
  LinkedList bufferedMsgs = null;

  public Intradopt(int agentID, Problem p, MessageSender mSndr){
    super(agentID,p,mSndr);
    msgQs = new Hashtable();
    bufferedMsgs = new LinkedList();
  } 

  /*Create a msg queue for the given pseudo-agent. */
  public void addMsgReceiver(String uname){
    LinkedList msgQ = new LinkedList(); 
    msgQs.put(uname, msgQ);
  }

  public void run() {

    Utility.Dprint("  Running Intradopt()...");
    /* problem set up */
    Vector vec= pbm.getVariablesFromAgentID(localAgentID);
    if(vec.size() == 0){
      Utility.Dprint("Can't find any variables for agent: " + localAgentID); 
      return;
    }

    /* set up an input message queue for each variable */
    for(int i=0;i<vec.size();i++){
      Variable var = (Variable) vec.elementAt(i);
      addMsgReceiver(var.uniqueNameOf());
    }

    /* create a pseudoagent for each variable I own */
    pseudoAgents = new Vector();
    for(int i=0;i<vec.size();i++){
      Variable var = (Variable) vec.elementAt(i);
      Adopt adpt = null;
      adpt = new Adopt(pbm, var, true, true, this);
      pseudoAgents.add(adpt);
    }

    /* get each solver started */
    for(int i=0;i<pseudoAgents.size();i++){
      Adopt adpt = (Adopt) pseudoAgents.elementAt(i);
      adpt.init();
      adpt.backtrack();
    }

    /* begin processing messages */
    handleMsgs();
  }

  public void handleMsgs(){
    Message msg = null;
    long tprime = Utility.TimeMS();

    Utility.Dprint("Entering handleMsgs():...", Utility.TRACE_RIDICULOUS_LEVEL);
    Utility.Dprint("  Waiting for incoming msg...", Utility.MSG_LEVEL3);
    while(!iWantToTerminate){
      cycleCnt++;
      /* deliver messages to each pseudoagent */
      for(int i=0;i<pseudoAgents.size();i++){
	Adopt adpt = (Adopt) pseudoAgents.elementAt(i);
	String uname = adpt.x_i.uniqueNameOf();
	boolean msgDelivered = false;
	
	LinkedList q = (LinkedList) msgQs.get(uname);
	try{msg = (Message) q.removeFirst();}
	catch(NoSuchElementException ex){ msg = null;}
	while(msg != null){
	  /* deliver message */
	  handleOneMsg(msg);
	  msgDelivered = true;
	  try{msg = (Message) q.removeFirst();}
	  catch(NoSuchElementException ex){ msg = null;}
	}
	/* allow pseudoagent to execute */
	if(msgDelivered){
	  Utility.Dprint("   "+uname+" executing...", Utility.MSG_LEVEL);
	  adpt.backtrack();
	}
      }
      
      /* report current variable values to Master agent (every 10 secs) */
      if(Utility.TimeMS() - lastReportTime > reportInterval){
	reportValuesToMaster();
	lastReportTime = Utility.TimeMS();
	/* also log some data */
	logData();
      }
      
      /* get messages from other agents */
      msg = mSender.getMessage("agent"+localAgentID);
      while(msg!=null){
	bufferedMsgs.addLast(msg);
	numMsgsRcvd++;
	msg = mSender.getMessage("agent"+localAgentID);
      } 
      
      /* put buffered messages on appropriate input message queue */
      msg=null;
      try{msg = (Message) bufferedMsgs.removeFirst();}
      catch(NoSuchElementException ex){ msg = null;}
      while(msg != null){
	String msgstring = ((Message)msg).rawMessage();
	StringTokenizer stok = new StringTokenizer(msgstring);
	String firstword = stok.nextToken();
	String dest = stok.nextToken();
	LinkedList q = (LinkedList) msgQs.get(dest);
	q.addLast(msg);
	try{msg = (Message) bufferedMsgs.removeFirst();}
	catch(NoSuchElementException ex){ msg = null;}
      }
      /* check for termination */
      if(checkForTermination2()){
	iWantToTerminate = true;
	/* record time of termination */
	timeEnd = Utility.TimeMS();
	reportValuesToMaster();
	logData();
	System.out.println("       agent" + localAgentID + "'s  cycle cnt: " + cycleCnt);
      }
    }
  }      

  public boolean checkForTermination2(){
    for(int i=0;i<pseudoAgents.size();i++){
      Adopt adpt = (Adopt) pseudoAgents.elementAt(i);
      if(!adpt.iWantToTerminate)
	return false;
    }
    /* All pseudoAgents terminated. must be done. */
    return true;
  }

  public boolean messageWanted(Adopt adpt, String destvar){
    if(adpt.x_i.uniqueNameOf().equals(destvar) &&    
       !adpt.iWantToTerminate){
      return true;
    }
    return false;
  }

  public void handleOneMsg(Message msg){

    String msgstring = ((Message)msg).rawMessage();
    StringTokenizer stok = new StringTokenizer(msgstring);
    String firstword = stok.nextToken();
    
    if(firstword.equals(Adopt.TERM)){
      String destvar = stok.nextToken();
      String sourcevar = stok.nextToken();
      /* invoke the necessary pseudoAgent */
      for(int i=0;i<pseudoAgents.size();i++){
	Adopt adpt = (Adopt) pseudoAgents.elementAt(i);
	if(messageWanted(adpt, destvar)){
	  adpt.whenReceivedTERMINATE();
	}
      }
    }
    else if(!timeToLoseMsg2()){

      /* Msg format: "VALUE  <destvariable> <THRESHOLD> <variable> <value> " */
      if(firstword.equals(Adopt.VALUE)){
	String destvar = stok.nextToken();
	int flimit = (new Integer(stok.nextToken())).intValue();
	String v2 = stok.nextToken();
	String val2 = stok.nextToken();
	/* invoke the necessary pseudoAgent */	
	for(int i=0;i<pseudoAgents.size();i++){
	  Adopt adpt = (Adopt) pseudoAgents.elementAt(i);
	  if(messageWanted(adpt, destvar)){
	    Variable vvar = (Variable) pbm.getVariableFromUniqueVarName(v2);
	    Value vval = vvar.getValue(val2);
	    adpt.whenReceivedVALUE(vvar, vval, flimit);
	  }
	}
      }
      /* Msg format: "COST  <destvariable> <sourcevariable> <lb> <ub> <context>" */
      else if(firstword.equals(Adopt.COST)){
	String destvar = stok.nextToken();
	String sourcestr = stok.nextToken();
	int lb = (new Integer(stok.nextToken())).intValue();
	int ub = (new Integer(stok.nextToken())).intValue();
	StringBuffer sb = new StringBuffer();
	while(stok.hasMoreTokens())
	  sb.append(" " + stok.nextToken());
	/* invoke the necessary pseudoAgent */
	for(int i=0;i<pseudoAgents.size();i++){
	  Adopt adpt = (Adopt) pseudoAgents.elementAt(i);
	  if(messageWanted(adpt, destvar)){
	    Context vvw = parseContext(sb.toString());
	    adpt.whenReceivedCOST(vvw, lb, ub, sourcestr);
	  }
	}
      }
    }
  }

  public void reportValuesToMaster(){
    for(int i=0;i<pseudoAgents.size();i++){
      Adopt adpt = (Adopt) pseudoAgents.elementAt(i);
	/* Msg format: "STATUS <unique name> <current val> <cost>" */
	String rawmsg = "STATUS " + adpt.x_i.uniqueNameOf() + 
	  " " + adpt.d_i.toString() + " " + 0;
	
	mSender.sendMessage(new Message(Master.masterID, localAgentID, rawmsg),
			    "agent"+Master.masterID, false);
    }
  }

  public void setLogger(Logger l){
    nLog = l;
    nLog.printToLogln("# <time> <cycles> <nummsgsrcvd> <iwanttoterminate>");
  }

  public void logData(){
    /* total number of messages sent */
    nLog.printToLogln(Utility.TimeStringMS() + " " + cycleCnt + " " +  
		      numMsgsRcvd + " " + iWantToTerminate );
  }

  /* Override Algorithm.sendMessage:
     (check to see if message is going from one pseudoagent to another) 
     'cntFlag': count this message in the final stats? */
  public void sendMessage(Message m, String aname, boolean cntFlag){
    /*check to see if message is going from one pseudoagent to another */
    if (aname.equals("agent"+localAgentID))
      bufferedMsgs.addLast(m);
    else{
      mSender.sendMessage(m, aname, cntFlag);
    }
    LogMsg_SEND(m, localAgentID);
  }
}
  
