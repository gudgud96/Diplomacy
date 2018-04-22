package ddejonge.bandana.exampleAgents;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ddejonge.bandana.tools.Logger;
import es.csic.iiia.fabregues.dip.board.Game;
import es.csic.iiia.fabregues.dip.board.Region;

public class RegionGraph {
	/**
	 * RegionGraph: A Diplomacy implementation of Graph.java
	 * Each unit itself has a RegionGraph vision to estimate utility of each region
	 */
	
	/*---------------FIELDS---------------*/
	
	//a utility list to store utility for each node
	private Map<String, Integer> utilityList;
	//a normalised utility list to be output
	private Map<String, Double> nUtilityList;
	
	//pass in the specified unit name, logger and the current game status
	private String unitName;
	private Logger logger;
	private Game game;
	
	public RegionGraph(String unitName, Game game, Logger logger){
		utilityList = new HashMap<String, Integer>();
		nUtilityList = new HashMap<String, Double>();
		this.unitName = unitName;
		this.logger = logger;
		this.game = game;
	}
	
	/*---------------HEURISTICS---------------*/
	
	public void updateUtility(Map<String, List<String>> subgraphMap){
		/**
		 * updateUtility: algorithm to update utility of each region using variant of PageRank algorithm
		 * @param subgraphMap: subgraph processed by subgraph() method
		 */
		
		//loglnC("Updating utility...\n");
		
		Map<String, Integer> updatedUtilityList = new HashMap<String, Integer>();
		
		//first find which nodes should be calculated in the subgraph
		List<String> nodesInSubgraph = new ArrayList<String>(subgraphMap.keySet());
		
		//assign values to new nodes - by now SC is 10, normal is 1 -- 2017.2.4
		for(String adj: nodesInSubgraph){
			if(!utilityList.containsKey(adj)){	//haven't recorded yet, then assign corresponding value
				if(game.getRegion(adj).getProvince().isSC())	//if the province of the region is an SC
					utilityList.put(adj, 10);	//assign 10
				else utilityList.put(adj, 1);	//else assign 1
			}
		}
		//apply the algorithm to update utility
		for(String node: utilityList.keySet()){
			int initialUtility = utilityList.get(node);
			List<String> neighbours = subgraphMap.get(node);		//get all neighbouring nodes in the subgraph
			//log("Utility of " + node + " : " + initialUtility);
			for(String neighbour: neighbours){
				int neighbourUtility = utilityList.get(neighbour);				//get neighbour's utility
				int neighbourOutward = subgraphMap.get(neighbour).size();		//get neighbour's #neighbour
				initialUtility += neighbourUtility / neighbourOutward;			//PageRank algorithm here
				//log(" + " + neighbourUtility + "/" + neighbourOutward);
			}
			//log(" = " + initialUtility);
			updatedUtilityList.put(node, initialUtility);			//record new utility value
		}
		
		//update current utility list
		utilityList.putAll(updatedUtilityList);
		//loglnC("Utility updated.");
	}
	
	
	public Map<String, List<String>> subgraph(Map<String, List<String>>graphIn, String startingNode, int level){
		/**
		 * subgraph: recursive method to return a k-level subgraph
		 * graphIn starts with an empty graph
		 * @param graphIn: graphIn starts with an empty graph
		 * @param startingNode: name of the residing region of the unit
		 * @param level: minimum level input should be 0 - 0-level subgraph means a single node
		 */
		
		if (level == 0) {
			//terminating case: return a graph with only its neighbours occured in the previous level subgraph
			//this is because exploration stops at leaf node
			Region unitRegion = game.getRegion(startingNode);
			List<Region> neighbourRegions = unitRegion.getAdjacentRegions();
			
			//always store as string, instead of Region object itself to prevent troubles
			//get all neighbours of the leaf node
			List<String> adjNeighbour = new ArrayList<String>();
			for(Region neighbourRegion: neighbourRegions){
				adjNeighbour.add(neighbourRegion.getName());
			}
			
			//for leaf, only put in adjacency list if the neighbour is in subgraph
			List<String> resultList = new ArrayList<String>();
			for(String neighbour : adjNeighbour){
				if(graphIn.containsKey(neighbour))	
					resultList.add(neighbour);
					//lesson learnt. remove method is extremely notorious!
			}
			
			//output the subgraph of leaf node generated
			Map<String, List<String>> resultMap = new HashMap<String, List<String>>();
			resultMap.put(startingNode, resultList);
			return resultMap;
		}
		else{
			//recursively add sub-subgraphs into the original subgraph
			
			Region unitRegion = game.getRegion(startingNode);
			List<Region> neighbourRegions = unitRegion.getAdjacentRegions();
			
			//always store as string, instead of Region object itself to prevent troubles
			List<String> adjacency = new ArrayList<String>();
			for(Region neighbourRegion: neighbourRegions){
				adjacency.add(neighbourRegion.getName());
			}			
			
			//explore neighbours of starting node
			graphIn.put(startingNode, adjacency);

			
			//for each neighbours, recursively add the other lower level subgraph starting from adj
			for(String adj: adjacency)
				graphIn.putAll(subgraph(graphIn, adj, level - 1));

			return graphIn;
		}
	}
	
	public void pageRank(){

		
		logln("");
		logln("Running PageRank...");
		
		//run the algorithm
		int level = 3;	
		for(int i = 1; i <= level; i++)
			updateUtility(subgraph(new HashMap<String, List<String>>(), unitName, i));
		
		logln("PageRank successfully finished.");
		logln("Normalising...");
		
		//normalise utility list using linear scaling - 2017.2.18
		//regions with utility > 0.5 are considered very important
		
		int lowerBound = Collections.min(utilityList.values());	
		int upperBound = Collections.max(utilityList.values());
		for(String key: utilityList.keySet()){
			double normalisedUtility = (double)(utilityList.get(key) - lowerBound) / (double)(upperBound - lowerBound);
			nUtilityList.put(key, normalisedUtility);
		}
		
		logln("Utility list for : " + unitName);
		for(String key: nUtilityList.keySet()){
			if(game.getRegion(key).getProvince().isSC())
				logln(key + " : " + nUtilityList.get(key) + " (SC)");
			else logln(key + " : " + nUtilityList.get(key));
		}
		//logln("");
		
	}
	
	
	public Map<String, Double> getUtilityList(){
		return nUtilityList;
	}
	
	public String getUnitName(){
		return unitName;
	}
/*---------------------Utilities--------------------------*/
	
	//simple utility methods for logging
	private void loglnC(String inString){
		logger.logln(inString, true);
	}
	
	private void logln(String inString){
		logger.logln(inString, false);
	}
	
	/*private void logC(String inString){
		logger.log(inString, true);
	}*/
	
	private void log(String inString){
		logger.log(inString, false);
	}
	
	
/*--------------------------------------------------------*/
}
