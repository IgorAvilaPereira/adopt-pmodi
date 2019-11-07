/*
 *    Simulator.java
 *    Author: Jay Modi
 *    Date: Oct 24  2002
 *
 *    Coordinates running a DCOP algorithm on a DCOP.
 */

package adopt.solver;

import adopt.common.*;
import adopt.problem.*;

import java.util.*;

//import com.sun.tools.sjavac.ProblemException;

import java.io.*;

public class Simulator implements MessageSender {

	/* A DCO Problem */
	Problem pbm;
	/* type of DCOP */
	String problemType;
	/* problem input file (must be of type 'problemType') */
	String inputFileName;
	/* name of algorithm */
	String algorithmName;
	/*
	 * name of a special "master" agent. used to collect problem solution.
	 */
	String masterName;
	/* Data logger */
	Logger nLog;
	/* List of Adopt agents */
	Vector Agents;
	/* input message queues for each agent. */
	Hashtable msgQs = null;
	/* total number of messages sent in the system */
	int numsent = 0;
	/* total number of messages rcvd in the system */
	int numrcvd = 0;
	/*
	 * total number of *counted* messages sent in the system. (some messages are not
	 * counted in the final stats, e.g., messages sent to the MASTER are not
	 * counted.
	 */
	int msgCnt = 0;

	long timeStart;
	long timeEnd;
	/* how often to print out stats (ms) */
	int pollTime = 5000;
	/* time elapsed after which to terminate program (sec) */
	int timeMax = 7200; // 2 hrs

	boolean mutex = false;

	public Simulator(String args[]) {
		try {

			if (args.length != 3) {
				System.out.println("Usage: Simulator " + "<algorithm-name> <problem-type> <input-filename>");
				System.exit(0);
			}

			// adopt maxcsp-tree MaxCSP.txt
			algorithmName = args[0];
			problemType = args[1];
			inputFileName = args[2];

			masterName = "agent" + Master.masterID;

			/* Instrumentation Code */
			PrintWriter pwtr = null;
			String fname = "Logs/Summary.txt";
			try {
				FileWriter ffile = new FileWriter(fname);
				pwtr = new PrintWriter(ffile);
			} catch (IOException e) {
				System.out.println("Error opening " + fname + " for logging.");
				System.out.println("Exiting.");
				System.exit(0);
			}
			nLog = new Logger(pwtr);
			logAlgorithmName(nLog, algorithmName);
			/* Done Instrumentation Code */

		} catch (Exception ex) {
			System.err.println(ex.getMessage());
		}

		/* init message queues */
		msgQs = new Hashtable(Collections.synchronizedMap(new Hashtable()));

	}

	public void run() {

		/* read Problem */
		if (problemType.equals("maxcsp-chain")) {
			System.out.println("----------Problem: maxcsp-chain ----------");
			nLog.printToLogln("Problem MaxCSP (Random Chain)");
			pbm = new MaxCSPProblem(inputFileName, Problem.RANDOMCHAIN);
		} else if (problemType.equals("maxcsp-tree")) {
			System.out.println("----------Problem: maxcsp-tree ----------");
			pbm = new MaxCSPProblem(inputFileName, Problem.TREE);
			nLog.printToLogln("Problem MaxCSP (Tree, depth = " + pbm.orderDepth() + ")");
			if (algorithmName.startsWith("sbb") || algorithmName.startsWith("sid")) {
				pbm.convertToChain();
			}
		} else {
			System.out.println("Unknown problem: " + problemType);
			System.out.println("Exiting.");
			System.exit(0);
		}

		/* copy problem input file to Logs directory */
		// igor
//		try {
//			Runtime.getRuntime().exec("cp " + inputFileName + " Logs/");
//		} catch (Exception e) {
//		}

		/* init list of agents */
		Agents = new Vector();
		/* init Master agent */
		AgentThread master = new AgentThread(Master.masterID, pbm, "master", this);
		Agents.add(master);
		addMsgReceiver(masterName);

		/* for each agent in the problem, create an AgentThread */
		for (int i = 0; i < pbm.numAgents(); i++) {
			int agentID = ((Integer) pbm.agents.elementAt(i)).intValue();

			if (agentID == Master.masterID) {
				System.out.println(" Agent" + Master.masterID + " is a reserved agent ID!");
				System.out.println(" Please choose another ID and restart. ");
				System.exit(0);
			}

			AgentThread at = new AgentThread(agentID, pbm, algorithmName, this);
			Agents.add(at);
			addMsgReceiver("agent" + agentID);
		}

		Utility.Dprint("Simulator(): Begin Simulator run...");
		System.out.println("Begin algorithm execution...");

		timeStart = Utility.TimeMS();

		/* start each agent thread */
		for (int i = 0; i < Agents.size(); i++) {
			AgentThread at = (AgentThread) Agents.elementAt(i);
			at.start();
		}

		while (true) {
			try {
				Thread.sleep(pollTime);
				/* check for termination */
				boolean termFlag = true;
				for (int i = 0; i < Agents.size(); i++) {
					AgentThread at = (AgentThread) Agents.elementAt(i);
					/* check to see if some (non-master) agent does not want to terminate */
					if (!at.algorithm.iWantToTerminate) {
						if (at.algorithm.localAgentID != Master.masterID) {
							termFlag = false;
							break;
						}
					}
				}
				if (termFlag) {
					double t = (Utility.TimeMS() - (double) timeStart) / 1000;
					String s = "  All agents terminated";
					System.out.println(s + Instrumentation.fillerString(s) + t);
					s = "  Total msgs sent";
					System.out.println(s + Instrumentation.fillerString(s) + " " + msgCnt);
					printSummaryAndTerminate(false);
				} else {
					/* print execution status information */
					double t = (Utility.TimeMS() - (double) timeStart) / 1000;
					String s = "  time elapsed(sec)";
					System.out.println(s + Instrumentation.fillerString(s) + t);
					s = "  total msgs communicated";
					System.out.println(s + Instrumentation.fillerString(s) + numsent + " (rcvd=" + numrcvd + ")");
					if (t > timeMax) {
						s = "  Max Time Elapsed";
						System.out.println(s + Instrumentation.fillerString(s) + t);
						if (nLog != null) {
							nLog.printToLogln("Error maxtime " + timeMax);
						}
						printSummaryAndTerminate(true);
					}
				}
			} catch (Exception e) {
			}
		}
	}

	/* errorCondition == true means terminating abnormally */
	public void printSummaryAndTerminate(boolean errorCondition) {
		/* ending time is the latest ending time of any agent */
		timeEnd = 0;
		for (int i = 0; i < Agents.size(); i++) {
			AgentThread at = (AgentThread) Agents.elementAt(i);
			if (at.algorithm.timeEnd > timeEnd) {
				timeEnd = at.algorithm.timeEnd;
			}
		}

		double elapsed = ((double) timeEnd - timeStart) / 1000;

		for (int i = 0; i < Agents.size(); i++) {
			AgentThread at = (AgentThread) Agents.elementAt(i);
			if (at.algorithm.localAgentID == Master.masterID) {
				/* inform master that all agents are dead */
				((Master) at.algorithm).agentsAlive = false;
				/* wait for master to finish up */
				while (!at.algorithm.iWantToTerminate) {
					System.out.println("Simulator waiting for Master to finish");
					try {
						Thread.sleep(1000);
					} catch (Exception e) {
					}
				}
				System.out.println("Done. ");

				/**************************/
				/* Check for any lost messages */

				while (mutex)
					try {
						Thread.sleep(10);
					} catch (Exception e) {
					}
				mutex = true;
				/* count the number of messages left on message queues */
				int cnt = 0;
				Message msg = null;
				for (int j = 0; j < Agents.size(); j++) {
					AgentThread at2 = (AgentThread) Agents.elementAt(j);
					int id = at2.algorithm.localAgentID;
					LinkedList q = (LinkedList) msgQs.get("agent" + id);
					if (q != null) {

						try {
							msg = (Message) q.removeFirst();
						} catch (NoSuchElementException ex) {
							msg = null;
						}
						while (msg != null) {
							cnt++;
							try {
								msg = (Message) q.removeFirst();
							} catch (NoSuchElementException ex) {
								msg = null;
							}
						}
					}
				}
				if (numsent != numrcvd + cnt) {
					System.out.println(
							"Simulator error....some messages may" + "have been lost or otherwise unaccounted for!");
					String s = "  Error stats (sent,rcvd,undlvrd)";
					System.out.println(s + Instrumentation.fillerString(s) + " " + numsent + " " + numrcvd + " " + cnt);
					errorCondition = true;
					if (nLog != null) {
						nLog.printToLogln("Error msgLoss " + " " + numsent + "," + numrcvd + "," + cnt);

					}
				}

				/**************************/

				System.out.println("\n\n");
				System.out.println("Solution");
				System.out.println("--------------------");
				((Master) at.algorithm).computeQuality(0);
				((Master) at.algorithm).logSolution();

//				igor - funcao que dá só os valores das variaveis...
//				String opa = ((Master)at.algorithm).solutionString(0);
//				System.out.println("====================");
//				System.out.println(opa);
//				System.out.println("====================");

				/* Print summary Information */
				if (nLog != null) {
					if (errorCondition) {
						nLog.printToLogln("TotalTime2 null " + elapsed);
					} else {
						nLog.printToLogln("TotalTime2 " + elapsed);
					}
					nLog.printToLogln("TotalMsg2 " + msgCnt);
					nLog.printToLogln("FinalSolQlty " + ((Master) at.algorithm).finalQuality());
					nLog.printToLogln("BestSolQlty " + ((Master) at.algorithm).bestQuality());
				}
				break;
			}
		}

		System.out.println("********* TotalTime (secs) " + elapsed + " (pollTime " + pollTime / 1000 + ") *********");
		System.out.println("********* Thanks for playing ************");
		System.exit(0);
	}

	/*
	 * Create an input msg queue agent 'aname'.
	 */
	public void addMsgReceiver(String aname) {
		LinkedList msgQ = new LinkedList(Collections.synchronizedList(new LinkedList()));
		msgQs.put(aname, msgQ);
	}

	/*
	 * some agent wants to send message 'm' to 'aname'. 'cntFlag': count this
	 * message in the final stats?
	 */
	public void sendMessage(Message m, String aname, boolean cntFlag) {
		while (mutex)
			try {
				Thread.sleep(10);
			} catch (Exception e) {
			}
		mutex = true;
		LinkedList q = (LinkedList) msgQs.get(aname);
		if (q != null) {
			q.addLast(m);
			numsent++;
			if (cntFlag)
				msgCnt++;
		} else {
			System.out.println("Simulator.sendMessage(): Unknown agent: " + aname);
		}
		mutex = false;
	}

	/* agent 'aname' wants a message */
	public Message getMessage(String aname) {
		Message msg = null;
		while (mutex)
			try {
				Thread.sleep(10);
			} catch (Exception e) {
			}
		mutex = true;
		LinkedList q = (LinkedList) msgQs.get(aname);

		if (q != null) {
			try {
				msg = (Message) q.removeFirst();
			} catch (NoSuchElementException ex) {
				msg = null;
			}
			if (msg != null) {
				numrcvd++;
				mutex = false;
				return msg;
			}
		} else {
			System.out.println("Simulator.receiveMessage(): Unknown agent: " + aname);
		}
		mutex = false;
		return null;
	}

	public static void logAlgorithmName(Logger l, String algoName) {

		/* Do some logging *************/
		if (algoName.equals("adopt")) {
			Utility.Dprint("----------Algorithm: Adopt ----------", Utility.DEBUG);
			l.printToLogln("Algorithm Adopt");
		} else if (algoName.equals("master")) {
			Utility.Dprint("----------Algorithm: Master ----------", Utility.DEBUG);
		} else if (algoName.equals("sbb")) {
			Utility.Dprint("----------Algorithm: SBB ----------", Utility.DEBUG);
			l.printToLogln("Algorithm SBB");
		} else if (algoName.equals("sid")) {
			Utility.Dprint("----------Algorithm: SID ----------", Utility.DEBUG);
			l.printToLogln("Algorithm SID");
		}
		/* test algorithm with message loss */
		else if (algoName.startsWith("adopt-loss")) {
			int lr = new Integer(algoName.substring(algoName.lastIndexOf("s") + 1)).intValue();

			Utility.Dprint("----------Algorithm: Adopt (msgLoss" + lr + ") ----------", Utility.DEBUG);
			l.printToLogln("Algorithm Adopt (msgLoss" + lr + ")");
		}
		/* test algorithm with different solution quality error bounds */
		else if (algoName.startsWith("adopt-bound")) {
			int lr = new Integer(algoName.substring(algoName.lastIndexOf("d") + 1)).intValue();

			Utility.Dprint("----------Algorithm: Adopt (bound" + lr + ") ----------", Utility.DEBUG);
			l.printToLogln("Algorithm Adopt (bound" + lr + ")");
		} else {
			l.printToLogln("Algorithm Unknown");
		}
		/* End logging *************/
	}

	public static void main(String argv[]) {
		// Simulator sim = new Simulator(argv);
		// algorithmName = "adopt";
		// problemType = "maxcsp-tree";
		// inputFileName = "MaxCSP.txt";
		String argumentos[] = new String[3];
		argumentos[0] = "adopt";
		argumentos[1] = "maxcsp-tree";
		// argumentos[2] = "./problems/MaxCSP.txt";
		argumentos[2] = "./problems/ex4agentes.txt";
		// problemas extras estao na pasta problems.
		// argumentos[2] = "./problems/Problem-GraphColor-8_3_2_0.4_r0";
		Simulator sim = new Simulator(argumentos);
		sim.run();
	}
}
