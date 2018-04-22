package ddejonge.bandana.exampleAgents;

import java.util.*;

public class Graph {
	//FIELDS OF GRAPH
	//a hashmap for adjacency list, eg: "BEL": <"HOL", "PAR">
	private Map<String, List<String>> adjacencyList;
	//a utility list to store utility for each node
	//note that utilities are independent among each unit(army/fleet) itself
	private Map<String, Integer> utilityList;
	
	public Graph(){
		adjacencyList = new HashMap<String, List<String>>();
		utilityList = new HashMap<String, Integer>();
	}
	
	/*---------------for testing purpose---------------*/
	public void addNode(){
		//construct an entry in the graph
		Scanner in = new Scanner(System.in);
		System.out.println("Input node: ");
		String inputNode = in.nextLine();
		String keyNode = inputNode;
		List<String> adjacency = new LinkedList<String>();
		
		System.out.println("Input neighbour node: ");
		inputNode = in.nextLine();
		while(!inputNode.equals("-1")){
			adjacency.add(inputNode);
			System.out.println("Input neighbour node: ");
			inputNode = in.nextLine();
		}
		
		adjacencyList.put(keyNode, adjacency);
	}
	
	public void constructGraph(){
		//construct the whole graph
		Scanner in = new Scanner(System.in);
		System.out.println("Number of nodes to input: ");
		int numberOfNodes = in.nextInt();
		
		for(int i = 0; i < numberOfNodes; i++){
			addNode();
		}
		System.out.println("Graph constructed");
		System.out.println(adjacencyList);
	}

	/*---------------for testing purpose---------------*/
	
	public void updateUtility(String startingNode, Map<String, List<String>> subgraphMap){
		//algorithm to update utility according to PageRank algorithM
		System.out.println("Subgraph now: " + subgraphMap);
		
		Map<String, Integer> updatedUtilityList = new HashMap<String, Integer>();
		
		//first find which nodes should be calculated in the subgraph
		List<String> nodesInSubgraph = new ArrayList<String>(subgraphMap.keySet());
		
		//assign values to new nodes
		for(String adj: nodesInSubgraph){
			if(!utilityList.containsKey(adj)){	//haven't recorded yet, then assign corresponding value
				if(adj.substring(0, 2).equals("SC"))	//if it is a supply center
					utilityList.put(adj, 10);	//assign 10
				else utilityList.put(adj, 1);	//else assign 1
			}
		}
		//apply the algorithm to update utility
		for(String node: utilityList.keySet()){
			Integer initialUtility = utilityList.get(node);
			List<String> neighbours = subgraphMap.get(node);		//get all neighbouring nodes in the subgraph
			System.out.print("Utility of " + node + " : " + initialUtility);
			for(String neighbour: neighbours){
				Integer neighbourUtility = utilityList.get(neighbour);			//get neighbour's utility
				int neighbourOutward = subgraphMap.get(neighbour).size();		//get neighbour's #neighbour
				initialUtility += neighbourUtility / neighbourOutward;			//PageRank algorithm here
				System.out.print(" + " + neighbourUtility + "/" + neighbourOutward);
			}
			System.out.print(" = " + initialUtility);
			System.out.println();
			updatedUtilityList.put(node, initialUtility);			//record new utility value
		}
		
		//update current utility list
		utilityList.putAll(updatedUtilityList);
		
	}
	
	public Map<String, List<String>> subgraph(Map<String, List<String>>graphIn, String startingNode, int level){
		//recursive method to return a k-level subgraph
		//graphIn starts with an empty graph
		//minimum level input should be 0 - 0-level subgraph means a single node
		
		//System.out.println("GraphIn: " + graphIn);
		if (level == 0) {
			//terminating case: return a graph with only its neighbours occured in the previous level subgraph
			//this is because exploration stops at leaf node
			List<String> adjNeighbour = adjacencyList.get(startingNode);
			List<String> resultList = new ArrayList<String>();
			//System.out.println("AdjNeighbour: " + adjNeighbour);
			for(String neighbour : adjNeighbour){
				if(graphIn.containsKey(neighbour))	//for leaf, only put in adjacency list if the neighbour is in subgraph
					resultList.add(neighbour);
					//lesson learnt. remove method is extremely notorious!
			}
			Map<String, List<String>> resultMap = new HashMap<String, List<String>>();
			resultMap.put(startingNode, resultList);
			return resultMap;
		}
		else{
			//recursively add sub-subgraphs into the original subgraph
			
			//System.out.println("adjacency" + adjacencyList.get(startingNode));
			List<String> adjacency = adjacencyList.get(startingNode);			
			
			//explore neighbours of starting node
			graphIn.put(startingNode, adjacency);
			
			//System.out.println("GraphIn now: " + graphIn);
			
			//for each neighbours, recursively add the other lower level subgraph starting from adj
			for(String adj: adjacencyList.get(startingNode))
				graphIn.putAll(subgraph(graphIn, adj, level - 1));

			return graphIn;
		}
	}
	
	public void pageRank(String startingNode){
		//PageRank algorithm combining 2 methods above, execute until the intended level
			
		//for now, iterate the algorithm for 3 levels (shortsighted algorithm) - 2017.2.4
		//reason for 3 levels: if it is a spring move, need to anticipate 2 moves until the fall phase
		//for fall phase, assume 1 more level is needed for anticipation. hence 3 levels.
		
		int level = 3;	
		for(int i = 1; i <= level; i++)
			updateUtility(startingNode, subgraph(new HashMap<String, List<String>>(), startingNode, i));
	}
}
