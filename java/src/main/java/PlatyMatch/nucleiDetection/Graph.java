package PlatyMatch.nucleiDetection;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

class Graph {
    // A user define class to represent a graph.
    // A graph is an array of adjacency lists.
    // Size of array will be V (number of vertices
    // in graph)
    int V;
    LinkedList<Integer>[] adjListArray;
    private List<ArrayList<Integer>> cc;

    // constructor
    Graph(int V) {
        this.V = V;
        // define the size of array as
        // number of vertices
        adjListArray = new LinkedList[V];

        // Create a new list for each vertex
        // such that adjacent nodes can be stored

        for (int i = 0; i < V; i++) {
            adjListArray[i] = new LinkedList<Integer>();
        }

        cc=new ArrayList<>();
    }

    // Adds an edge to an undirected graph
    void addEdge(int src, int dest) {
        // Add an edge from src to dest.
        adjListArray[src].add(dest);

        // Since graph is undirected, add an edge from dest
        // to src also
        adjListArray[dest].add(src);
    }

    List<Integer> DFSUtil(int v, boolean[] visited, List<Integer> temp) {
        // Mark the current node as visited and print it
        visited[v] = true;
        //System.out.print(v + " ");
        temp.add(v);
        // Recur for all the vertices
        // adjacent to this vertex
        for (int x : adjListArray[v]) {
            if (!visited[x]) DFSUtil(x, visited, temp);
        }
        return temp;
    }

    void connectedComponents() {
        // Mark all the vertices as not visited
        boolean[] visited = new boolean[V];

        for (int v = 0; v < V; ++v) {
            if (!visited[v]) {
                // print all reachable vertices
                // from v
                List<Integer> temp = new ArrayList<>();
                temp = (ArrayList<Integer>) DFSUtil(v, visited, temp);
                //System.out.println();
                cc.add((ArrayList<Integer>) temp);

            }
        }

    }

        public List<ArrayList<Integer>> getCC(){
            connectedComponents();
            return cc;

        }







    // Driver program to test above
    public static void main(String[] args) {
        // Create a graph given in the above diagram
        Graph g = new Graph(10); // 5 vertices numbered from 0 to 4

        g.addEdge(1, 0);
        g.addEdge(2, 3);
        g.addEdge(3, 4);
        g.addEdge(1, 9);
        System.out.println("Following are connected components");
        //g.connectedComponents();
        System.out.println("My addition");
        System.out.println(g.getCC());
    }
}
