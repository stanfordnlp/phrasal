package edu.stanford.nlp.mt.preordering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.StringLabel;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Pair;

/*
 * @author Sebastian Schuster
 */

public class ClauseTypeLabeller {
  
  
  private static String[] pennPunctTags = {"''", "``", "-LRB-", "-RRB-", ".", ":", ",", "?", "\"", "!"};

  
  /* adds clause type labels to the parse tree of a sentence 
   * -MAIN: for main clauses
   * -EXTR: for main clauses with a peripheral clause in the prefield
   * -SUB: for clauses with a subordinating conjunction
   * -XCOMP: for infinitival clauses
   * -INT: interrogative clauses 
   */
  public HashMap<String,Clause> labelTree(Tree tree) {
    
    HashMap<String,Clause> clauses = new HashMap<String,Clause>();
    LinkedList<Pair<Tree, Tree>> q = new LinkedList<Pair<Tree, Tree>>();
    List<Tree> leaves = tree.getLeaves();
    Pair<Tree, Tree> root = new Pair<Tree, Tree>(tree, null);
    q.add(root);
    boolean first = true;
    Label clausePosTag = new StringLabel("CLAUSE");

    
    int clauseCounter = 0;
    
    while (!q.isEmpty()) {
      Pair <Tree, Tree> curr = q.pollFirst();
      Tree currentNode = curr.first();
      Tree parentNode = curr.second();
      //check whether the current node is "S","SBAR", "SINV", "SBARQ" or "SQ"
      if (currentNode.label().toString().startsWith("S") || currentNode.label().toString().equals("FRAG")) {
        if (first) {
          //check for interrogative clause
          List<Label>posTags = currentNode.preTerminalYield();
          List<Label>gloss = currentNode.yield();
          String firstWord = gloss.isEmpty() ? null : gloss.get(0).toString();
          
            
          
          String firstChild = currentNode.getChildrenAsList().isEmpty() ? null : currentNode.getChildrenAsList().get(0).label().value();
          if ((firstChild.equals("''") || firstChild.equals("\"")) && currentNode.getChildrenAsList().size() > 1)
            firstChild = currentNode.getChildrenAsList().get(1).label().value();

          
          //question word at the beginning of the sentence
          if (currentNode.label().value().endsWith("Q")) {
            currentNode.label().setValue("S-INT");
          
          //Do, does, did at the beginning of the sentence  
          } else if (firstWord != null && 
              (firstWord.equalsIgnoreCase("Do") || 
                  firstWord.toString().equalsIgnoreCase("Does") || 
                  firstWord.toString().equalsIgnoreCase("Did"))) {
            currentNode.label().setValue("S-INT");
          } else if (firstChild != null && firstChild.equals("NP") || firstChild.equals("S") || firstChild.equals("CC") || firstChild.equals("ADVP")) {
            currentNode.label().setValue("S-MAIN");
          } else {
            currentNode.label().setValue("S-EXTR");
          }
          
          clauseCounter++;
          String clauseId = "--C-" + clauseCounter;
          String clauseType =  currentNode.label().value();
          clauses.put(clauseId, new Clause(clauseId, clauseType, currentNode));
          
          Label l = new StringLabel(clauseId);
          Tree w = new LabeledScoredTreeNode(l);
          List<Tree> child = new ArrayList<Tree>();
          child.add(w);
          Tree pos =  new LabeledScoredTreeNode(clausePosTag, child);

          int idx = parentNode.objectIndexOf(currentNode);
          parentNode.setChild(idx, pos);
          
          
          first = false;
        } else if (parentNode == null || (!parentNode.label().toString().startsWith("S-") || parentNode.label().toString().equals("S-EXTR") || parentNode.label().toString().equals("S-MAIN"))) {
          List<Label>posTags = currentNode.preTerminalYield();
          List<Label>gloss = currentNode.yield();

          String firstChild = currentNode.getChild(0).label().toString();
          String firstWord = gloss.isEmpty() ? null : gloss.get(0).toString();

          //check for infinitival clause
          if (posTags.size() > 1 && posTags.get(0).toString().equals("TO") && (posTags.get(1).toString().equals("VB") || posTags.get(1).toString().equals("RB"))) {
            currentNode.label().setValue("S-XCOMP");
          } else if (posTags.size() > 2 && gloss.get(0).value().toLowerCase().equals("not") && posTags.get(1).toString().equals("TO") && (posTags.get(2).toString().equals("VB") || posTags.get(2).toString().equals("RB"))) {
              
            currentNode.label().setValue("S-XCOMP");

          //check for subordinate clause
          } else if (firstChild.equals("IN") || firstChild.startsWith("WH")) {
            currentNode.label().setValue("S-SUB");
            
            //question word at the beginning of the sentence
          } else if (!posTags.isEmpty() && posTags.get(0).toString().startsWith("W")) {
              currentNode.label().setValue("S-INT");
            
          //Do, does, did at the beginning of the sentence  
          } else if(firstWord != null && 
              (firstWord.equalsIgnoreCase("Do") || 
                  firstWord.toString().equalsIgnoreCase("Does") || 
                  firstWord.toString().equalsIgnoreCase("Did"))) {
            currentNode.label().setValue("S-INT");
          } else {
            currentNode.label().setValue("S-MAIN");
          }
          
          //create a new clause object containing the subtree
          //and the clause boundaries and add it to the list of clauses
          clauseCounter++;
          String clauseId = "--C-" + clauseCounter;
          String clauseType =  currentNode.label().value();
          clauses.put(clauseId, new Clause(clauseId, clauseType, currentNode));
          
          Label l = new StringLabel(clauseId);
          Tree w = new LabeledScoredTreeNode(l);
          List<Tree> child = new ArrayList<Tree>();
          child.add(w);
          Tree pos =  new LabeledScoredTreeNode(clausePosTag, child);
          
          int idx = parentNode.objectIndexOf(currentNode);
          parentNode.setChild(idx, pos);
        }
        
        
      }
      for (Tree child : currentNode.getChildrenAsList()) {
        if (!child.isLeaf()) {
          q.add(new Pair<Tree,Tree>(child, currentNode));
        }
      }
    }
  
    return clauses;
  }
  
  public class Clause {
    private int start;
    private int end;
    private Tree tree;
    private String id;
    private String type;
    
    
    
    public Clause (String id, String type, Tree tree) {
      this.id = id;
      this.type = type;
      this.tree = tree;
    }
    
    /*
     * start: index of first word in the clause
     * end: index of last word in the clause
     * tree: subtree beginning at S node of the clause
     */
    public Clause(int start, int end, Tree tree) {
      this.start = start;
      this.end = end;
      this.tree = tree;
    }
    
    public int getStart() {
      return start;
    }
    
    public int getEnd() {
      return end;
    }
    
    public Tree getTree() {
      return tree;
    }
    
    private boolean isFiniteVerb (String posTag, String word) {
      return ((posTag.equals("MD") || posTag.equals("VBP") || posTag.equals("VBZ") || posTag.equals("VBD"))
          && (!word.toLowerCase().equals("do") && !word.toLowerCase().equals("does")) && !word.toLowerCase().equals("did")) ;
    }
    
    private boolean isPunct (String word) {
      for (int i = 0; i <pennPunctTags.length; i++) {
        if (word.equals(pennPunctTags[i]))
          return true;
      }
      if (word.startsWith("--C-")) {
        return true;
      }
      return false;
    }
    
    private boolean isDo(String word) {
      return (word.toLowerCase().equals("do") || word.toLowerCase().equals("does") || word.toLowerCase().equals("did"));
    }
    
    public String preorder(HashMap<String, Clause> clauses) {
      List<Label> posTags = tree.preTerminalYield();
      List<Label> gloss = tree.yield();
      List<Integer> finiteVerbs = new ArrayList<Integer>();
      List <Integer> mainVerbs = new ArrayList<Integer>();
      int negativeParticlePos = -1;
      int len = gloss.size();
      int terminalPunctCount = 0;
      StringBuilder sb = new StringBuilder();
      boolean hasFoundVerbComplex = false;
      for (int i = 0; i < posTags.size(); i++) {
        Label pos = posTags.get(i);
        String word = gloss.get(i).value();
        if (isFiniteVerb (pos.value(), word)
            || (i > 0 && pos.value().equals("RP") && !word.toLowerCase().startsWith("n") && isFiniteVerb(posTags.get(i-1).value(), gloss.get(i-1).value())) 
            || (i > 0 && pos.value().equals("VBG") && isFiniteVerb(posTags.get(i-1).value(), gloss.get(i-1).value()))
            ) {
          finiteVerbs.add(i);
          hasFoundVerbComplex = true;
        } else if (pos.value().startsWith("VB") || (pos.value().equals("RP") && !word.toLowerCase().startsWith("n") )) {
          
          if (!pos.value().equals("VBG") || (i > 0 &&  (posTags.get(i-1).value().startsWith("VB")))) {
            mainVerbs.add(i);
            hasFoundVerbComplex = true;
          }
        } else if ((pos.value().equals("RP") || pos.value().equals("RB")) && word.toLowerCase().startsWith("n")) {
          negativeParticlePos = i;
        } else if (hasFoundVerbComplex && !type.equals("S-INT") && !pos.value().equals("RB")) {
          
          break;
        }
      }
      
      if (finiteVerbs.isEmpty()) {
        finiteVerbs.addAll(mainVerbs);
        mainVerbs.clear();
      }
      
      for (int i = 0; i < gloss.size(); i++) {
        if (gloss.get(i).value().startsWith("--C-") || gloss.get(i).value().equals(":")) {
          if (!finiteVerbs.isEmpty() && finiteVerbs.get(0) < i) {
            if (i > 0 && (isPunct(gloss.get(i -1).value()) || posTags.get(i - 1).value().equals("IN")))
              i--;
          
            terminalPunctCount = gloss.size() - i;
            break;
          }
        }
      }
      if (terminalPunctCount == 0) {
        for (int i = gloss.size() - 1; i > 0; i--) {
          if (!isPunct(gloss.get(i).value()))
            break;
          terminalPunctCount++;
        }
      }

      if (type.equals("S-MAIN")) {
        if (!finiteVerbs.isEmpty() && !mainVerbs.isEmpty()) {
          int c = 0;
          if (negativeParticlePos != -1) {
            Label w = gloss.remove(negativeParticlePos);
            gloss.add(len - 1 - terminalPunctCount, w);
            c = 1;
          }
            
          for (int idx : mainVerbs) {
             Label w = gloss.remove(idx - c++);
             gloss.add(len - 1 - terminalPunctCount, w);
          }
        } 
      } else if (type.equals("S-EXTR")) {
        int firstCommaIdx = -1;
        int i = 0;
        Label commaLabel = null;
        for (Tree child : tree.getChildrenAsList()) {
          if (child.value().equals(",")) {
            commaLabel = child.getChild(0).label();
            break;
          }
        }
        
        if (commaLabel != null) {
          for (Label word : gloss) {
            if (word.equals(commaLabel)) {
              firstCommaIdx = i;
              break;
            }
            i++;
          }
        } else {
          for (Tree child : tree.getChildrenAsList()) {
            if (child.value().equals("NP")) {
              commaLabel = child.getLeaves().get(0).label();
              break;
            }
          }
          if (commaLabel != null) {
            i = 0;
            for (Label word : gloss) {
              if (word.equals(commaLabel)) {
                firstCommaIdx = i - 1;
                break;
              }
              i++;
            }
          }
        }
        if (commaLabel == null || firstCommaIdx == gloss.size() - 1) {
          firstCommaIdx = -1;
        }       
        if (firstCommaIdx != -1) {
        if (mainVerbs.isEmpty()) {
          int c = 0;
          if (negativeParticlePos != -1) {
            Label w = gloss.remove(negativeParticlePos);
            gloss.add(len - 1 - terminalPunctCount, w);
            c = 1;
          }
          for (int idx : finiteVerbs) {
            Label w = gloss.remove(idx);
            gloss.add(firstCommaIdx + 1 + c++, w);
          }
          if (negativeParticlePos != -1) {
            Label w = gloss.remove(negativeParticlePos);
            gloss.add(firstCommaIdx + 1 + c, w);
          }
        } else if (!finiteVerbs.isEmpty() && !mainVerbs.isEmpty()) {
          int c = 0;
          if (negativeParticlePos != -1) {
            Label w = gloss.remove(negativeParticlePos);
            gloss.add(len - 1 - terminalPunctCount, w);
            c = 1;
          }
          for (int idx : mainVerbs) {
            Label w = gloss.remove(idx - c++);
            gloss.add(len - 1 - terminalPunctCount, w);
          }
          c = 0;
          for (int idx : finiteVerbs) {
            Label w = gloss.remove(idx);
            gloss.add(firstCommaIdx + 1 + c++, w);
          } 
        }
        }
        
      } else if (type.equals("S-SUB")) {
        int c = 0;
        if (negativeParticlePos != -1) {
          Label w = gloss.remove(negativeParticlePos);
          gloss.add(len - 1 - terminalPunctCount, w);
          c = 1;
        }
        if (mainVerbs.isEmpty()) {
          for (int idx : finiteVerbs) {
            Label w = gloss.remove(idx - c++);
            gloss.add(len - 1 - terminalPunctCount, w);
          } 
        } else {
          for (int idx : mainVerbs) {
            Label w = gloss.remove(idx - c++);
            gloss.add(len - 1 - terminalPunctCount, w);
          }
          c = 0;
          for (int idx : finiteVerbs) {
            Label w = gloss.remove(idx - c++);
            gloss.add(len - 1 - terminalPunctCount, w);
          } 
        } 
      } else if (type.equals("S-XCOMP")) {
        //Do nothing for very short clauses, seems to cause more harm
        //than it helps
        if (gloss.size() > 4) {
          //Add "to" to the list of main verbs
          finiteVerbs.add(0, 0);
          int c = 0;
          //Check if the first word is not
          if (gloss.get(0).value().toLowerCase().equals("not")) {
            finiteVerbs.add(1,1);
          }
          
          //Move verb complex to the end
          for (int idx : finiteVerbs) {
            Label w = gloss.remove(idx - c++);
            gloss.add(len - 1 - terminalPunctCount, w);
          } 
        }
      } else if (type.equals("S-INT")) {
        if (!finiteVerbs.isEmpty() && !mainVerbs.isEmpty()) {
          int c = 0;
          for (int idx : mainVerbs) {
            Label w = gloss.remove(idx - c++);
            gloss.add(len - 1 - terminalPunctCount, w);
          }
        }
      }
      for (int i = 0; i < gloss.size(); i++) {
        String w = gloss.get(i).value();
        if (w.startsWith("--C-"))
          sb.append(clauses.get(w).preorder(clauses));
        else
          sb.append(w);
        sb.append(" ");
      }
      return sb.toString();
    }
    
    public String toString(){
      List<Label> gloss = tree.yield();
      StringBuilder sb = new StringBuilder();
      sb.append("[").append(this.type).append("] ");
      for (int i = 0; i < gloss.size(); i++) {
        sb.append(gloss.get(i).toString());
        sb.append(" ");
      }
      sb.append("\n");
      return sb.toString();
    }
    
  }
}
