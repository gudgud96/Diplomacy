package ddejonge.bandana.exampleAgents;


public class GraphApp {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Graph regionGraph = new Graph();
		regionGraph.constructGraph();
		regionGraph.pageRank("SC1");
	}

}
