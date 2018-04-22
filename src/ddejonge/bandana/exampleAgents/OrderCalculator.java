package ddejonge.bandana.exampleAgents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ddejonge.bandana.dbraneTactics.DBraneTactics;
import ddejonge.bandana.dbraneTactics.Plan;
import ddejonge.bandana.negoProtocol.BasicDeal;
import ddejonge.bandana.negoProtocol.DMZ;
import ddejonge.bandana.tools.Logger;
import es.csic.iiia.fabregues.dip.board.Game;
import es.csic.iiia.fabregues.dip.board.Power;
import es.csic.iiia.fabregues.dip.board.Province;
import es.csic.iiia.fabregues.dip.board.Region;
import es.csic.iiia.fabregues.dip.orders.HLDOrder;
import es.csic.iiia.fabregues.dip.orders.MTOOrder;
import es.csic.iiia.fabregues.dip.orders.Order;
import es.csic.iiia.fabregues.dip.orders.SUPMTOOrder;
import es.csic.iiia.fabregues.dip.orders.SUPOrder;

public class OrderCalculator {
	
	//a power dependent calculator for a given order
	//also a method to get unit utility list of a given power
	//one problem to deal with is we do not know the hostility mapping of the particular power
	//implementable: check on the game and estimate the hostility
	
	private Map<String, Double> myUtilityList;
	private Game game;
	private DBraneTactics dBraneTactics;
	private Map<String, Double> strengthList;
	private Logger logger;
	private List<Power> allies;
	private List<BasicDeal> deals;
	private Power me;
	
	public OrderCalculator(Map<String, Double> myUtilityList, Game game, DBraneTactics dBraneTactics, 
			Map<String, Double> strengthList, Logger logger, List<Power> allies, List<BasicDeal> deals, Power me){
		this.myUtilityList = myUtilityList;
		this.game = game;
		this.dBraneTactics = dBraneTactics;
		this.strengthList = strengthList;
		this.logger = logger;
		this.allies = allies;
		this.deals = deals;
		this.me = me;
	}
	
	public double calculateOrder(SUPMTOOrder sMovOrder, String powerStr){
		//returns a probability to accept the deal. make it probabilistic for now
		
		double supporteeStrength = 0;	//supportee's strength
		double unitNeediness = 0;		//unit's neediness
		double targetStrength = 0;		//target's strength
		
		String supporteeStr = "";
		if(game.getController(sMovOrder.getSupportedRegion())!=null)
			supporteeStr = game.getController(sMovOrder.getSupportedRegion()).getName();
		String myUnitStr = sMovOrder.getLocation().getName();	//may need to check if this is correctly implemented
		
		//If the powerString itself is being supported, just say yes!
		if(supporteeStr.equals(powerStr))	return 1;
		else{
			//can't measure hostility
			//measure strength
			if(!supporteeStr.equals("")){
				supporteeStrength = strengthList.get(supporteeStr);
			}
				
			//measure neediness -> confirmed deals and allies are not known, so only a rough guess
			Plan bestPlan = null;
			if(!powerStr.equals(me.getName()))
				bestPlan = dBraneTactics.determineBestPlan(game, game.getPower(powerStr), new ArrayList<BasicDeal>());
			else
				bestPlan = dBraneTactics.determineBestPlan(game, me, deals, allies);
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
					unitNeediness = ((utility - 0.5) * (utility - 0.5)) / 0.5 + 0.5; 	//y = (x-0.5)^2/0.5 + 0.5
				else
					//unit's neediness directly depends on the utility of intended destination
					unitNeediness = utility;
			}
			
			else if (myUnitOrder instanceof SUPMTOOrder || myUnitOrder instanceof SUPOrder){
				if(myUnitOrder instanceof SUPMTOOrder) {
					Region supportedRegion = ((SUPMTOOrder)myUnitOrder).getSupportedRegion();
					Power supportedPower = game.getController(supportedRegion);
					if(supportedPower != null && supportedPower.getName().equals(powerStr)){
						//if my unit is planned to support myself, return no
						return 0;
					}
					else{
						//if originally I am to help a friend, unlikely to help a stranger
						//since we cannot measure the hostility difference between both powers
						//we could only assign a value of 0.5 for unit neediness
						unitNeediness = 0.5;
					}
				}
				else {
					Region supportedRegion = ((SUPOrder)myUnitOrder).getSupportedRegion();
					Power supportedPower = game.getController(supportedRegion);
					if(supportedPower != null && supportedPower.getName().equals(powerStr)){
						//if my unit is planned to support myself, return no
						unitNeediness = 1;
					}
					else{
						//if originally I am to help a friend, unlikely to help a stranger
						//since we cannot measure the hostility difference between both powers
						//we could only assign a value of 0.5 for unit neediness
						unitNeediness = 0.5;
					}
				}
			}
			else unitNeediness = 0.5;		//for hold order
				
			//check target
			String supTargetStr = "";
			if(game.getController(sMovOrder.getSupportedOrder().getDestination()) != null)
				supTargetStr = game.getController(sMovOrder.getSupportedOrder().getDestination()).getName();
			if(!supTargetStr.equals(""))
				targetStrength = strengthList.get(supTargetStr);
			
			//calculate composite acceptance probability
			double acceptProb = 0.2 * (1 - supporteeStrength) + 0.6 * (1 - unitNeediness)
								+ 0.2 * targetStrength;		//parameters of hostility are taken out
			
			return acceptProb;
		}
	}
	
	public double calculateOrder(SUPOrder sHoldOrder, String powerStr){

		double supporteeStrength = 0;	//supportee's strength
		double unitNeediness = 0;		//unit neediness
		
		String supporteeStr = "";
		if(game.getController(sHoldOrder.getSupportedRegion()) != null)
			supporteeStr = game.getController(sHoldOrder.getSupportedRegion()).getName();
		String myUnitStr = sHoldOrder.getLocation().getName();	//may need to check if this is correctly implemented
		
		//If I am being supported, just say yes!
		if(supporteeStr.equals(powerStr))	return 1;
		else{			
			//measure strength
			if(!supporteeStr.equals(""))
				supporteeStrength = strengthList.get(supporteeStr);
			
			//measure unit neediness
			Plan bestPlan = null;
			if(!powerStr.equals(me.getName()))
				bestPlan = dBraneTactics.determineBestPlan(game, game.getPower(powerStr), new ArrayList<BasicDeal>());
			else
				bestPlan = dBraneTactics.determineBestPlan(game, me, deals, allies);
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
				if(supportedPower != null && supportedPower.getName().equals(powerStr)){
					//if my unit is planned to support myself, return no
					return 0;
				}
				else unitNeediness = 0.5;
			}
			else unitNeediness = 0.5;
			
			//calculate composite acceptance probability
			double acceptProb = 0.3 * (1 - supporteeStrength) + 0.7 * (1 - unitNeediness);
			
			return acceptProb;
		}
	}

	public double calculateOrder(MTOOrder moveOrder, String powerStr){
				
		//if comparable, compare with previous MTOOrder
		//or else, just return the value of the intended region
		
		double newIsBetter = 0;		
		
		//can't count proposer's hostility
		String myUnitStr = moveOrder.getLocation().getName();
		
		Plan bestPlan = null;
		if(!powerStr.equals(me.getName()))
			bestPlan = dBraneTactics.determineBestPlan(game, game.getPower(powerStr), new ArrayList<BasicDeal>());
		else
			bestPlan = dBraneTactics.determineBestPlan(game, me, deals, allies);
		List<Order> bestOrders = bestPlan.getMyOrders();
		Order myUnitOrder = null;
		for(Order order : bestOrders){
			//check original plan of the requested support unit
			if(order.getLocation().getName().equals(myUnitStr)){//traverse to see my unit's original plan
				myUnitOrder = order;		//elicit the unit's order out
				break;
			}
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
			if(supportedPower != null && supportedPower.getName().equals(powerStr)){
				//if my unit is planned to support myself, return no
				return 0;
			}
			else newIsBetter = 0.5;
		}
		
		return newIsBetter;
	}
	
	public double calculateOrder(HLDOrder holdOrder, String powerStr){
		
		String myUnitStr = holdOrder.getLocation().getName();
		Plan bestPlan = null;
		if(!powerStr.equals(me.getName()))
			bestPlan = dBraneTactics.determineBestPlan(game, game.getPower(powerStr), new ArrayList<BasicDeal>());
		else
			bestPlan = dBraneTactics.determineBestPlan(game, me, deals, allies);
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
	
	public double calculateOrder(DMZ dmz, String powerStr){
		
		//DMZ calculation stays the same
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
			
			Plan bestPlan = null;
			if(!powerStr.equals(me.getName()))
				bestPlan = dBraneTactics.determineBestPlan(game, game.getPower(powerStr), new ArrayList<BasicDeal>());
			else
				bestPlan = dBraneTactics.determineBestPlan(game, me, deals, allies);
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
			
			if(intersectProvince.size() == 0) return 1;		//to dmz a region we are not interested, just accept
			
			else{
				//get the competitiveness
				int numberOfPowersInvolved = dmz.getPowers().size();
				competitiveness = Math.log10((double)numberOfPowersInvolved);
								
				//construction of provinceUtil map
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
}
