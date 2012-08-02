package edu.stanford.nlp.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic data structure for a weighted lattice. The lattice must be read from a string in
 * Python Lattice Format (PLF), which is the input format for Moses.
 * 
 * <p>
 * Currently, this package only supports weighted edges (vv. weighted nodes).
 * </p>
 * 
 * <p>
 * Currently, this package is configured as a min/+ algebra (tropical semiring) where
 * the weights are negative log-likelihoods.
 * </p>
 * 
 * TODO(spenceg): Add a PLF lexer instead of these bootleg Joshua regexes.
 * TODO(spenceg): Add HTK support.
 * TODO(spenceg): Add a PLF IString reader.
 * TODO(spenceg): Parameterize for other semirings. This isn't hard, but makes
 *                the interface more opaque. Besides, we mostly use tropical semirings.
 *                
 * @author Spence Green
 *
 */
public class Lattice<T> {

  // DO NOT CHANGE THIS UNLESS YOU KNOW WHAT YOU'RE DOING!
  private static final int ROOT_ID = 0;
  
  private final List<Node<T>> nodeList;
  
  public Lattice() { 
    nodeList = new ArrayList<Node<T>>();
    nodeList.add(new Node<T>(ROOT_ID));
  }
  
  public Node<T> getRoot() { return nodeList.get(ROOT_ID); }
  
  // Tropical semiring
  public double annihilator() { return Integer.MAX_VALUE; }
  public double identity() { return 0.0; }
  public double compare(double a, double b) { return Math.min(a, b); }
  public double score(double a, double b) { return a + b; }
  
  /**
   * Returns the Viterbi path through the lattice.
   * 
   * @return
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public List<Edge<T>> viterbiPath() {
    final int numNodes = nodeList.size();
    double[] d_v = new double[numNodes];
    Arrays.fill(d_v, annihilator());
    d_v[ROOT_ID] = identity();
    Edge[] p_v = new Edge[numNodes]; 
    
    // Forward pass
    for (int v = ROOT_ID+1; v < numNodes; ++v) {
      List<Edge<T>> edgeList = nodeList.get(v).backwardStar();
      for (Edge<T> edge : edgeList) {
        int u = edge.start().id();
        double bestCost = d_v[v];
        double transitionCost = score(d_v[u], edge.weight());
        if (compare(bestCost, transitionCost) == transitionCost) {
          // Do an update to the best cost
          d_v[v] = transitionCost;
          p_v[v] = edge;
        }
      }
    }
    
    // Backout the solution
    List<Edge<T>> bestPath = new LinkedList<Edge<T>>();
    Edge<T> bestEdge = p_v[numNodes-1];
    bestEdge = p_v[bestEdge.start().id()]; // Skip the goal node
    while(bestEdge.start().id() != ROOT_ID) {
      bestPath.add(0, bestEdge); // O(c) pre-pend
      bestEdge = p_v[bestEdge.start().id()];
    }

    return bestPath;
  }
  
  public Node<T> getNode(int id, boolean addIfNotPresent) {
    if ( !contains(id) && addIfNotPresent) {
      for (int i = nodeList.size(); i <= id; ++i) {
        nodeList.add(new Node<T>(i));
      }
    }
    return getNode(id);
  }
  
  public Node<T> getNode(int id) {
    if (contains(id)) {
      return nodeList.get(id);
    }
    throw new IndexOutOfBoundsException(String.format("Node id %d exceeds maximum: %d", id, nodeList.size()-1));
  }
  
  public boolean contains(int id) {
    return id >= ROOT_ID && id < nodeList.size();
  }
  
  /**
   * Constructs a lattice from a lattice encoded in PLF.
   * 
   * NOTE: For the predictPinchPoints functionality to work, cdec (or whichever lattice generator)
   * must include the unsegmented tokens in the output lattice.
   * 
   * TODO: This language can be recognized with a PDA. We really should
   * just have a lexer that returns lists of tuples. Right now I'm using some 
   * regexes bootlegged from Joshua:
   * 
   *   http://github.com/joshua-decoder/joshua/blob/master/src/joshua/lattice/Lattice.java
   * 
   * @param plfString
   */
  public static Lattice<String> plfStringToLattice(String plfString, boolean predictPinchPoints) {
    // this matches a sequence of tuples, which describe arcs
    // leaving this node
    final Pattern nodePattern = Pattern.compile("(.+?)\\((\\(.+?\\),)\\)(.*)");
    // this matches a comma-delimited, parenthesized tuple of a
    // (a) single-quoted word (b) a number (c) an offset (how many
    // states to jump ahead)
    final Pattern arcPattern = Pattern.compile("\\('(.+?)',(-?\\d+.?\\d*),(\\d+)\\),(.*)");

    Matcher nodeMatcher = nodePattern.matcher(plfString);

    final Lattice<String> lattice = new Lattice<String>();
    int nodeID = Lattice.ROOT_ID;
    int edgeId = 0;
    int maxPinchPoint = -1;
    while (nodeMatcher.matches()) {
      String nodeData = nodeMatcher.group(2);
      String remainingData = nodeMatcher.group(3);
      
      ++nodeID;
      Node<String> currentNode = lattice.getNode(nodeID, true);
      if (nodeID == 1) {
        currentNode.addInEdge(new Edge<String>("", lattice.identity(), edgeId++, lattice.getRoot()));
        if (predictPinchPoints) {
          lattice.getRoot().setPinchPoint(true);
          currentNode.setPinchPoint(true);
          maxPinchPoint = nodeID;
        }
      }
      
      Matcher arcMatcher = arcPattern.matcher(nodeData);
      while (arcMatcher.matches()) {
        final String arcLabel = arcMatcher.group(1);
        final double arcWeight = Math.abs(Double.valueOf(arcMatcher.group(2)));
        final int destinationNodeID = nodeID + Integer.valueOf(arcMatcher.group(3));
        maxPinchPoint = (destinationNodeID > maxPinchPoint) ? destinationNodeID : maxPinchPoint; 

        String remainingArcs = arcMatcher.group(4);
        arcMatcher = arcPattern.matcher(remainingArcs);
        
        Node<String> destinationNode = lattice.getNode(destinationNodeID, true);
        Edge<String> edge = new Edge<String>(arcLabel, arcWeight, edgeId++, currentNode);
        destinationNode.addInEdge(edge);
      }
      if (predictPinchPoints) lattice.getNode(maxPinchPoint).setPinchPoint(true);

      nodeMatcher = nodePattern.matcher(remainingData);
    }
    // Add the goal node
    Node<String> lastNode = lattice.nodeList.get(lattice.nodeList.size()-1);
    Node<String> goalNode = lattice.getNode(lattice.nodeList.size(), true);
    goalNode.addInEdge(new Edge<String>("", lattice.identity(), edgeId++, lastNode));
    if (predictPinchPoints) {
      lastNode.setPinchPoint(true);
      goalNode.setPinchPoint(true);
    }
    // nodeList should be in topological order at this point, so no need to sort
    return lattice;
  }
  
  /**
   * Only store forward edges since this is a DAG.
   * 
   * @author Spence Green
   *
   * @param <T>
   */
  public static class Node<T> implements Comparable<Node<T>> {
    private final int id;
    private final List<Edge<T>> inEdges;
    private boolean isPinchPoint = false;
    
    public Node(int id) {
      this.id = id;
      inEdges = new ArrayList<Edge<T>>();
    }
    
    public void addInEdge(Edge<T> e) { inEdges.add(e); }
    
    public List<Edge<T>> backwardStar() { return inEdges; }
    
    public int id() { return id; }

    public boolean isPinchPoint() { return isPinchPoint; }
    
    public void setPinchPoint(boolean b) { isPinchPoint = b; }
    
    @Override
    public String toString() {
      return String.format("[ id: %d  #edges: %d  isPinch: %b]", id, inEdges.size(), isPinchPoint);
    }
    
    // Topological ordering
    @Override
    public int compareTo(Node<T> o) {
      return this.id - o.id;
    }
  }
  
  /**
   * Only stores reference to the "other" Node, since Edge objects
   * are held in Node lists.
   * 
   * @author Spence Green
   *
   * @param <T>
   */
  public static class Edge<T> {
    
    private final T item;
    private final double weight;
    private final int id;
    private Node<T> start;
    
    public Edge(T item, double weight, int id, Node<T> start) {
      this.item = item;
      this.weight = weight;
      this.id = id;
      this.start = start;
    }
    
    public T item() { return item; }
    public double weight() { return weight; }
    public int id() { return id; }
    public Node<T> start() { return start; }
    
    @Override
    public String toString() {
      return String.format("[ id: %s  wt: %.4f  start: %d  item: %s ]", id, weight, start.id, item.toString());
    }
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length > 0 && args[0].equals("-help")) {
      System.err.printf("Usage: java %s [-help] < plf_file%n", Lattice.class.getName());
      System.exit(-1);
    }

    // TODO(spenceg): Make configurable
    final String segmentationMarker = "#";
    
    BufferedReader reader = new BufferedReader(new InputStreamReader(
        new BufferedInputStream(System.in)));
    try {
      for (String line; (line = reader.readLine()) != null; ) {
        if (line.length() == 0 || line.equals("()")) {
          // Don't parse empty lines
          System.out.println();
          continue;
        }

        Lattice<String> lattice = Lattice.plfStringToLattice(line, true);
        List<Edge<String>> bestPath = lattice.viterbiPath();
        boolean printSpace = false;
        for (Edge<String> edge : bestPath) {
          if (printSpace) System.out.print(" ");
          if (! edge.start().isPinchPoint()) System.out.print(segmentationMarker);
          System.out.print(edge.item());
          printSpace = true;
        }
        System.out.println();
      }
      
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
