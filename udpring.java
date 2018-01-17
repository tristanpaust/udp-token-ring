/*
* CHANGED RTT TO 20000 FOR LEADERS SINCE THE RING FAILS OTHERWISE IF ALL MESSAGES OF THE LEADER HAVE BEEN SENT
* WHY IS THAT
*/


import java.net.*;
import java.io.*;
import java.util.*;
import java.text.*;

// Generate random token for the leader
class Token {
 	static int token;
 		Token() {
 			token = new Random().nextInt(10000);
 			System.out.println("Token " + token + " generated.");
 		}
}

class Timer {
	static long timer;
	Timer() {
		timer = System.currentTimeMillis();
	}
}

class Message {
	public String message;
	int minutes;
	int seconds;
	long minutesL;
	long secondsL;
	long totalMS;

	public Message(int minutes, int seconds, String message) {
		this.minutes = minutes;
		this.seconds = seconds;
		this.message = message;
		this.minutesL = minutes * 60 * 1000;
		this.secondsL = seconds * 1000;
		this.totalMS = minutesL + secondsL;
	}
}

class UdpRing {
	// Socket declarations
	public static DatagramSocket socket = null;
	InetAddress hostAddress = null;
	public static long timer = System.currentTimeMillis();
	public static long join_time;
	public static long leave_time;

	// Used to check whether the ring is broken
	long lastMessageTime = 0;
	long rtt = 0;

	// General port variables
	volatile public static int lowestPort = 0;
	volatile public static int highestPort = 0;
	volatile public static int myPort = 0;
	volatile public static int nextHop = 0;
	volatile public static int priorHop = 0;

	// Thread declarations
  	Runnable receive, send, check;
  	Thread thread1, thread2, thread3;

  	// Message identifiers
  	String message1 = "HOP_REQUEST";
  	String message2 = "HOP_REPLY";
  	String message3 = "ELECT";
  	String message4 = "ELECTED";
  	String message5 = "POST";
  	String message6 = "TOKEN";
  	String message7 = "NEW";

  	// Election variables
  	Boolean keepSending = true;
  	volatile int forwardMine = 0;
  	volatile int forwardOther = 0;
  	volatile int electedLeader = 0;
  	volatile int originalSender = 0;

  	// Post variables
  	String forwardPost = "";
  	Boolean hasToken = false;

  	volatile Boolean probing = true;
  	volatile Boolean electing = false;
  	volatile Boolean spreadElection = false;
  	volatile Boolean postMessages = false;
  	volatile Boolean checkRing = false;

  	// Post message input storage
  	static Vector<Message> inputMessages = new Vector<Message>();

  	// Output file variables
  	static String outputFile;

  	String nextHopOutput = "next hop is changed to client ";
  	String lastHopOutput = "previous hop is changed to client ";
  	String electionOutput = "started election, send election message to client ";
  	String changeLeaderToMe = "relayed election message, replaced leader";
  	String changeLeaderToSomeone = "relayed election message, leader client ";
  	String leaderSelected = "leader selected ";
  	String tokenCreated = "new token generated ";
  	String sendToken1 = "token ";
  	String sendToken2 = "was sent to client ";
  	String receiveToken1 = "token ";
  	String receiveToken2 = "was received";
  	String receivePost1 = "post ";
  	String receivePost2 = "was received";
  	String sendPost1 = "post ";
  	String sendPost2 = "was sent";
  	String forwardPost1 = "post ";
  	String forwardPost2 = "from client ";
  	String forwardPost3 = "was relayed";
  	String successfulPost1 = "post ";
  	String successfulPost2 = "was delivered to all successfully";
  	String ringError = "ring is broken";
  	String tokenID;
  	String previousToken;

	public void RingFormation(int lowestPort, int highestPort, int myPort) {
		// Start Receiving and Sending thread, Open Socket
		System.out.println("Lowest Port: " + lowestPort + " Highest Port: " + highestPort + " My Port: " + myPort);
		try {
			hostAddress = InetAddress.getByName("localhost");
			socket = new DatagramSocket(myPort);
		
			receive = new ReceiveThread();
			send = new SendThread();
			check = new CheckThread();

			thread1 = new Thread(receive);
			thread2 = new Thread(send);
			thread3 = new Thread(check);				

			thread1.start();
			thread2.start();
			thread3.start();
		}
		catch (Exception e) {
			System.err.println("Error6: " + e.getMessage());
		}
	}

	String preparetoWrite(String prepareThis) {
		String[] splitThis = prepareThis.split("_");
		String lastPart = splitThis[splitThis.length-1];
		return lastPart;
	}

	public void writeToFile(String writeThis) {
		try{
			BufferedWriter outputWriter = new BufferedWriter(new FileWriter(outputFile, true));
			
	 		SimpleDateFormat formattedDate = new SimpleDateFormat("mm:ss");

      		Date date = new Date(System.currentTimeMillis()-timer);
      		String result = "[" + formattedDate.format(date) + "] ";

    		outputWriter.write(result + preparetoWrite(writeThis));
    		outputWriter.write("\n");
    		outputWriter.close();
		} catch (IOException e) {
   			e.printStackTrace();
			System.out.println("Error7: " + e.getMessage());
		}
	}

	// Keep on checking whether the ring is still working every 2*rtt if available or every 2 seconds if no rtt is available
	class CheckThread implements Runnable {
		public void run() {
			checkIfBroken();
		}

		public void checkIfBroken() {
			// Check if the ring is still working:
			// 2*round trip time if the client is the leader currently, as he can measure the time, otherwise use 2000ms for timeout
			// If the ring is broken, write to output file, reset all variables, kill this surveillance thread and start finding hops again
			try {
				while(!Thread.currentThread().isInterrupted()) {
					if (checkRing){
					if (rtt != 0) {
						if (System.currentTimeMillis() > (lastMessageTime + 2000)) {
							System.out.println("ring is broken.");
							// Write to file
							writeToFile(ringError);	
							// Reset variables
							nextHop = 0;
							priorHop = 0;
							forwardOther = 0;
							forwardMine = 0;
							electedLeader = 0;	
							originalSender = 0;	
							lastMessageTime = System.currentTimeMillis();
							//Reset flags
							probing = true;
							electing = false;
							spreadElection = false;
							postMessages = false;
							checkRing = false;
							hasToken = false;
							forwardPost = "";
						}
						else {
							thread3.sleep(1000);							
						}						
					}
					else {
						if (System.currentTimeMillis() > (lastMessageTime + 2000)) {
							System.out.println("ring is broken.");
							// Write to file
							writeToFile(ringError);
							// Reset variables
							nextHop = 0;
							priorHop = 0;
							forwardOther = 0;
							forwardMine = 0;
							electedLeader = 0;
							originalSender = 0;
							lastMessageTime = System.currentTimeMillis();
							// Reset flags
							probing = true;
							electing = false;
							spreadElection = false;
							postMessages = false;
							checkRing = false;
							hasToken = false;
							forwardPost = "";
						}
						else {
							thread3.sleep(2000);							
						}
					}
				}}
			}
							
			catch(Exception e) {
				e.printStackTrace();
				System.out.println("Error8: " + e.getMessage());
			}
		}
	}

	class ReceiveThread implements Runnable {
		private SendThread sendthread;
		// Return true if the message is an election message
		Boolean checkIfElectionMsg(String packetMessage) {
			String[] arrivedElection = packetMessage.split("_");
			if (arrivedElection[0].equals(message3)) {
				return true;
			}
			return false;
		}

		Boolean checkIfMyElectionMsg(String packetMessage) {
			String[] arrivedElection = packetMessage.split("_");
			if (Integer.parseInt(arrivedElection[1]) == myPort) {
				return true;
			}
			return false;
		}

		// Return true if the message contains a new leader port
		Boolean checkIfElectedLeader(String packetMessage) {
			String[] arrivedElection = packetMessage.split("_");
			if (arrivedElection[0].equals(message4)) {
				return true;
			}
			return false;
		}
		
		// Return true if the message contains a new leader port
		Boolean checkIfPostMsg(String packetMessage) {
			String[] arrivedPost = packetMessage.split("_");
			if (arrivedPost[0].equals(message5)) {
				return true;
			}
			return false;
		}
		
		// Return true if the message contains a token
		Boolean checkIfTokenMsg(String packetMessage) {
			String[] arrivedPost = packetMessage.split("_");
			if (arrivedPost[0].equals(message6)) {
				return true;
			}
			return false;
		}

		// Return true if the message contains a new leader port
		Boolean newClient(String packetMessage) {
			String[] arrivedMessage = packetMessage.split("_");
			if (arrivedMessage[0].equals(message7)) {
				return true;
			}
			return false;
		}

		// Return true if the message contains a token
		String getTokenMessage(String packetMessage) {
			String[] arrivedToken = packetMessage.split("_");
			if (arrivedToken[0].equals(message6)) {
				return arrivedToken[1];
			}
			return null;
		}

		// Get the port number of the original sender
		String getElectionSender(String packetMessage) {
			String[] arrivedElection = packetMessage.split("_");
			if ((arrivedElection[0]).equals(message3)) {
				return arrivedElection[1];
			}
			if (arrivedElection[0].equals(message4)) {
				return arrivedElection[1];
			}
			return null;
		}

		// Return the port number of both above cases
		String getElectionMessage(String packetMessage) {
			String[] arrivedElection = packetMessage.split("_");
			if ((arrivedElection[0]).equals(message3)) {
				return arrivedElection[2];
			}
			if (arrivedElection[0].equals(message4)) {
				return arrivedElection[1];
			}
			return null;
		}

		// Return the port number of the post sender
		String getPostID(String packetMessage) {
			String[] arrivedPost = packetMessage.split("_");
			if (arrivedPost[0].equals(message5)) {
				return arrivedPost[1];
			}
			return null;
		}

		// Return the post message
		String getPostMessage(String packetMessage) {
			String[] arrivedPost = packetMessage.split("_");
			if (arrivedPost[0].equals(message5)) {
				return arrivedPost[2];
			}
			return null;
		}

		public void run() {
			receive();
		}
	
		public void receive() {
			try {
				byte[] sendReply = new byte[1024]; 
				byte[] receiveData = new byte[1024];
					try {
						while(true) {
							// Leave the ring if the time is over
							if (System.currentTimeMillis() >= leave_time) {
								System.out.println("Now leaving at time: " + leave_time);
								System.out.println("System time is: " + System.currentTimeMillis());
								System.exit(0);
							}
							////
							// General Receiving
							////
							DatagramPacket receivePacket = new DatagramPacket(receiveData, 1024);
							socket.receive(receivePacket);
							int receivedPort = receivePacket.getPort();
    						String packetMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());

							////
							// Probing Part
							////
    						
							// send reply to prior hop
							if (packetMessage.equals(message1) && receivedPort >= priorHop) {
								System.out.println("Found my previous port: " + receivedPort);
								priorHop = receivedPort;
    							sendReply = message2.getBytes();
    							DatagramPacket sendReplyPacket = new DatagramPacket(sendReply, sendReply.length, hostAddress, receivedPort);
   								socket.send(sendReplyPacket); 
   								
   							}

   							// set nextHop
   							if (packetMessage.equals(message2)) {
								nextHop = receivedPort;
								writeToFile(nextHopOutput + "[port " + nextHop +"]");
								System.out.println("Found my next port: " + receivedPort);
							}

							////
							// Election Part
							////

							/*
							* Check if the message has an election flag
							* If the message contains my port, I am the leader, set electedLeader to myPort
							* If the incoming port is higher than mine, set forwardOther to that ID and forward it
							* If the incoming port is smaller than mine, forward mine instead of that 
							*/

							// Check if an incoming message contains an election message											
							if (checkIfElectionMsg(packetMessage) && electedLeader == 0) {
								if(checkIfMyElectionMsg(packetMessage) || (Integer.parseInt(getElectionSender(packetMessage)) == myPort)) {
									lastMessageTime = System.currentTimeMillis()+new Random().nextInt(1000);
								}

								String arrivedPort = getElectionMessage(packetMessage);
								originalSender = Integer.parseInt(getElectionSender(packetMessage));
								// The message contains my port, thus I am the leader
								if (Integer.parseInt(arrivedPort) == myPort) {
									lastMessageTime = System.currentTimeMillis();
									System.out.println("I am the leader");
									electedLeader = myPort;
									spreadElection = true;
									writeToFile(leaderSelected);
								}
					
								// The incoming port is higher than mine and higher than a prior incoming higher port
								if (Integer.parseInt(arrivedPort) > myPort && Integer.parseInt(arrivedPort) > forwardOther) {
									forwardOther = Integer.parseInt(arrivedPort);
									writeToFile(changeLeaderToSomeone + "[port " + Integer.toString(forwardOther) + "]");
								}
								// The incoming port is smaller than mine, start changing the port in the message to my port number
 								if (Integer.parseInt(arrivedPort) < myPort) {
									forwardMine = myPort;
									writeToFile(changeLeaderToMe);
 								}
							}

							// Check if the message contains a new leader message
							if (checkIfElectedLeader(packetMessage)) {
								String newLeader = getElectionMessage(packetMessage);
								lastMessageTime = System.currentTimeMillis()+1000;
								System.out.println("A leader has been elected!" + newLeader);
								electedLeader = Integer.parseInt(newLeader);
								electing = false;
								spreadElection = true;
								// The message made a full circle, leader creates a token and starts posting messages
								if (Integer.parseInt(newLeader) == myPort) {
									System.out.println("The leader message has made a full circle!");
									// Create a token used to identify the leader, start actual message part, set token flag to true
									Token token = new Token();
									tokenID = Integer.toString(Token.token);
									writeToFile(tokenCreated + "[" + tokenID + "]");
									hasToken = true;
									electing = false;
									spreadElection = false;
									postMessages = true;
								}
							}
   					
							////
							// Post Message Part
							////

	   						/*
	   						* Check if the incoming message is of the type post
	   						* Check if the ID included is my ID
	   						* If it is not, set forwardPost to the original message and send it to the next hop
	   						* If it is my ID, remove the first item from the message list and call send again
							*/

	   						// If you are already in posting state and still have no previous port, use the next incoming one
	   						// Sometimes the information about the previous port gets lost when resetting too late, hence this workaround
	   						if (postMessages && priorHop == 0) {
	   							priorHop = receivedPort;
	   						}
	   						if (postMessages && packetMessage.equals(message1)) {
	   							priorHop = receivedPort;
	   						}
							if (checkIfPostMsg(packetMessage)) {
								spreadElection = false;
								electing = false;
								if(Integer.parseInt(getPostID(packetMessage)) != myPort) {
									postMessages = true;
									String newPost = getPostMessage(packetMessage);
									forwardPost = packetMessage;
									lastMessageTime = System.currentTimeMillis();	
  									writeToFile(receivePost1 + "\"" + preparetoWrite(forwardPost) + "\" " + receivePost2);
  									writeToFile(forwardPost1 + "\"" + preparetoWrite(forwardPost) + "\" " + forwardPost2 + "[port " + Integer.toString(Integer.parseInt(getPostID(packetMessage))) + "] " + forwardPost3);	
								}
								if ((Integer.parseInt(getPostID(packetMessage)) == myPort) && receivedPort == priorHop) {
									rtt = System.currentTimeMillis() - lastMessageTime;
									lastMessageTime = System.currentTimeMillis();
									postMessages = true;	
									if(!inputMessages.isEmpty()) {									
										synchronized (inputMessages) { 
	    									for (Iterator<Message> processPosts = inputMessages.iterator(); processPosts.hasNext(); ) {
												Message processMessage = processPosts.next();
												if(getPostMessage(packetMessage).equals(processMessage.message)) {
													processPosts.remove();
													writeToFile(successfulPost1 + "\"" + preparetoWrite(packetMessage) + "\" " + successfulPost2);
												}
											}
										}
									}
								}	
		   					}								
					

						////
						// Token Part
						////

						// The leader is done posting and passes his leadership along, together with the token
							if (checkIfTokenMsg(packetMessage) && receivedPort == priorHop) {
								// Set token flag to true, start posting
								hasToken = true;
								tokenID = getTokenMessage(packetMessage);
								lastMessageTime = System.currentTimeMillis();									
								spreadElection = false;
								electing = false;									
								postMessages = true;
								// Stop forwarding
								forwardPost = "";
								electedLeader = myPort;
								System.out.println("The token " +tokenID+  " has arrived at the next hop");
								if (previousToken == null || !previousToken.equals(tokenID)) {
									writeToFile(receiveToken1 + "[" + getTokenMessage(packetMessage) + "] " + receiveToken2);
									previousToken = tokenID;
								}
							}
							
   					}}
    				catch(SocketTimeoutException e) {
    				}
				}
				catch(Exception e) {
					e.printStackTrace();
					System.out.println("Error9: " + e.getMessage());
				}
			}
		}

	class SendThread implements Runnable {
		public void run() {
			send();
		}

		/*
		* Send test messages to the next port available, starting from myPort+1
		* If the last available port is reached without a reply, start probing from the very first port
		* If the next hop is found, start probing and keep on replying to probing messages to find the prior hop
		* If both next and prior hop are found, break	
		*/

		public void send() {
			try {
				while(true) {
					if (probing) {
						byte[] sendRequest = message1.getBytes();
						// THe the port is not the highest available port
						if (myPort != highestPort) {
							for(int i = myPort; i <= highestPort; i++) {
								// Established connection to next hop, start the election for a leader
								if (nextHop != 0){
									writeToFile(nextHopOutput + "[port " + nextHop + "]");
									writeToFile(electionOutput + "[port " + nextHop +"]");
									thread2.sleep(new Random().nextInt(1000));
									probing = false;
									electing = true;
									lastMessageTime = System.currentTimeMillis();	
									break;
								}

								// Both the prior and the next hop are known, stop probing
								if (priorHop !=0 && nextHop !=0){								
								  	writeToFile(lastHopOutput + "[port " + priorHop + "]");
									break;
								}

								if (myPort != i) {
									DatagramPacket sendRequestPacket = new DatagramPacket(sendRequest, sendRequest.length, hostAddress, i);
									socket.send(sendRequestPacket);
								}
								// The highest port has been reached without a reply, hence my port is the highest. Start from the lowest port 								
								if (i == highestPort){
									i = lowestPort-1;
								}


								thread2.sleep(100);
		    				}
	    				}
	    				// If the port number is the highest port possible
	    				else {
							for(int i = lowestPort; i <= highestPort; i++) {
								// Established connection to next hop, start the election for a leader
								if (nextHop !=0){
									writeToFile(nextHopOutput + "[port " + nextHop + "]");
									//writeToFile(electionOutput + "[port " + nextHop +"]");
									thread2.sleep(1000);
									probing = false;
									electing = true;
									lastMessageTime = System.currentTimeMillis();	
									break;									
								}
								// Both the prior and the next hop are known, stop probing
								if (priorHop !=0 && nextHop !=0){
									lastMessageTime = System.currentTimeMillis();
								  	writeToFile(lastHopOutput + "[port " + priorHop + "]");	
								  	break;			
								}
								if (myPort != i) {
									DatagramPacket sendRequestPacket = new DatagramPacket(sendRequest, sendRequest.length, hostAddress, i);
									socket.send(sendRequestPacket);	
									thread2.sleep(100);
								}
								// The highest port has been reached without a reply, hence my port is the highest. Start from the lowest port
								if (i == highestPort){
									i = lowestPort-1;
								}							
		    				}    					
	    				}
					} /* End Probing */

					if (electing) {
						//lastMessageTime = System.currentTimeMillis();
						checkRing = true;
						// No other messages have been seen at all, hence just send my port to next port
						if(forwardMine == 0 && forwardOther == 0 && electedLeader == 0) {
							String delimiter = message3+"_"+Integer.toString(myPort)+"_";
							String electionString = delimiter + Integer.toString(myPort);
							byte[] electionMessage = electionString.getBytes();
							DatagramPacket sendElectionPacket = new DatagramPacket(electionMessage, electionMessage.length, hostAddress, nextHop);

							if (nextHop != 0) {
								socket.send(sendElectionPacket);
							}
							thread2.sleep(100);
						}
						// Only smaller port messages showed up, thus change it to my number pass along
						if(forwardMine != 0 && forwardOther == 0 && electedLeader == 0) {
							String delimiter = message3+"_"+ Integer.toString(originalSender)+"_";
							String electionString = delimiter + Integer.toString(forwardMine);
							byte[] electionMessage = electionString.getBytes();
							DatagramPacket sendElectionPacket = new DatagramPacket(electionMessage, electionMessage.length, hostAddress, nextHop);

							if (nextHop != 0) {
								socket.send(sendElectionPacket);
							}
							thread2.sleep(100);
						}
						// An incoming message contained a higher port number, thus it will be forwarded from now on
						if(forwardOther != 0 && electedLeader == 0) {
							String delimiter = message3+"_"+ Integer.toString(originalSender)+"_";
							String electionString = delimiter + Integer.toString(forwardOther);
							byte[] electionMessage = electionString.getBytes();
							DatagramPacket sendElectionPacket = new DatagramPacket(electionMessage, electionMessage.length, hostAddress, nextHop);

							if (nextHop != 0) {
								socket.send(sendElectionPacket);
							}
							thread2.sleep(100);
						}
						if(spreadElection) {
							electing = false;
							spreadElection = false;
							postMessages = true;
							String delimiter = message4+"_";
							String electionString = delimiter + Integer.toString(electedLeader);
							byte[] electionMessage = electionString.getBytes();
							DatagramPacket sendElectionPacket = new DatagramPacket(electionMessage, electionMessage.length, hostAddress, nextHop);
							socket.send(sendElectionPacket);
							thread2.sleep(100);
						}
					} /* End Electing */

					if (postMessages) {
						if (inputMessages.isEmpty() && hasToken) {
							System.out.println("Done!");
							rtt = 0;
							lastMessageTime = System.currentTimeMillis();
							String delimiter = message6+"_";
							String tokenString = delimiter + tokenID;
							byte[] tokenMessage = tokenString.getBytes();
							DatagramPacket sendTokenPacket = new DatagramPacket(tokenMessage, tokenMessage.length, hostAddress, nextHop);
							socket.send(sendTokenPacket);
							hasToken = false;
							//electedLeader = 0;
							if (previousToken == null || !previousToken.equals(tokenID)) {
								writeToFile(sendToken1 + "[" + tokenID + "] " + sendToken2 + "[port " + nextHop + "]");
								previousToken = tokenID;
							}
							thread2.sleep(100);
						}
						else if(hasToken) {
							lastMessageTime = System.currentTimeMillis();
							synchronized (inputMessages) { 
								Iterator<Message> processPosts = inputMessages.iterator();
								while (processPosts.hasNext()) {
									Message processMessage = processPosts.next();
									if(System.currentTimeMillis() - timer > processMessage.totalMS) {
																postMessages = false;

										String delimiter = message5+"_"+myPort+"_";
										String postMsg = delimiter + processMessage.message;
										byte[] postMessage = postMsg.getBytes();
										DatagramPacket sendMessagePacket = new DatagramPacket(postMessage, postMessage.length, hostAddress, nextHop);
										socket.send(sendMessagePacket);
										// Set variable to current time, if RTT is too long, start probing again
										thread2.sleep(100);
										}
									}
							}
							if (!inputMessages.isEmpty()) {
								rtt = 0;
								System.out.println("Done for now!");
								String delimiter = message6+"_";
								String tokenString = delimiter + tokenID;
								byte[] tokenMessage = tokenString.getBytes();
								DatagramPacket sendTokenPacket = new DatagramPacket(tokenMessage, tokenMessage.length, hostAddress, nextHop);
								socket.send(sendTokenPacket);
								hasToken = false;
								//electedLeader = 0;
								if (previousToken == null || !previousToken.equals(tokenID)) {
									writeToFile(sendToken1 + "[" + tokenID + "] " + sendToken2 + "[port " + nextHop + "]");
									previousToken = tokenID;
								}
								thread2.sleep(100);
							}
						}
						else if(!hasToken && !forwardPost.isEmpty()) {
							System.out.println("Forwarding post " + forwardPost);
							String postMsg =  forwardPost;
							byte[] postMessage = postMsg.getBytes();
							DatagramPacket sendMessagePacket = new DatagramPacket(postMessage, postMessage.length, hostAddress, nextHop);
							socket.send(sendMessagePacket);
							forwardPost = "";
							thread2.sleep(100);
						}
					} /* End Posting */
				}
			}
			catch(Exception e) { 
				e.printStackTrace(); 
				System.out.println("Error1: " + e.getMessage());
			}
		}
	}

	// Read the config input file
	static void readConfig(Scanner config){
		try{
			File file = new File(config.nextLine());
            config = new Scanner(file);
			while (config.hasNextLine()) {
				String line = config.nextLine();
				String[] tokens = line.split(" ");
				if (line.startsWith("client_port")) {
					String[] ports = tokens[1].split("-");
					lowestPort = Integer.parseInt(ports[0]);
					highestPort = Integer.parseInt(ports[1]);
				}
				if (line.startsWith("my_port")) {
					myPort = Integer.parseInt(tokens[1]);
				}
				if (line.startsWith("join_time")) {
					String[] time = tokens[1].split(":");
					long time1 = Integer.parseInt(time[0]) * 60000;
					long time2 = Integer.parseInt(time[1]) * 1000;
					join_time = System.currentTimeMillis() + (time1+time2);
					System.out.println("Client joins in: " + (time[0]) + " minutes and " + (time[1]) + " seconds.");
				}
				if (line.startsWith("leave_time")) {
					String[] time = tokens[1].split(":");
					long time1 = Integer.parseInt(time[0]) * 60000;
					long time2 = Integer.parseInt(time[1]) * 1000;
					System.out.println("Client leaves in: " + (time[0]) + " minutes and " + (time[1]) + " seconds.");
					leave_time = System.currentTimeMillis() + (time1+time2);
				}
			}
			config.close();	
		}
		catch (Exception e) {
			fileNotFound();
	        System.exit(0);	
		}
	}

	// Read the message input file
	static void readInput(Scanner input){
		try{
			File file = new File(input.nextLine());
            input = new Scanner(file);

			while (input.hasNextLine()) {
				String line = input.nextLine();
				if (line.trim().length() > 0) {
					String[] time = line.split(":");
					String[] tokens = time[1].split("\t");
					Message obj = new Message(Integer.parseInt(time[0]), Integer.parseInt(tokens[0]), tokens[1]);
					inputMessages.add(obj);
				}
			}
			input.close();	
		}
		catch (Exception e) {
			fileNotFound();
	        System.exit(0);	
		}
	}

	// Error if arguments do not match
	static void inputErrorMsg() {
		String newline = System.getProperty("line.separator");
		System.out.print(
			newline + "Input Error! You did not supply the needed amount of input arguments." + newline + 
			newline + "Usage:" + newline +
			"-c Configfile >> Specfies the configuration for every client containing port information, as well as join and leave time" + newline +
			"-i Inputfile >> Specifies the input file for every client containing message this client will broadcast" + newline +
			"-o Outputfile >> Specifies the output file every client creates to write the output" + newline
		);
	}
	// Error if file can not be opened
	static void fileNotFound() {
		String newline = System.getProperty("line.separator");
		System.out.print(
			newline + "File Error! One of the files you supplied can not be opened or it does not exist" + newline 
		);
	}

	// Loop until join_time is reached, then start probing
	public static void addClient(long s) {
		if (System.currentTimeMillis() >= s) {
			UdpRing client = new UdpRing(); 
        	client.RingFormation(lowestPort, highestPort, myPort);
		}
		else {
        	while (s > 0) { 
            	if (s == System.currentTimeMillis()) {
        			UdpRing client = new UdpRing(); 
        			client.RingFormation(lowestPort, highestPort, myPort);
        			break;
            	}
        	}
    	}
    }

	// Process input
	public static void main(String args[]) {
		if(args.length == 6) {
			// Pass config and input file to function
			if (args[0].equals("-c") && args[2].equals("-i") && args[4].equals("-o")) {
				try {
					Scanner config = new Scanner(args[1]);
					Scanner input = new Scanner(args[3]);
					outputFile = args[5];
					readConfig(config);
					readInput(input);
					addClient(join_time);

				}
				catch (Exception e) {
					System.err.println("Error: " + e.getMessage());
	        	}
        	}
        }
        else {
	        inputErrorMsg();
	        System.exit(0);
        }	
	}
}