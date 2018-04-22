package ddejonge.bandana.exampleAgents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import ddejonge.bandana.anac.ANACNegotiator;
import ddejonge.bandana.dbraneTactics.DBraneTactics;
import ddejonge.bandana.dbraneTactics.Plan;
import ddejonge.bandana.gameBuilder.DiplomacyGameBuilder;
import ddejonge.bandana.internalAdjudicator.InternalAdjudicator;
import ddejonge.bandana.negoProtocol.BasicDeal;
import ddejonge.bandana.negoProtocol.DMZ;
import ddejonge.bandana.negoProtocol.Deal;
import ddejonge.bandana.negoProtocol.DiplomacyNegoClient;
import ddejonge.bandana.negoProtocol.DiplomacyProposal;
import ddejonge.bandana.negoProtocol.OrderCommitment;
import ddejonge.bandana.tools.Logger;
import ddejonge.bandana.tools.Utilities;
import ddejonge.negoServer.Message;
import es.csic.iiia.fabregues.dip.board.Game;
import es.csic.iiia.fabregues.dip.board.Phase;
import es.csic.iiia.fabregues.dip.board.Power;
import es.csic.iiia.fabregues.dip.board.Province;
import es.csic.iiia.fabregues.dip.board.Region;
import es.csic.iiia.fabregues.dip.comm.CommException;
import es.csic.iiia.fabregues.dip.orders.HLDOrder;
import es.csic.iiia.fabregues.dip.orders.MTOOrder;
import es.csic.iiia.fabregues.dip.orders.Order;
import es.csic.iiia.fabregues.dip.orders.SUPMTOOrder;
import es.csic.iiia.fabregues.dip.orders.SUPOrder;
import es.csic.iiia.fabregues.dip.Observer;


public class MyANACNegotiator extends ANACNegotiator{
	
	/**
	 * Main method to start the agent.
	 * @param args
	 */
	public static void main(String[] args){

		
		MyANACNegotiator myPlayer = new MyANACNegotiator(args);
		myPlayer.run();
		
	}
	
	/*-----------------------FIELDS--------------------------*/
	//in case of using random fields, adding this random field first -- 2016.12.12
	public Random random = new Random();	
	
	DBraneTactics dBraneTactics;
	
	//adding 2 utilities in field first
	private InternalAdjudicator adj;
	private DiplomacyGameBuilder gameBuilder;
	
	//including logger but don't know whether this is the correct way
	private Logger logger;
	
	//including confirmed deals arraylist but don't know whether this is the correct way
	private List<BasicDeal> confirmedDeals;
	
	//including allies list, hostility list, list of my units (last round)
	private List<Power> allies;
	private Map<String, Integer> hostilityList;
	private Map<String, Double> nHostilityList;
	private Map<String, Double> strengthList;
	private List<String> listOfMyUnitString;
	
	//include a universal utility list of the map
	private Map<String, Double> myUtilityList;

	//include game counter for logging purpose
	private int gameCounter;
	
	//include the dealPool - powerStr : issue : <deals related to this issue>
	//include currently proposed deals = powerStr : dealProposed : related issue
	//include the reservationPool - powerStr : issue : DMZ
	private Map<String, Map<String, List<String>>> dealPool; 
	private Map<String, Map<BasicDeal, String>> currentlyProposedDeals;
	private Map<String, Map<String, BasicDeal>> reservationPool;
	
	//include returnCredit, number of favours affordable to return
	private int returnCredit;
		 	
	/*----------------------Constructor----------------------*/
	/**
	 * You must implement a Constructor with exactly this signature.
	 * The body of the Constructor must start with the line <code>super(args)</code>
	 * but below that line you can put whatever you like.
	 * @param args
	 */
	public MyANACNegotiator(String[] args) {
		super(args);
		
		//initialisation of all fields
		dBraneTactics = this.getTacticalModule();
		adj = new InternalAdjudicator();
		gameBuilder = new DiplomacyGameBuilder();
		logger = this.getLogger();
		confirmedDeals = this.getConfirmedDeals();
		allies = new ArrayList<Power>();
		gameCounter = 1;
		
		//initialise the hostility list with an empty hashmap
		hostilityList = new HashMap<String, Integer>();
		nHostilityList = new HashMap<String, Double>();
		
		//initialise strength list
		strengthList = new HashMap<String, Double>();
		
		//initialise the unit list with an empty arraylist
		listOfMyUnitString = new ArrayList<String>();
		
		//initialist utility list
		myUtilityList = new HashMap<String, Double>();
		
		//initialise the deal pool
		dealPool = new HashMap<String, Map<String, List<String>>>();
		
		//initialise the current proposal tracker
		currentlyProposedDeals = new HashMap<String, Map<BasicDeal, String>>();
		
		//initialise the reservation pool
		reservationPool = new HashMap<String, Map<String, BasicDeal>>();
		
		//initialise the return credit to 1
		returnCredit = 1;
	}
	
	/*----------------Agent's main methods----------------------*/
	@Override
	public void start() {
		loglnC("Starting MyANACNegotiator...");
	}

	@Override
	public void negotiate(long deadline) {

		// Negotiation algorithm here. Make sure the method returns before the deadline
		
		logln("Starting negotiation...");
		
		//let return credit be 1/3 of your army
		returnCredit = me.getControlledRegions().size() / 3;
		if(returnCredit == 0) returnCredit = 1;				//at least 1 for return favour
		
		//first round, initialise some fields
		if(hostilityList.isEmpty()){
			//first round, initialise it
			List<Power> allPowers = game.getPowers();
			for(Power power: allPowers){
				hostilityList.put(power.getName(), 0);
			}
		}
		normaliseHostilityList();
		
		if(listOfMyUnitString.isEmpty()){
			List<Region> myUnits = me.getControlledRegions();
			for(Region unit: myUnits){
				listOfMyUnitString.add(unit.getName());
			}
		}
		
		//compute heuristics before negotiation
		if(myUtilityList.isEmpty()){
			logln("Computing my utility...");
			myUtilityList = computeHeuristics();
		}
		
		//reset deal pool, map tracker and reservation pool
		if(dealPool.isEmpty()){
			for(Power power: game.getPowers()){
				Map<String, List<String>> issueToRelatedDeals = new HashMap<String, List<String>>();
				dealPool.put(power.getName(), issueToRelatedDeals);
			}
		}
	
		//don't reset currently proposed deals, for tracking
		if(currentlyProposedDeals.isEmpty()){
			for(Power power: game.getPowers()){
				Map<BasicDeal, String> issueMap = new HashMap<BasicDeal, String>();
				currentlyProposedDeals.put(power.getName(), issueMap);
			}
		}
		
		for(Power power: game.getPowers()){
			Map<String, BasicDeal> issueToDMZs = new HashMap<String, BasicDeal>();
			reservationPool.put(power.getName(), issueToDMZs);
		}

		//check who attack us first, before negotiating
		checkWhoHitUs();
		
		//if it is Spring period, update owned SCs
		if(game.getPhase().equals(Phase.SPR))	opponentStrength();		//need to check implementation also
		
		while(System.currentTimeMillis() < deadline){

			//STEP 1: Handle incoming messages
			while(hasMessage()){
				messageHandler();	
				
				// just in case the other agents send so many proposals, break out
				if(deadline - System.currentTimeMillis() < 2000) break;
			}
			
			//STEP 2: Propose deals
			biddingStrategy();
		}
		
		logln("New hostility list: ");
		for(String powerName : nHostilityList.keySet()){
			logln(powerName + " " + nHostilityList.get(powerName));
		}
		logln("New ally list: " + allies);
		gameCounter++;
		
		loglnC("Negotiation ended.");

	}

	@Override
	public void receivedOrder(Order order) {}
	
	/*----------------Heuristics Computing--------------------*/
	private Map<String, Double> computeHeuristics(){
		/**
		 * Compute heuristic utility values of all provinces and store in a hashmap
		 */
		logln("Computing heuristics...");
		List<Province> allProvinces = game.getProvinces();
		Map<String, Double> utilityList = new HashMap<String, Double>();
		for (Province pro: allProvinces){
			double utility = 0;
			if(pro.isSC()) utility = 10;	else utility = 1;
			
			List<Province> neighbourProvinces = game.getAdjacentProvinces(pro);
			for (Province nPro: neighbourProvinces){
				double discountFactor = 0.3;
				if(nPro.isSC()) utility += 10*discountFactor;	else utility += 1*discountFactor;
			}
			
			utilityList.put(pro.getName(), utility);
		}
		
		//normalisation
		double upper = Collections.max(utilityList.values());
		for(String pro: utilityList.keySet()){
			double newUtil = utilityList.get(pro) / upper;
			utilityList.put(pro, newUtil);
		}
		return utilityList;
	}
		
	/*-------------Opponent Modeling---------------*/
	private void checkWhoHitUs(){
		logln("Checking who attacked us...");
		List<Region> myUnitsForNow = me.getControlledRegions();
		List<String> myUnitStringForNow = new ArrayList<String>();
		for(Region unit: myUnitsForNow){
			myUnitStringForNow.add(unit.getName());
		}
		
		logln("myUnitStringForNow: " + myUnitStringForNow);
		logln("listOfMyUnitString: " + listOfMyUnitString);
		
		for(String previousUnit: listOfMyUnitString){
			if(!myUnitStringForNow.contains(previousUnit)){	//I lost control of some regions
				//check what kind of regions we loss. It may be just because we left the region
				log("Lost control of: " + previousUnit + ". ");
				
				Region regionOfThisUnit = game.getRegion(previousUnit);
				if(regionOfThisUnit.getProvince().isSC()){
					log(previousUnit + " is an SC. ");
					//if it is an SC, beware to see if anyone stole it
					Power potentialHostile = game.getController(regionOfThisUnit);
					if(potentialHostile != null){
						//someone really stole it! alert!
						String enemyName = potentialHostile.getName();
						log(" Its controller is " + enemyName);
						int hostilityValue = hostilityList.get(enemyName) - 10;
						hostilityList.put(enemyName, hostilityValue);
						normaliseHostilityList();
						logln(" Enemy spotted!" + enemyName + " took our supply center at " + previousUnit + "!");
					}
					else{
						log(" Its controller is null.");
					}
				}
			}
		}
		
		//update hostility list
		listOfMyUnitString = myUnitStringForNow;
	}
	
	private void opponentStrength(){
		for(Power power: game.getPowers()){
			//update SC owned number every round
			strengthList.put(power.getName(), 0.0);
		}
		for(Province province: game.getProvinces()){
			if(province.isSC() && game.getOwner(province) != null){
				String ownerStr = game.getOwner(province).getName();
				strengthList.put(ownerStr, strengthList.get(ownerStr) + 1);
			}
		}
		//normalisation
		for(String power: strengthList.keySet()){
			double strength = strengthList.get(power);
			double normalisedStrength = 0.5 * Math.sin(Math.PI * (strength - 9) / 18) + 0.5;
			strengthList.put(power, normalisedStrength);
		}
		
		logln("\nOpponent Strength List:");
		for(String power: strengthList.keySet()){
			logln(power + " " + strengthList.get(power));
		}
	}
	
	private void normaliseHostilityList(){
		double upperBound = (double)Collections.max(hostilityList.values());
		double lowerBound = (double)Collections.min(hostilityList.values());
		for(String power: hostilityList.keySet()){
			double nHostility = 0;
			double hostility = (double)hostilityList.get(power);
			if(hostility < 0){
				nHostility = 0.5 * ((hostility - lowerBound) / (0 - (lowerBound + 1)));
			}
			else{
				nHostility = 0.5 * (hostility / (upperBound + 1)) + 0.5;
			}
			nHostilityList.put(power, nHostility);
		}
	}
	
	/*-----------------Acceptance strategy-------------------*/
	private void messageHandler(){
		
		Message receivedMessage = removeMessageFromQueue();
		
		if(receivedMessage.getPerformative().equals(DiplomacyNegoClient.PROPOSE)){
			
			DiplomacyProposal receivedProposal = (DiplomacyProposal)receivedMessage.getContent();
			loglnC("Proposal incoming: " + receivedProposal);
			BasicDeal deal = (BasicDeal)receivedProposal.getProposedDeal();
			
			if(!checkIfOutdated(deal)){
				//check if deal is outdated. if not, launch acceptance strategy
				acceptanceStrategy(receivedProposal);
			} else logln("Ignore outdated proposal.");					
		}
		
		else if(receivedMessage.getPerformative().equals(DiplomacyNegoClient.ACCEPT)){
			
			DiplomacyProposal acceptedProposal = (DiplomacyProposal)receivedMessage.getContent();
			loglnC("ACCEPTANCE INCOMING: " + receivedMessage.getSender() + ": " + acceptedProposal);			
			BasicDeal acceptedDeal = (BasicDeal)acceptedProposal.getProposedDeal();
			
			//Normally, accepted deals are not proposed by us. Because we do bilateral deals
		}
		
		else if(receivedMessage.getPerformative().equals(DiplomacyNegoClient.CONFIRM)){
			// From now on we consider the deal as a binding agreement.
			
			
			DiplomacyProposal confirmedProposal = (DiplomacyProposal)receivedMessage.getContent();
			loglnC("CONFIRMED DEALS INCOMING: " + confirmedProposal);
			BasicDeal confirmedDeal = (BasicDeal)confirmedProposal.getProposedDeal();
			
			//REMOVE THIS LINE IF CONFIRMED DEALS ARE AUTOMATICALLY ADDED
			//if so, there isn't a need to store confirmed deals as a field
			confirmedDeals.add(confirmedDeal);
			//logln("Confirmed deals: " + confirmedDeals.toString());
			
			//only update hostility list when deals are confirmed
			List<OrderCommitment> confirmedOCs = confirmedDeal.getOrderCommitments();
			for(OrderCommitment oc: confirmedOCs){
				Order order = oc.getOrder();
				if(isSupportHold(order) || isSupportMove(order)){	//if the order is a support order
					if(isBenificiary(order)){						//and I am a benificiary
						//most probably that supporter is a friend, update the hostility list
						String helpingPowerString = order.getPower().getName();		
						
						//TODO:THIS NEEDS FURTHER IMPROVEMENT. for now, each support earns 5 points - 2017.2.5
						int newHostility = hostilityList.get(helpingPowerString) + 5;
						hostilityList.put(helpingPowerString, newHostility);
						logln("Spotted a friend here!");
						logln("Hostility list updated: " + helpingPowerString + " " + hostilityList.get(helpingPowerString));
					}
				}
			}
			normaliseHostilityList();
			
			//If deals confirmed, that issue could be closed. Clear the arraylist of the issue
			
			List<String> participants = confirmedProposal.getParticipants();
			participants.remove(me.getName());
			String targetPowerStr = participants.get(0);		//assume only 2 participants inside!
			loglnC("Accept power string: " + targetPowerStr);
			
			//for below, assume that identical deals have identical strings
			String issue = null;
			String dealStr = "";
			logln(currentlyProposedDeals.toString());
			for(BasicDeal deal: currentlyProposedDeals.get(targetPowerStr).keySet()){
				if(deal.toString().equals(confirmedDeal.toString())){	//use strings to compare, hopefully it works
					logln("This basic deal got accepted! " + deal.toString());
					issue = currentlyProposedDeals.get(targetPowerStr).get(deal);
					dealStr = deal.toString();
					break;
				}
			}
			
			if(issue == null) 
				logln("The deal accepted is not proposed by us.");
			
			else{
				//elicit the issue string
				Map<String, List<String>> issueToDeal = dealPool.get(targetPowerStr);
				List<String> deals = issueToDeal.get(issue);
				if(!deals.contains(dealStr)) loglnC("PROBLEM (386): Deal not in dealPool but accepted!");
				else{
					//clear the deal arraylist to close the issue
					//one of the deals got accepted, issue can close
					deals.clear();
					issueToDeal.put(issue, deals);
					dealPool.put(targetPowerStr, issueToDeal);
				}
			}
		}
		
		else if(receivedMessage.getPerformative().equals(DiplomacyNegoClient.REJECT)){
			
			DiplomacyProposal rejectedProposal = (DiplomacyProposal)receivedMessage.getContent();
			loglnC("REJECT INCOMING: " + receivedMessage.getSender() + ": " + rejectedProposal);
			BasicDeal rejectedDeal = (BasicDeal)rejectedProposal.getProposedDeal();
			
			//If rejected, propose the next deal in the queue if we still have a deal -- 2017.2.26
			//UPDATE: if rejected, just leave it. We propose the reservation deals when all other deals are rejected 
			List<String> participants = rejectedProposal.getParticipants();
			participants.remove(me.getName());
			String targetPowerStr = participants.get(0);
			System.out.println("Reject power string: " + targetPowerStr);
			
			//for below, assume that identical deals have identical strings
			/*boolean isFound = false;
			for(BasicDeal deal: currentlyProposedDeals.get(targetPowerStr).keySet()){
				if(deal.toString().equals(rejectedDeal.toString())){	//use strings to compare, hopefully it works
					System.out.println("Found the rejected deal");
					
					String issue = currentlyProposedDeals.get(targetPowerStr).get(deal);
					dealPool.get(targetPowerStr).get(issue).poll();
					BasicDeal nextDealToPropose = dealPool.get(targetPowerStr).get(issue).peek();
					
					if(nextDealToPropose != null){
						//update the tracker map
						Map<BasicDeal, String> dealToIssueMap = new HashMap<BasicDeal, String>();
						dealToIssueMap.put(nextDealToPropose, issue);
						currentlyProposedDeals.put(targetPowerStr, dealToIssueMap);
						printAndProposeDeal(nextDealToPropose);
						
						logln("Found next deal for proposal: " + nextDealToPropose);
					}
					
					isFound = true;
					break;
					
				}
			}
			
			if(!isFound) logln("Can't find any deals identical to the power in deal pool!");*/

		}else{
			//We have received any other kind of message.
			logC("Received a message of unhandled type: " + receivedMessage.getPerformative() + ". ");
			logC("Message content: " + receivedMessage.getContent().toString());
			loglnC("");
		}
	}
	
	private boolean checkIfOutdated(BasicDeal deal){
		
		// Sometimes we may receive messages too late, so we check if the proposal does not
		// refer to some round of the game that has already passed.
		
		boolean outDated = false;
		for(DMZ dmz : deal.getDemilitarizedZones()){

			if(isHistory(dmz.getPhase(), dmz.getYear())){
				outDated = true;
				break;
			}

		}
		for(OrderCommitment orderCommitment : deal.getOrderCommitments()){
			
			if( isHistory(orderCommitment.getPhase(), orderCommitment.getYear())){
				outDated = true;
				break;
			}
		}
		return outDated;
	}
		
	private void acceptanceStrategy(DiplomacyProposal receivedProposal){
		
		logln("Running acceptance strategy...");
		
		BasicDeal deal = (BasicDeal)receivedProposal.getProposedDeal();
		
		//first check if the deal matches best plan, though a bit unrealistic
		List<DMZ> receivedDMZs = deal.getDemilitarizedZones();
		List<OrderCommitment> receivedOCs = deal.getOrderCommitments();
		
		//maintain a probability list for now, may do more than finding the average probability in future
		//--2017.2.14
		ArrayList<Double> acceptProbList = new ArrayList<Double>();
		
		//handle order commitments
		for(OrderCommitment order: receivedOCs){
			if(order.getYear() != game.getYear() || !order.getPhase().equals(game.getPhase())){
				logln("Proposed deal is not for this round. Marked unsure...");
				acceptProbList.add(0.0);	//for unsure cases, add 0 probability. Do not accept it
			}
			else{
				Order orderinOC = order.getOrder();
				if(orderinOC.getPower().getName().equals(me.getName())){
					//if the order involves me, run acceptance strategy
					if(orderinOC instanceof HLDOrder){
						logln("This HLDOrder's probability: " + acceptHoldOrder((HLDOrder)orderinOC));
						acceptProbList.add(acceptHoldOrder((HLDOrder)orderinOC));
					}
					else if (orderinOC instanceof MTOOrder){
						logln("This MTOOrder's probability: " + acceptMoveOrder((MTOOrder)orderinOC));
						acceptProbList.add(acceptMoveOrder((MTOOrder)orderinOC));
					}
					else if (orderinOC instanceof SUPMTOOrder){
						logln("This SUPMTOOrder's probability: " + acceptSupportMoveOrder((SUPMTOOrder)orderinOC));
						acceptProbList.add(acceptSupportMoveOrder((SUPMTOOrder)orderinOC));
					}
					else{
						logln("This SUPOrder's probability: " + acceptSupportHoldOrder((SUPOrder)orderinOC));
						acceptProbList.add(acceptSupportHoldOrder((SUPOrder)orderinOC));
					}
				}					
			}
		}

		//handle dmz
		for(DMZ dmz: receivedDMZs){
			if(dmz.getYear() != game.getYear() || !dmz.getPhase().equals(game.getPhase())){
				logln("Proposed DMZ is not for this round. Marked unsure...");
				acceptProbList.add(0.0);	//for unsure cases, add 0.5 probability
			}
			else{
				logln("This DMZ's probability: " + acceptDMZOrder(dmz));
				acceptProbList.add(acceptDMZOrder(dmz));
			}
		}
		
		//find average utility value
		double totalUtilValue = 0;
		for(double utilValue: acceptProbList){
			totalUtilValue += utilValue;
		}
		totalUtilValue /= acceptProbList.size();
		logln("Probability list for this deal: " + acceptProbList);
		logln("Average probability: " + totalUtilValue);
		
		//Acceptance: between 0.4-0.8, flip the coin; < 0.4 reject, > 0.8 accept
		if (totalUtilValue >= 0.4 && totalUtilValue < 0.8){
			if (random.nextDouble() <= totalUtilValue){
				this.acceptProposal(receivedProposal.getId());
				loglnC("Accepted deal: ..." + deal + "\n");
			}
			else {
				this.rejectProposal(receivedProposal.getId());	
				loglnC("Rejected deal: ..." + deal + "\n");
			}
		}
		
		else{
			if(totalUtilValue >= 0.8){
				this.acceptProposal(receivedProposal.getId());
				loglnC("Accepted deal: ..." + deal + "\n");
			}
			else{
				this.rejectProposal(receivedProposal.getId());	
				loglnC("Rejected deal: ..." + deal + "\n");
			}
		}

	}
	
	private double acceptSupportMoveOrder(SUPMTOOrder sMovOrder){
		//returns a probability to accept the deal. make it probabilistic for now
		
		double supporteeHos = 0;		//supportee's hostility
		double supporteeStrength = 0;	//supportee's strength
		double unitNeediness = 0;		//unit's neediness
		double targetStrength = 0;		//target's strength
		double targetHos = 0;			//target's hostility
		double hostDiff = 0;			//hostility difference
		
		String supporteeStr = game.getController(sMovOrder.getSupportedRegion()).getName();
		String myUnitStr = sMovOrder.getLocation().getName();	//may need to check if this is correctly implemented
		
		//If I am being supported, just say yes!
		if(supporteeStr.equals(me.getName()))	return 1;
		else{
			//measure hostility
			supporteeHos = nHostilityList.get(supporteeStr);
			
			//measure strength
			supporteeStrength = strengthList.get(supporteeStr);
			
			//measure neediness
			Plan bestPlan = dBraneTactics.determineBestPlan(game, me, getConfirmedDeals(), allies);
			List<Order> bestOrders = bestPlan.getMyOrders();
			Order myUnitOrder = null;
			for(Order order : bestOrders){
				//check original plan of the requested support unit
				if(order.getLocation().getName().equals(myUnitStr)){
					//traverse to see my unit's original plan
					myUnitOrder = order;		//elicit the unit's order out
					break;
				}
			}
						
			if (myUnitOrder instanceof MTOOrder){
				String intendedRegionStr = ((MTOOrder)myUnitOrder).getDestination().getName();
				double utility = myUtilityList.get(intendedRegionStr.substring(0, 3));
				if(utility >= 0.5)
					unitNeediness = ((utility - 0.5) * (utility - 0.5)) / 0.5 + 0.5; 	//y = (x-0.5)^2/0.5 + 0.5
				else
					//unit's neediness directly depends on the utility of intended destination
					unitNeediness = utility;
			}
			
			else if (myUnitOrder instanceof SUPMTOOrder || myUnitOrder instanceof SUPOrder){
				if(myUnitOrder instanceof SUPMTOOrder) {
					Region supportedRegion = ((SUPMTOOrder)myUnitOrder).getSupportedRegion();
					Power supportedPower = game.getController(supportedRegion);
					if(supportedPower.getName().equals(me.getName())){
						//if my unit is planned to support myself, return no
						return 0;
					}
					else{
						//if originally I am to help a friend, unlikely to help a stranger
						double supportedPowerHos = nHostilityList.get(supportedPower);
						hostDiff = (supportedPowerHos - supporteeHos) / supportedPowerHos;
						if(hostDiff > 0) unitNeediness = hostDiff * 0.8;
						else unitNeediness = 0;
					}
				}
				else {
					Region supportedRegion = ((SUPOrder)myUnitOrder).getSupportedRegion();
					Power supportedPower = game.getController(supportedRegion);
					if(supportedPower.getName().equals(me.getName())){
						//if my unit is planned to support myself, return no
						unitNeediness = 1;
					}
					else{
						//if originally I am to help a friend, unlikely to help a stranger
						double supportedPowerHos = nHostilityList.get(supportedPower);
						hostDiff = (supportedPowerHos - supporteeHos) / supportedPowerHos;
						if(hostDiff > 0) unitNeediness = hostDiff * 0.8;
						else unitNeediness = 0;
					}
				}
			}
			else unitNeediness = 0.5;		//for hold order
				
			//check target
			if(game.getController(sMovOrder.getSupportedOrder().getDestination()) != null){
				String supTargetStr = game.getController(sMovOrder.getSupportedOrder().getDestination()).getName();
				targetStrength = strengthList.get(supTargetStr);

				targetHos = nHostilityList.get(supTargetStr);
			}
			
			//calculate composite acceptance probability
			double acceptProb = 0;
			if(targetStrength == 0 && targetHos == 0)
				acceptProb = 0.3 * supporteeHos + 0.2 * (1 - supporteeStrength) + 0.5 * (1 - unitNeediness);
			
			else acceptProb = 0.2 * supporteeHos + 0.1 * (1 - supporteeStrength) + 0.5 * (1 - unitNeediness)
								+ 0.1 * targetStrength + 0.1 * targetHos;
			
			logln("\nsupporteeHos - " + supporteeHos);
			logln("supporteeStrength - " + supporteeStrength);
			logln("unitNeediness - " + unitNeediness);
			logln("targetStrength - " + targetStrength);
			logln("targetHos - " + targetHos);
			logln("acceptProb - " + acceptProb + "\n");
			
			return acceptProb;
		}
	}
	
	private double acceptSupportHoldOrder(SUPOrder sHoldOrder){
		double supporteeHos = 0;		//supportee's hostility
		double supporteeStrength = 0;	//supportee's strength
		double unitNeediness = 0;		//unit neediness
		double hostDiff = 0;			//difference between hostility
		
		String supporteeStr = game.getController(sHoldOrder.getSupportedRegion()).getName();
		String myUnitStr = sHoldOrder.getLocation().getName();	//may need to check if this is correctly implemented
		
		//If I am being supported, just say yes!
		if(supporteeStr.equals(me.getName()))	return 1;
		else{
			//measure hostility
			supporteeHos = nHostilityList.get(supporteeStr);
			
			//measure strength
			supporteeStrength = strengthList.get(supporteeStr);
			
			//measure unit neediness
			Plan bestPlan = dBraneTactics.determineBestPlan(game, me, getConfirmedDeals(), allies);
			List<Order> bestOrders = bestPlan.getMyOrders();
			Order myUnitOrder = null;
			for(Order order : bestOrders){
				//check original plan of the requested support unit
				if(order.getLocation().getName().equals(myUnitStr)){//traverse to see my unit's original plan
					myUnitOrder = order;		//elicit the unit's order out
					break;
				}
			}
			if (myUnitOrder instanceof MTOOrder){
				String intendedRegionStr = ((MTOOrder)myUnitOrder).getDestination().getName();
				
				double utility = myUtilityList.get(intendedRegionStr.substring(0, 3));
				if(utility >= 0.5)
					unitNeediness = ((utility - 0.5) * (utility - 0.5)) / 0.5 + 0.5;
				else
					//unit's neediness directly depends on the utility of intended destination
					unitNeediness = utility;
			}
			else if (myUnitOrder instanceof SUPMTOOrder || myUnitOrder instanceof SUPOrder){
				Region supportedRegion = null;
				if(myUnitOrder instanceof SUPMTOOrder){
					supportedRegion = ((SUPMTOOrder) myUnitOrder).getSupportedRegion();
				}
				else{
					supportedRegion = ((SUPOrder) myUnitOrder).getSupportedRegion();
				}
				Power supportedPower = game.getController(supportedRegion);
				if(supportedPower.getName().equals(me.getName())){
					//if my unit is planned to support myself, return no
					return 0;
				}
				else{
					double supportedPowerHos = nHostilityList.get(supportedPower);
					hostDiff = (supportedPowerHos - supporteeHos) / supportedPowerHos;
					if(hostDiff > 0) unitNeediness = hostDiff * 0.8;
					else unitNeediness = 0;
				}
			}
			else unitNeediness = 0.5;
			
			//calculate composite acceptance probability
			double acceptProb = 0.3 * supporteeHos + 0.2 * (1 - supporteeStrength) + 0.5 * (1 - unitNeediness);			
			
			logln("\nsupporteeHos - " + supporteeHos);
			logln("supporteeStrength - " + supporteeStrength);
			logln("unitNeediness - " + unitNeediness);
			logln("acceptProb - " + acceptProb + "\n");
			
			return acceptProb;
		}
	}

	private double acceptMoveOrder(MTOOrder moveOrder){
		double targetHos = 0;		//target's hostility
		double newIsBetter = 0.2;	//if new is better, = 0.8, else = 0.2
		
		String myUnitStr = moveOrder.getLocation().getName();
		
		Plan bestPlan = dBraneTactics.determineBestPlan(game, me, getConfirmedDeals(), allies);
		List<Order> bestOrders = bestPlan.getMyOrders();
		Order myUnitOrder = null;
		for(Order order : bestOrders){
			//check original plan of the requested support unit
			if(order.getLocation().getName().equals(myUnitStr)){//traverse to see my unit's original plan
				myUnitOrder = order;		//elicit the unit's order out
				break;
			}
		}
		
		//find target's hostility - there's someone in destination
		if(game.getController(moveOrder.getDestination()) != null){
			String targetStr = game.getController(moveOrder.getDestination()).getName();
			targetHos = nHostilityList.get(targetStr);	
		}
		
		if(myUnitOrder == null || myUnitOrder instanceof HLDOrder){
			//treat null as HLDOrder
			String destStr = moveOrder.getDestination().getName().substring(0,3);
			if(myUtilityList.get(myUnitStr.substring(0,3)) >= myUtilityList.get(destStr))
				newIsBetter = 0.8;
		}
			
		else if (myUnitOrder instanceof MTOOrder){
			String newDestination = moveOrder.getDestination().getName();
			String originalDestination = ((MTOOrder) myUnitOrder).getDestination().getName();
			double newDesUtil = myUtilityList.get(newDestination.substring(0,3));
			double oriDesUtil = myUtilityList.get(originalDestination.substring(0,3));
			
			if(newDesUtil - oriDesUtil >= 0) newIsBetter = 0.8;
			if(newDestination.equals(originalDestination)) newIsBetter = 1;
		}
		
		else{
			Region supportedRegion = null;
			if(myUnitOrder instanceof SUPMTOOrder){
				supportedRegion = ((SUPMTOOrder) myUnitOrder).getSupportedRegion();
			}
			else{
				supportedRegion = ((SUPOrder) myUnitOrder).getSupportedRegion();
			}
			Power supportedPower = game.getController(supportedRegion);
			if(supportedPower.getName().equals(me.getName())){
				//if my unit is planned to support myself, return no
				return 0;
			}
			else{
				double supporteeHos = nHostilityList.get(supportedPower.getName());
				if(supporteeHos >= 0.7) newIsBetter = 0.8;
			}
		}
					
		//calculate composite probability
		double acceptProb = 0;
		if(targetHos == 0)
			acceptProb = newIsBetter;
		else acceptProb = 0.3 * targetHos + 0.7 * newIsBetter;
		
		if(targetHos != 0) logln("targetHos - " + targetHos);
		logln("newIsBetter - " + newIsBetter);
		logln("acceptProb - " + acceptProb + "\n");

		return acceptProb;
	}
	
	private double acceptHoldOrder(HLDOrder holdOrder){
		String myUnitStr = holdOrder.getLocation().getName();
		Plan bestPlan = dBraneTactics.determineBestPlan(game, me, getConfirmedDeals(), allies);
		List<Order> bestOrders = bestPlan.getMyOrders();
		Order myUnitOrder = null;
		for(Order order : bestOrders){
			//check original plan of the requested support unit
			if(order.getLocation().getName().equals(myUnitStr)){//traverse to see my unit's original plan
				myUnitOrder = order;		//elicit the unit's order out
				break;
			}
		}
		if (myUnitOrder instanceof HLDOrder) return 1;		//matches best plan
		else if (myUnitOrder instanceof MTOOrder){
			String intendedRegionStr = ((MTOOrder)myUnitOrder).getDestination().getName();
						
			//unit's neediness directly depends on the utility of intended destination
			double intendedRegionUtil = myUtilityList.get(intendedRegionStr.substring(0,3));
			if(intendedRegionUtil > 0.7) return 0;
			else return 0.4;		//default value shows slight tendency towards refusing 
		}
		
		else{
			//the support class order
			return 0.1;				//very unlikely to accept
		}
	}
	
	private double acceptDMZOrder(DMZ dmz){
		double competitiveness = 0;		//competitiveness of the region
		double utilityOfRegion = 0;
		
		//if it is not negotiating about dmz for this round, just return unsure probability;
		if(dmz.getYear() != game.getYear() || !dmz.getPhase().equals(game.getPhase())) return 0.5;
		else{
			List<Province> demilProvinces = dmz.getProvinces();
			Set<String> demilProvincesStr = new HashSet<String>();
			
			for(Province province: demilProvinces){
				demilProvincesStr.add(province.getName());
			}
			
			Plan bestPlan = dBraneTactics.determineBestPlan(game, me, getConfirmedDeals(), allies);
			List<Order> bestOrders = bestPlan.getMyOrders();
			Map<String, String> intendedProvinces = new HashMap<String,String>();
			for(Order order : bestOrders){
				if(order instanceof MTOOrder){
					String destination = ((MTOOrder) order).getDestination().getProvince().getName();
					String unitLocation = ((MTOOrder) order).getLocation().getProvince().getName();
					intendedProvinces.put(destination, unitLocation);	//key is destination instead of location
				}
			}
			
			//a copy of demilitarised provinces
			Set<String> intersectProvince = new HashSet<String>(demilProvincesStr);
			intersectProvince.retainAll(intendedProvinces.keySet());
			
			if(intersectProvince.size() == 0) return 0.5;		//to dmz a region we are not interested, be neutral
			
			else{
				//get the competitiveness
				int numberOfPowersInvolved = dmz.getPowers().size();
				competitiveness = Math.log10((double)numberOfPowersInvolved);
								
				for(String pro: intersectProvince){
					double util = myUtilityList.get(pro.substring(0, 3));
					utilityOfRegion += util;
				}
				
				//for all utilities, for now simply find the average of it. --2017.2.14
				utilityOfRegion /= intersectProvince.size();
				
				//calculate composite probability
				return 0.6 * competitiveness + 0.4 * (1 - utilityOfRegion);
			}
		}		
	}
	
	/*-------------------Bidding strategy--------------------*/
	private void biddingStrategy(){
		//normalise attacks
		for(Power power: game.getNonDeadPowers()){
			if(!power.getName().equals(me.getName()))
				normaliseAttackRegions(power.getName());
		}
		
		//resolve conflicts
		for(Power power: game.getNonDeadPowers()){
			if(!power.getName().equals(me.getName()))
				resolveConflictRegions(power.getName());
		}
		
		//generate some decent deals using genetic algorithm (experimental)
		//geneticAlgorithm();
		
		//for here, dealPool should have been updated
		//log the dealPool for now
		logln("Updated deal pool as below:");
		printDealPool();
		
		logln("Updated current proposed deals as below:");
		printProposedDeals();
	}
	
	private void normaliseAttackRegions(String powerString){
		List<String> myHomes = new ArrayList<String>();
		for(Province home: me.getHomes()){
			myHomes.add(home.getName());
		}
		Plan powerBestPlan = dBraneTactics.determineBestPlan(game, game.getPower(powerString), new ArrayList<BasicDeal>());
		List<Order> powerBestOrders = powerBestPlan.getMyOrders();
		Map<String, Order> unitAttackRegion = new HashMap<String, Order>();
		
		for(Order order: powerBestOrders){
			if(order instanceof MTOOrder){
				Region destination = ((MTOOrder)order).getDestination();
				Region location = ((MTOOrder)order).getLocation();
				if(myHomes.contains(destination.getProvince().getName())){
					//someone is going to attack us
					unitAttackRegion.put(location.getName(), order);
				}
			}
		}
		if(unitAttackRegion.keySet().isEmpty()) logln(powerString + " is not attacking us.");
		else{
			List<OrderCommitment> ocsToProposeBack = new ArrayList<OrderCommitment>();
			for(String unit: unitAttackRegion.keySet()){
				List<Order> allOrders = generateOrderPool(powerString, unit);
				Order hostileOrder = unitAttackRegion.get(unit);
				Order alternativeOrder = hostileOrder;
				
				for(Order order: allOrders){
					if(!order.toString().equals(hostileOrder.toString()) && 
							calculateOrder(order, powerString) >= calculateOrder(alternativeOrder, powerString)){
						//if there exists an order, not the original hostile order, and offers the best utility among all
						//it is the best alternative to propose
						alternativeOrder = order;
					}
				}
				
				if(!alternativeOrder.toString().equals(hostileOrder.toString())){
					//we found an alternative for the unit. put the oc into proposing deal
					OrderCommitment oc = new OrderCommitment(game.getYear(), game.getPhase(), alternativeOrder);
					ocsToProposeBack.add(oc);
					
					//return the favour to increase the probability of being accepted, if allowed
					if(returnCredit > 0){
						OrderCommitment oc2 = new OrderCommitment(game.getYear(), game.getPhase(), returnTheFavour(powerString, "", ""));
						if(oc2 != null) ocsToProposeBack.add(oc2);
					}

					BasicDeal proposeAlternative = new BasicDeal(ocsToProposeBack, new ArrayList<DMZ>());
					printAndProposeDeal(proposeAlternative);
					
					//add related deals into deal pool and currently proposed deals
					//issueName is the home that is under attack
					String issueName = ((MTOOrder)hostileOrder).getDestination().getName();
					addToDealPool(alternativeOrder.getPower().getName(), issueName, proposeAlternative);
					addToCurrentlyProposedDeals(alternativeOrder.getPower().getName(), issueName, proposeAlternative);
				}
				
				else{
					//we can't found an alternative
					logln("We cannot find an alternative way to stop " + unit + " from invading " + ((MTOOrder)hostileOrder).getDestination().getName()+ ". Try calling support...");
					
					//find someone to support hold us, if we have units in the home
					Region underAttackHome = ((MTOOrder)hostileOrder).getDestination();
					if(game.getController(underAttackHome)!= null &&
						game.getController(underAttackHome).getName().equals(me.getName())){
												
						for(Region adjacentUnit: game.getAdjacentUnits(underAttackHome.getProvince())){
							String adjPowerStr = game.getController(adjacentUnit).getName();
							if(!adjPowerStr.equals(hostileOrder.getPower().getName()) && !adjPowerStr.equals(me.getName())){
								//if the controller of the adjacent unit is not the issuer of hostile order
								//ask to support hold
								HLDOrder toHold = new HLDOrder(me, underAttackHome);
								SUPOrder supportHoldOrder = new SUPOrder(game.getPower(adjPowerStr), adjacentUnit, toHold);
								OrderCommitment oc1 = new OrderCommitment(game.getYear(), game.getPhase(), supportHoldOrder);
								ocsToProposeBack.add(oc1);
								
								//and not forget to return the favour, if allowable
								if(returnCredit > 0){
									Order orderToReturnFavour = returnTheFavour(adjPowerStr, "", "");
									OrderCommitment oc2 = new OrderCommitment(game.getYear(), game.getPhase(), orderToReturnFavour);
									ocsToProposeBack.add(oc2);
								}
								
								//propose the deal
								BasicDeal proposeAlternative = new BasicDeal(ocsToProposeBack, new ArrayList<DMZ>());
								printAndProposeDeal(proposeAlternative);
								
								//add related deals into deal pool and currently proposed deals
								//issueName is the home that is under attack
								String issueName = ((MTOOrder)hostileOrder).getDestination().getName();
								addToDealPool(alternativeOrder.getPower().getName(), issueName, proposeAlternative);
								addToCurrentlyProposedDeals(alternativeOrder.getPower().getName(), issueName, proposeAlternative);
							}
						}
						
					}
					
					else{
						//we don't have units inside, only but to propose a DMZ
						logln("No unit in house. Only to DMZ...");
						
						Province dmzThisRegion = ((MTOOrder)hostileOrder).getDestination().getProvince();
						List<Province> dmzProvince = new ArrayList<Province>();
						dmzProvince.add(dmzThisRegion);
						
						List<Power> powerToDMZ = new ArrayList<Power>();
						powerToDMZ.add(me);
						powerToDMZ.add(hostileOrder.getPower());
						
						DMZ dmz = new DMZ(game.getYear(), game.getPhase(), powerToDMZ, dmzProvince);
						List<DMZ> dmzToPropose = new ArrayList<DMZ>();
						dmzToPropose.add(dmz);
						
						List<OrderCommitment> haystack = new ArrayList<OrderCommitment>();
						if(returnCredit > 0){
							OrderCommitment hay = new OrderCommitment(game.getYear(), game.getPhase(), 
									returnTheFavour(hostileOrder.getPower().getName(), "", ""));
							haystack.add(hay);
						}
						
						//propose the deal
						BasicDeal dmzDeal = new BasicDeal(haystack, dmzToPropose);
						printAndProposeDeal(dmzDeal);
						
						//add related deals into deal pool and currently proposed deals
						//issueName is the home that is under attack
						String issueName = ((MTOOrder)hostileOrder).getDestination().getName();
						addToDealPool(hostileOrder.getPower().getName(), issueName, dmzDeal);
						addToCurrentlyProposedDeals(hostileOrder.getPower().getName(), issueName, dmzDeal);
					}
				}
			}
		}
	}
	
	private void resolveConflictRegions(String powerString){
		//resolve conflict regions with a certain power by updating the dealPool
		
		logln("Resolving conflict zones with "+ powerString + "...");
		
		Plan myBestPlan = dBraneTactics.determineBestPlan(game, me, getConfirmedDeals(), allies);
		List<BasicDeal> emptyCommitment = new ArrayList<BasicDeal>();
		Plan powerBestPlan = dBraneTactics.determineBestPlan(game, game.getPower(powerString), emptyCommitment);
		List<Order> myBestOrders = myBestPlan.getMyOrders();
		List<Order> powerBestOrders = powerBestPlan.getMyOrders();
		
		//only MTOOrder will be in conflict of each other, for now just note down the regions in conflict
		//map in a way of <Destination, Location>
		Map<String, String> myMoveToMap = new HashMap<String, String>();
		Map<String, String> powerMoveToMap = new HashMap<String, String>();
		
		for(Order order: myBestOrders){
			if(order instanceof MTOOrder){
				Region destination = ((MTOOrder)order).getDestination();
				Region location = ((MTOOrder)order).getLocation();
				myMoveToMap.put(destination.getName(), location.getName());
			}
		}
		
		for(Order order: powerBestOrders){
			if(order instanceof MTOOrder){
				Region destination = ((MTOOrder)order).getDestination();
				Region location = ((MTOOrder)order).getLocation();
				powerMoveToMap.put(destination.getName(), location.getName());
			}
		}
		
		Set<String> conflictRegions = myMoveToMap.keySet();
		conflictRegions.retainAll(powerMoveToMap.keySet());
		
		if(conflictRegions.isEmpty())	return;		//no in conflict regions with this power
		
		//handle conflicts
		else{
			//a list of deal to be enqueue for proposal, according to priority
			BasicDeal proposeAlternative = null;
			BasicDeal askForSupportAndGrantReturn = null;
			BasicDeal reservationDMZ = null;
			
			for(String conflictRegion: conflictRegions){
				//one conflict region is one issue
				
				logln("Conflict region at " + conflictRegion);
				
				String myUnitStr = myMoveToMap.get(conflictRegion);
				String opponentUnitStr = powerMoveToMap.get(conflictRegion);
				
				//reconstruct the hostile move order of opponent
				Order hostileOrder = new MTOOrder(game.getController(game.getRegion(opponentUnitStr)), 
						game.getRegion(opponentUnitStr), game.getRegion(conflictRegion));
				
				//first find if there is a better alternative for the unit
				String opponentPowerString = game.getController(game.getRegion(opponentUnitStr)).getName();
				List<Order> allOrders = generateOrderPool(opponentPowerString, opponentUnitStr);
				Order alternativeOrder = hostileOrder;
				
				for(Order order: allOrders){
					if(!order.toString().equals(hostileOrder.toString()) && 
							calculateOrder(order, powerString) >= calculateOrder(alternativeOrder, powerString)){
						//if there exists an order, not the original hostile order, and offers the best utility among all
						//it is the best alternative to propose
						alternativeOrder = order;
					}
				}
				
				if(!alternativeOrder.toString().equals(hostileOrder.toString())){
					//we found an alternative for the unit. put the oc into proposing deal
					
					logln("We found an alternative as below:");
					
					OrderCommitment oc = new OrderCommitment(game.getYear(), game.getPhase(), alternativeOrder);
					List<OrderCommitment> ocs = new ArrayList<OrderCommitment>();
					ocs.add(oc);
					
					if(returnCredit > 0){
						OrderCommitment returnFavour = new OrderCommitment(game.getYear(), game.getPhase(), returnTheFavour(powerString, "", ""));
						ocs.add(oc);		//this is just to increase the likelihood of being accepted
					}
					
					proposeAlternative = new BasicDeal(ocs, new ArrayList<DMZ>());
					printAndProposeDeal(proposeAlternative);
					
					//add related deals into deal pool and currently proposed deals
					addToDealPool(alternativeOrder.getPower().getName(), conflictRegion, proposeAlternative);
					addToCurrentlyProposedDeals(alternativeOrder.getPower().getName(), conflictRegion, proposeAlternative);
				}
				
				else{
					//ask others to support us
					
					String supportingPowerStr = "";
					Order orderToReturnFavour = null;
					SUPMTOOrder supportOrder = null;
					OrderCommitment pureSupport = null;
					List<OrderCommitment> pureSup = null;
					
					logln("Looking to support conflict regions...");
					
					//reconstruct the hostile move order of opponent
					MTOOrder conflictMoveOrder = new MTOOrder(me, game.getRegion(myUnitStr), game.getRegion(conflictRegion));
						
					Region requestSupportRegion = conflictMoveOrder.getDestination();
					List<Region> adjacentUnits = game.getAdjacentUnits(requestSupportRegion.getProvince());
					
					List<BasicDeal> supportDeal = new ArrayList<BasicDeal>();
					
					for(Region adjUnit: adjacentUnits){
						if(game.getController(adjUnit) != null && 
								!game.getController(adjUnit).getName().equals(me.getName())){
							
							supportOrder = new SUPMTOOrder(game.getController(adjUnit), adjUnit, conflictMoveOrder);
							pureSupport = new OrderCommitment(game.getYear(), game.getPhase(), supportOrder);

							logln("Support order found: " + pureSupport.toString());
							
							List<OrderCommitment> oc = new ArrayList<OrderCommitment>();
							supportingPowerStr = supportOrder.getPower().getName();
							
							if(returnCredit > 0){
								//now find other "good enough" bids to stuff together, if allowable
								String supportProvince = supportOrder.getDestination().getName();
								orderToReturnFavour = returnTheFavour(supportingPowerStr, myUnitStr, supportProvince);
								
								//return the favour will not return null, at least stuff haystack in it
								OrderCommitment returnFavourOC = new OrderCommitment(game.getYear(), game.getPhase(), orderToReturnFavour);
								oc.add(returnFavourOC);
							}
							
							oc.add(pureSupport);
							askForSupportAndGrantReturn = new BasicDeal(oc, new ArrayList<DMZ>());
							
							logln("A good enough deal is found as below.");
							printAndProposeDeal(askForSupportAndGrantReturn);
							
							//add related deals into deal pool
							addToDealPool(supportingPowerStr, conflictRegion, askForSupportAndGrantReturn);
							addToCurrentlyProposedDeals(supportingPowerStr, conflictRegion, askForSupportAndGrantReturn);
						}
						
					}
				}
				
				//put the reservation value: DMZ region for both powers
				List<Power> dmzPowers = new ArrayList<Power>();
				dmzPowers.add(me);
				dmzPowers.add(game.getPower(powerString));
				List<Province> dmzProvinces = new ArrayList<Province>();
				dmzProvinces.add(game.getRegion(conflictRegion).getProvince());
				DMZ justCallForDMZ = new DMZ(game.getYear(), game.getPhase(), dmzPowers, dmzProvinces);
				List<DMZ> dmz = new ArrayList<DMZ>();
				dmz.add(justCallForDMZ);
				
				List<OrderCommitment> ocs = new ArrayList<OrderCommitment>();
				
				//maybe even for DMZ, return the favour, if allowable
				if(returnCredit > 0)
					ocs.add(new OrderCommitment(game.getYear(), game.getPhase(), returnTheFavour(powerString, "", "")));
				
				reservationDMZ = new BasicDeal(ocs, dmz);
				
				//add it into reservation pool, propose it when all other deals in dealPool failed
				addToReservationPool(powerString, conflictRegion, reservationDMZ);
			}
		}
	}
	
	private Order returnTheFavour(String powerStr, String cannotBeThisRegion, String cannotBeThisDest){
		//find a good enough bid to return back to the power, to increase probability of supporting us
		//should return the most tempting bid back here
		//cannotBeThisRegion is the unit which we are in conflict with the power.
		//Since we have plans for the power, it cannot be used to support the target power
		//cannotBeThisDest is the province we want to move in. Proposing back to support the power to go in is ridiculous.
		
		logln("Finding tempting bids to propose back...");
		
		Power targetPower = game.getPower(powerStr);
		
		ArrayList<Double> bufferedUtility = new ArrayList<Double>();
		ArrayList<String> bufferedLocAndDes = new ArrayList<String>();
		 
		//beware! n^3 here!
		for(Region targetPowerRegion: targetPower.getControlledRegions()){			
			for(Region adjRegion: targetPowerRegion.getAdjacentRegions()){
				double regionUtility = myUtilityList.get(adjRegion.getName().substring(0,3));
				
				if(regionUtility > 0.5){
					//this adjacent region should be somehow valuable for the power. try to see whether we can help
					for(Region adjOfAdjRegion: adjRegion.getAdjacentRegions()){
						//if it is my region, just offer help
						if(game.getController(adjOfAdjRegion) != null){
							if(game.getController(adjOfAdjRegion).getName().equals(me.getName())){
								bufferedUtility.add(regionUtility);
								bufferedLocAndDes.add(adjOfAdjRegion.getName());	//my unit location
								bufferedLocAndDes.add(targetPowerRegion.getName());	//target unit location
								bufferedLocAndDes.add(adjRegion.getName());			//target unit destination
							}
						}
					}
				}
			}
		}
		//if arraylist is not null, which means SUPMTOOrder is possible. 
		//This should be the best deal to return in favour
		if(!bufferedUtility.isEmpty()){
			for(int i = 0; i < bufferedUtility.size(); i++){
				if(!bufferedLocAndDes.get(i*3).equals(cannotBeThisRegion)){		//cannot be the conflicting region!
					Region myUnitLocation = game.getRegion(bufferedLocAndDes.get(i*3));
					Region targetUnitLocation = game.getRegion(bufferedLocAndDes.get(i*3 + 1));
					Region targetUnitDestination = game.getRegion(bufferedLocAndDes.get(i*3 + 2));
					
					if(!targetUnitDestination.getName().substring(0,3).equals(cannotBeThisDest)){	
						//the destination cannot be the one we want to go in. or else meaningless
						MTOOrder newMoveOrder = new MTOOrder(targetPower, targetUnitLocation, targetUnitDestination);
						SUPMTOOrder favourSupportOrder = new SUPMTOOrder(me, myUnitLocation, newMoveOrder);
						logln("Found favour support order in return: " + favourSupportOrder);
						return favourSupportOrder;
					}
				}
			}
		}
		//there isn't the case that we can offer support move to back in this case
		else{
			//try to offer support hold
			for(Region targetPowerRegion: targetPower.getControlledRegions()){
				for(Region adjRegion: targetPowerRegion.getAdjacentRegions()){
					if(game.getController(adjRegion) != null && game.getController(adjRegion).getName().equals(me.getName())){
						//if i am at the adjacent, just support hold
						HLDOrder newHoldOrder = new HLDOrder(targetPower, targetPowerRegion);
						SUPOrder favourSupportHoldOrder = new SUPOrder(me, adjRegion, newHoldOrder);
						logln("Found favour support hold order in return: " + favourSupportHoldOrder);
						return favourSupportHoldOrder;
					}
				}
			}
		}
		
		logln("Can't found anything to return back as favour!");
		returnCredit -= 1;
		
		//TODO: Continue from here
		return randomOCs().get(0).getOrder();
	}
	
	private List<Order> generateOrderPool(String powerStr){
		//generate all possible orders for the power as segments
		//to form deals (chromosomes)
		
		Plan bestPlan = dBraneTactics.determineBestPlan(game, me, getConfirmedDeals(), allies);
		
		//get all units of the negotiating power.
		List<Region> units = new ArrayList<Region>();
		units.addAll(game.getPower(powerStr).getControlledRegions());
		
		List<Order> orderPool = new ArrayList<Order>();
		
		//add hold orders
		for(Region powerUnit: units){
			Order newOrder = new HLDOrder(game.getPower(powerStr), powerUnit);
			orderPool.add(newOrder);
		}
		
		//add move orders
		for(Region powerUnit: units){
			for(Region adjUnit: powerUnit.getAdjacentRegions()){
				Order newOrder = new MTOOrder(game.getPower(powerStr), powerUnit, adjUnit);
				orderPool.add(newOrder);
			}
		}
		
		//add support move orders
		for(Region powerUnit: units){
			for(Region adjUnit: powerUnit.getAdjacentRegions()){
				for(Region adjadjUnit: adjUnit.getAdjacentRegions()){
					//if there is someone we could support, and not ourselves
					if(game.getController(adjadjUnit) != null && !game.getController(adjadjUnit).getName().equals(powerStr)){
						//supportee's move order
						MTOOrder moveOrder = new MTOOrder(game.getController(adjadjUnit), adjadjUnit, adjUnit);
						Order newOrder = new SUPMTOOrder(game.getPower(powerStr), powerUnit, moveOrder);
						orderPool.add(newOrder);
					}
				}
			}
		}
		
		//add support hold orders
		for(Region powerUnit: units){
			for(Region adjUnit: powerUnit.getAdjacentRegions()){
				//if there is someone we could support, and not ourselves
				if(game.getController(adjUnit) != null && !game.getController(adjUnit).getName().equals(powerStr)){
					HLDOrder holdOrder = new HLDOrder(game.getController(adjUnit), adjUnit);
					Order newOrder = new SUPOrder(game.getPower(powerStr), powerUnit, holdOrder);
					orderPool.add(newOrder);
				}
			}
		}
		
		return orderPool;
	}

	private List<Order> generateOrderPool(String powerStr, String unitStr){
		//generate all possible orders for a certain unit of the power
		
		Plan bestPlan = dBraneTactics.determineBestPlan(game, me, getConfirmedDeals(), allies);
		
		//get all units of the negotiating power.
		List<Region> units = new ArrayList<Region>();
		
		//only 1 unit - the specified one
		units.add(game.getRegion(unitStr));
		
		List<Order> orderPool = new ArrayList<Order>();
		
		//add hold orders
		for(Region powerUnit: units){
			Order newOrder = new HLDOrder(game.getPower(powerStr), powerUnit);
			orderPool.add(newOrder);
		}
		
		//add move orders
		for(Region powerUnit: units){
			for(Region adjUnit: powerUnit.getAdjacentRegions()){
				Order newOrder = new MTOOrder(game.getPower(powerStr), powerUnit, adjUnit);
				orderPool.add(newOrder);
			}
		}
		
		//add support move orders
		for(Region powerUnit: units){
			for(Region adjUnit: powerUnit.getAdjacentRegions()){
				for(Region adjadjUnit: adjUnit.getAdjacentRegions()){
					//if there is someone we could support, and not ourselves
					if(game.getController(adjadjUnit) != null && !game.getController(adjadjUnit).getName().equals(powerStr)){
						//supportee's move order
						MTOOrder moveOrder = new MTOOrder(game.getController(adjadjUnit), adjadjUnit, adjUnit);
						Order newOrder = new SUPMTOOrder(game.getPower(powerStr), powerUnit, moveOrder);
						orderPool.add(newOrder);
					}
				}
			}
		}
		
		//add support hold orders
		for(Region powerUnit: units){
			for(Region adjUnit: powerUnit.getAdjacentRegions()){
				//if there is someone we could support, and not ourselves
				if(game.getController(adjUnit) != null && !game.getController(adjUnit).getName().equals(powerStr)){
					HLDOrder holdOrder = new HLDOrder(game.getController(adjUnit), adjUnit);
					Order newOrder = new SUPOrder(game.getPower(powerStr), powerUnit, holdOrder);
					orderPool.add(newOrder);
				}
			}
		}
		
		return orderPool;
	}
	
	private void geneticAlgorithm(){
		//generate some deals, randomly or genetically, to propose
		
		int seed = 20;
		List<Power> powers = game.getNonDeadPowers();
		List<BasicDeal> dealsToPropose = new ArrayList<BasicDeal>();
		
		for(Power randomPower: powers){
			List<Order> orderPool1 = generateOrderPool(randomPower.getName());
			List<Order> orderPool2 = generateOrderPool(me.getName());
			Map<BasicDeal, Double> population = new HashMap<BasicDeal, Double>();
			
			for(int i=0; i<seed; i++){
				//generate 10 basic deals (chromosomes)
				List<OrderCommitment> ocs = new ArrayList<OrderCommitment>();
				List<DMZ> dmzs = new ArrayList<DMZ>();
				
				//their order
				Order order = orderPool1.get(random.nextInt(orderPool1.size()));		//randomly picked an order
				OrderCommitment oc = new OrderCommitment(game.getYear(), game.getPhase(), order);	//form OC
				ocs.add(oc);
				
				//our order
				order = orderPool2.get(random.nextInt(orderPool2.size()));		//randomly picked an order
				oc = new OrderCommitment(game.getYear(), game.getPhase(), order);	//form OC
				ocs.add(oc);

				//for now leave the DMZ empty
				BasicDeal deal = new BasicDeal(ocs, dmzs);
				population.put(deal, calculateDeal(deal, randomPower.getName()));
			}
			
			Map<BasicDeal, Double> newPopulation = new HashMap<BasicDeal, Double>();
			double selectionThreshold = 0.6;
			int counter = 0;
			
			//1st selection
			for(BasicDeal deal: population.keySet()){
				if(population.get(deal) >= selectionThreshold && calculateDeal(deal, me.getName()) >= selectionThreshold){	
					//bilateral agreement, so the target's willingness and our willingness are taken into account
					newPopulation.put(deal, population.get(deal));
				}
			}
			population.clear();
			population.putAll(newPopulation);
			newPopulation.clear();
			
			while(!(selectionThreshold >= 0.8 || counter >= 5 || population.size() < 5)){
							
				//crossover
				int numberOfCrossovers = seed - population.size();
				List<BasicDeal> populationList = new ArrayList<BasicDeal>(population.keySet());
				
				for(int i=0; i<numberOfCrossovers; i++){
					BasicDeal deal1 = populationList.get(random.nextInt(populationList.size()));
					BasicDeal deal2 = populationList.get(random.nextInt(populationList.size()));
									
					List<OrderCommitment> ocs1 = deal1.getOrderCommitments();
					List<OrderCommitment> ocs2 = deal2.getOrderCommitments();
					List<DMZ> dmzs1 = deal1.getDemilitarizedZones();
					List<DMZ> dmzs2 = deal2.getDemilitarizedZones();
					List<OrderCommitment> newocs1 = new ArrayList<OrderCommitment>();
					List<OrderCommitment> newocs2 = new ArrayList<OrderCommitment>();
					List<DMZ> newdmzs1 = new ArrayList<DMZ>();
					List<DMZ> newdmzs2 = new ArrayList<DMZ>();
					
					for(int j=0; j<ocs1.size(); j++){
						if(j < (ocs1.size() / 2)) newocs1.add(ocs1.get(j));
						else newocs2.add(ocs1.get(j));
					}
					for(int j=0; j<ocs2.size(); j++){
						if(j < (ocs2.size() / 2)) newocs2.add(ocs2.get(j));
						else newocs1.add(ocs2.get(j));
					}
					for(int j=0; j<dmzs1.size(); j++){
						if(j < (dmzs1.size() / 2)) newdmzs1.add(dmzs1.get(j));
						else newdmzs2.add(dmzs1.get(j));
					}
					for(int j=0; j<dmzs2.size(); j++){
						if(j < (ocs2.size() / 2)) newdmzs2.add(dmzs2.get(j));
						else newdmzs1.add(dmzs2.get(j));
					}
					
					BasicDeal newDeal = new BasicDeal(newocs1, newdmzs1);
					BasicDeal newDeal2 = new BasicDeal(newocs2, newdmzs2);
					
					population.put(newDeal, calculateDeal(newDeal, randomPower.getName()));
					population.put(newDeal2, calculateDeal(newDeal2, randomPower.getName()));
				}
				
				//remove duplicates
				List<BasicDeal> defectDeals = new ArrayList<BasicDeal>();
				for(BasicDeal deal: new ArrayList<BasicDeal>(population.keySet())){
								
					Map<String, Double> unitAndUtility = new HashMap<String, Double>();
					List<OrderCommitment> defects = new ArrayList<OrderCommitment>();
					OrderCommitment temp = null;
					List<OrderCommitment> ocs = deal.getOrderCommitments();

					for(OrderCommitment oc: ocs){
						String unitStr = oc.getOrder().getLocation().getName();
						if(unitAndUtility.keySet().contains(unitStr)){		//duplicate
							double utility = calculateOrder(oc.getOrder(), oc.getOrder().getPower().getName());
							if(utility < unitAndUtility.get(oc.getOrder().getLocation().getName())) defects.add(oc);
							else {
								defects.add(temp);
								unitAndUtility.put(unitStr, utility);
								temp = oc;
							}
						}
						else{
							double utility = calculateOrder(oc.getOrder(), oc.getOrder().getPower().getName());
							unitAndUtility.put(unitStr, utility);
							temp = oc;
						}
					}
					if(defects.size() != 0) defectDeals.add(deal);
					for(OrderCommitment oc: defects) ocs.remove(oc);
					BasicDeal newDeal = new BasicDeal(ocs, deal.getDemilitarizedZones());
					population.put(newDeal, calculateDeal(newDeal, randomPower.getName()));
				}
				
				for(BasicDeal deal: defectDeals) population.remove(deal);
				
				
				//selection
				for(BasicDeal deal: population.keySet()){
					if(population.get(deal) >= selectionThreshold && calculateDeal(deal, me.getName()) >= selectionThreshold){
						boolean isFound = false;
						for(BasicDeal dl: newPopulation.keySet()){
							if(deal.toString().equals(dl.toString())){
								isFound = true;
								break;
							}
						}
						//remove duplicates
						if(!isFound) newPopulation.put(deal, population.get(deal));
					}
				}
				population.clear();
				population.putAll(newPopulation);
				newPopulation.clear();
				
				selectionThreshold += 0.05;
				counter += 1;
			}

			//randomly select 1 deal to propose
			List<BasicDeal> populationList = new ArrayList<BasicDeal>(population.keySet());
			
			if(!populationList.isEmpty()){
				BasicDeal chosenDeal = populationList.get(random.nextInt(populationList.size()));
				dealsToPropose.add(chosenDeal);
				
				//add related deals into deal pool and currently proposed deals
				//issueName is the home that is under attack
				String issueName = "genetic";
				addToDealPool(randomPower.getName(), issueName, chosenDeal);
				addToCurrentlyProposedDeals(randomPower.getName(), issueName, chosenDeal);
			}
		}
		
		if(!dealsToPropose.isEmpty()){
			logln("Below are proposed by genetic algorithm:");
			//propose the deals generated
			for(BasicDeal deal: dealsToPropose)
				printAndProposeDeal(deal);
		}
		else
			logln("Not proposing by genetic algorithm.");
	}
	
	
	
	
	/*---------------------Utilities-------------------------*/
	
	//simple utility methods for logging
	private void loglnC(String inString){
		this.getLogger().logln(inString, true);
	}
	
	private void logln(String inString){
		this.getLogger().logln(inString, false);
	}
	
	private void logC(String inString){
		this.getLogger().log(inString, true);
	}
	
	private void log(String inString){
		this.getLogger().log(inString, false);
	}
	
	private boolean isSupportHold(Order order){
		if(order instanceof SUPOrder) return true;
		else return false;
	}
	
	private boolean isSupportMove(Order order){
		if(order instanceof SUPMTOOrder) return true;
		else return false;
	}
	
	/*please verify if these 2 methods below are correctly implemented*/
	private boolean isBenificiary(Order order){
		//only can be used by support order
		//typecast within this method
		
		if(isSupportHold(order)){
			SUPOrder sHoldOrder = (SUPOrder)order;
			Region supportedRegion = sHoldOrder.getSupportedRegion();
			if(game.getController(supportedRegion).equals(me)){
				//it means I am the benificial
				return true;
			}
			else return false;
		}
		else if (isSupportMove(order)){
			SUPMTOOrder sMoveOrder = (SUPMTOOrder)order;
			Region supportedRegion = sMoveOrder.getSupportedRegion();
			if(game.getController(supportedRegion) != null && game.getController(supportedRegion).getName().equals(me.getName())){
				//it means I am the benificial
				return true;
			}
			else return false;
		}
		
		else return false;
		
	}
	
	
	private double calculateDeal(BasicDeal deal, String powerStr){
		double utility = 0;
		int counter = 0;
		List<OrderCommitment> ocs = deal.getOrderCommitments();
		List<DMZ> dmzs = deal.getDemilitarizedZones();
		for(OrderCommitment oc: ocs){
			if(oc.getYear() == game.getYear() && oc.getPhase().equals(game.getPhase())){
				Order order = oc.getOrder();
				if(order.getPower().getName().equals(powerStr)){
					//the order belongs to the powerStr, count it
					double thisUtility = calculateOrder(order, powerStr);
					utility += thisUtility;
					counter += 1;
				}
			}
			else {
				logln(oc.getOrder().toString() + " 0 (not this year)");
				utility += 0;		//for unsure ones, just refuse to accept
				counter += 1;
			}
			
		}
		for(DMZ dmz: dmzs){
			//you must be in the dmz to get notified. DMZ proposed normally contains you
			if(dmz.getYear() == game.getYear() && dmz.getPhase().equals(game.getPhase())){
				double thisUtility = calculateOrder(dmz, powerStr);
				utility += thisUtility;
			}
			else {
				logln(dmz.toString() + " 0 (not this year)");
				utility += 0;
			}
			counter += 1;
		}
		if(counter == 0) counter = 1;	//avoid zero division error
		utility /= counter;			//for now just take the average
		//logln("Average utility: " + utility);
		return utility;
	}
	
	private double calculateOrder(Order order, String powerStr){
		OrderCalculator oc = new OrderCalculator(myUtilityList, game, dBraneTactics, strengthList, logger, allies, this.getConfirmedDeals(), me);
		double value = 0;
		if(order instanceof HLDOrder)
			value = oc.calculateOrder((HLDOrder)order, powerStr);
		else if(order instanceof MTOOrder)
			value = oc.calculateOrder((MTOOrder)order, powerStr);
		else if(order instanceof SUPMTOOrder)
			value = oc.calculateOrder((SUPMTOOrder)order, powerStr);
		else if(order instanceof SUPOrder)
			value = oc.calculateOrder((SUPOrder)order, powerStr);
		else {
			loglnC("Something went wrong!");		//something went wrong
			return -1;
		}
		if(value < 0 || value > 1) 
			loglnC("Invalid value for probability!");
		return value;
	}
	
	private double calculateOrder(DMZ dmz, String powerStr){
		OrderCalculator oc = new OrderCalculator(myUtilityList, game, dBraneTactics, strengthList, logger, allies, this.getConfirmedDeals(), me);
		return oc.calculateOrder(dmz, powerStr);
	}
	
	private void printDealPool(){
		for(String powerString: dealPool.keySet()){
			logln("Deal pool for " + powerString + ":");
			for(String issue: dealPool.get(powerString).keySet()){
				logln(issue + " : " + dealPool.get(powerString).get(issue));	//printing basic deal toString out
			}
		}
	}
	
	private void printProposedDeals(){
		for(String powerString: currentlyProposedDeals.keySet()){
			logln("Proposals for " + powerString + ":");
			if(!currentlyProposedDeals.get(powerString).keySet().isEmpty()){
				for(BasicDeal deal: currentlyProposedDeals.get(powerString).keySet()){
					logln(deal + " : " + currentlyProposedDeals.get(powerString).get(deal));	//printing basic deal toString out
				}
			}
		}
	}
	
	private void printAndProposeDeal(BasicDeal deal){
		loglnC("I have proposed a deal: " + deal.toString());
		this.proposeDeal(deal);
	}
	
	private void addToDealPool(String powerName, String issueName, BasicDeal deal){
		Map<String, List<String>> issues = dealPool.get(powerName);
		List<String> relatedDeals;
		if(issues.containsKey(issueName)) relatedDeals = issues.get(issueName);	//not new issue
		else relatedDeals = new ArrayList<String>();	//new issue
			
		relatedDeals.add(deal.toString());
		issues.put(issueName, relatedDeals);
		dealPool.put(powerName, issues);
	}
	
	private void addToReservationPool(String powerName, String issueName, BasicDeal DMZdeal){
		Map<String, BasicDeal> issues = reservationPool.get(powerName);
		issues.put(issueName, DMZdeal);
		reservationPool.put(powerName, issues);
	}
	
	private void addToCurrentlyProposedDeals(String powerName, String issueName, BasicDeal deal){
		Map<BasicDeal, String> issues = currentlyProposedDeals.get(powerName);
		issues.put(deal, issueName);
		currentlyProposedDeals.put(powerName, issues);
	}
	
	private List<DMZ> randomDMZ(){
		List<Power> aliveNegotiatingPowers = this.getNegotiatingPowers();
		List<DMZ> demilitarizedZones = new ArrayList<DMZ>(3);
		for(int i=0; i<3; i++){
			
			//1. Create a list of powers
			ArrayList<Power> powers = new ArrayList<Power>(2);
			
			//1a. add myself to the list
			powers.add(me);
			
			//1b. add a random other power to the list.
			Power randomPower = me;
			while(randomPower.equals(me)){
				
				int numNegoPowers = aliveNegotiatingPowers.size();
				randomPower = aliveNegotiatingPowers.get(random.nextInt(numNegoPowers));
			}
			powers.add(randomPower);
			
			//2. Create a list containing 3 random provinces.
			ArrayList<Province> provinces = new ArrayList<Province>();
			for(int j=0; j<3; j++){
				int numProvinces = this.game.getProvinces().size();
				Province randomProvince = this.game.getProvinces().get(random.nextInt(numProvinces));
				provinces.add(randomProvince);
			}
			
			
			//This agent only generates deals for the current year and phase. 
			// However, you can pick any year and phase here, as long as they do not lie in the past.
			// (actually, you can also propose deals for rounds in the past, but it doesn't make any sense
			//  since you obviously cannot obey such deals).
			demilitarizedZones.add(new DMZ( game.getYear(), game.getPhase(), powers, provinces));

		}
		
		return demilitarizedZones;
	}
	
	private List<OrderCommitment> randomOCs(){
		
		List<Power> aliveNegotiatingPowers = game.getNonDeadPowers();
		List<OrderCommitment> randomOrderCommitments = new ArrayList<OrderCommitment>();
			
			
		//get all units of the negotiating powers.
		List<Region> units = new ArrayList<Region>();
		for(Power power : aliveNegotiatingPowers){
			if(!power.getName().equals(me.getName()))
				units.addAll(power.getControlledRegions());
		}
			
			
		//Pick a random unit and remove it from the list
		Region randomUnit = units.remove(random.nextInt(units.size()));
		
		//Get the corresponding power
		Power power = game.getController(randomUnit);

		//Create a list of adjacent regions, including the current location of the unit.
		List<Region> adjacentRegions = new ArrayList<>(randomUnit.getAdjacentRegions());
		adjacentRegions.add(randomUnit);
		
		Region randomDestination = adjacentRegions.get(random.nextInt(adjacentRegions.size()));
		
		Order randomOrder = new MTOOrder(power, randomUnit, randomDestination);
		randomOrderCommitments.add(new OrderCommitment(game.getYear(), game.getPhase(), randomOrder));

		loglnC(randomOrderCommitments.toString());
		return randomOrderCommitments;
	}
	
	/*--------------------------------------------------------*/
	

}
