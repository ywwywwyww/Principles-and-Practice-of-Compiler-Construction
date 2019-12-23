package decaf.backend.reg;

import java.util.*;


public class InterferenceGraph {

    int numNodes;

    // color = -1 corresponds spilled to memory
    Map<Integer, Integer> colors;

    List<Set<Integer>> edges;

    // Set of removed node
    Set<Integer> removedNodes;

    InterferenceGraph(int numNodes)
    {
        this.numNodes = numNodes;
        this.edges = new ArrayList<>();
        for (int i = 0; i < numNodes; i++) {
            edges.add(new TreeSet<>());
        }
        this.removedNodes = new TreeSet<>();
        this.colors = new TreeMap<>();
    }

    void addEdge(int x, int y) {
        edges.get(x).add(y);
        edges.get(y).add(x);
    }

    int getDegree(int x) {
        return getNeighbourhood(x).size();
    }

    List<Integer> getNeighbourhood(int x) {
        if (removedNodes.contains(x)) {
            return new ArrayList<>();
        }
        Set<Integer> s = new TreeSet<>(edges.get(x));
        s.removeIf(v -> removedNodes.contains(v));
        return new ArrayList<>(s);
    }

    void removeNode(int x) {
        removedNodes.add(x);
    }

    void recoverNode(int x) {
        removedNodes.remove(x);
    }

    /**
     * get color of a node
     * @param x node id
     * @return color if has color, else -1
     */
    Integer getColor(int x) {
        return colors.getOrDefault(x, -1);
    }

    /**
     *
     * @param x node id
     * @param color color
     */
    void setColor(int x, int color) {
        colors.put(x, color);
    }
}
