package edu.stanford.nlp.mt.preordering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.StringLabel;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Pair;

/*
 * @author Sebastian Schuster
 */

public class ClauseTypeLabeller {
  
  
  private static String[] pennPunctTags = {"''", "``", ".", ":", ",", "?", "\"", "!"};

  
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
    private Tree tree;
    private String id;
    private String type;
    private List<Integer> mainVerbIndices;
    private List<Integer> finiteVerbIndices;
    private int negativeParticlePos;
    
    
    public Clause (String id, String type, Tree tree) {
      this.id = id;
      this.type = type;
      this.tree = tree;
      this.negativeParticlePos = -1;
    }
    
    
    public Tree getTree() {
      return tree;
    }
    
    /* Checks if the specified POS Tag corresponds to a finite verb. */
    private boolean isFiniteVerb (String posTag, String word) {
      return ((posTag.equals("MD") || posTag.equals("VBP") 
                   || posTag.equals("VBZ") || posTag.equals("VBD"))
               && (!word.toLowerCase().equals("do") 
                   && !word.toLowerCase().equals("does")) 
                   && !word.toLowerCase().equals("did")) ;
    }
    
    /* Returns true if WORD is not a punctuation mark or a clause 
     * reference.
     */
    private boolean isWord (String word) {
      for (int i = 0; i < pennPunctTags.length; i++) {
        if (word.equals(pennPunctTags[i]))
          return false;
      }
      if (word.startsWith("--C-")) {
        return false;
      }
      return true;
    }
    
    
    private boolean isNot(String word) {
      return (word.toLowerCase().equals("not") 
              ||  word.toLowerCase().equals("n't"));
    }
    
    /* 
     * Returns true if the POS tag of the i-th token is a particle and the verb in front
     * of the particle is a finite verb and the particle is not a negation particle.
     */
    private boolean isFiniteVerbParticle(List<Label> posTags, List<Label> gloss, int i) {
      String pos = posTags.get(i).value();
      String word = gloss.get(i).value();
      return (i > 0
              && pos.equals("RP") //is particle?
              && !isNot(word) //not a negative particle
              && isFiniteVerb(posTags.get(i-1).value(), gloss.get(i-1).value()));
    }
    
    /* 
     * Returns true if the i-th token is a gerund verb and the previous word is a 
     * finite verb.
     */
    private boolean isFiniteGerund(List<Label> posTags, List<Label> gloss, int i) {
      String pos = posTags.get(i).value();
      return (i > 0
              && pos.equals("VBG") 
              && isFiniteVerb(posTags.get(i-1).value(), gloss.get(i-1).value()));
    }
    
     
    private void extractVerbIndices(List<Label> posTags, List<Label> gloss) {
      boolean hasFoundVerbComplex = false;
      for (int i = 0; i < posTags.size(); i++) {
        Label pos = posTags.get(i);
        String word = gloss.get(i).value();
        /* Extract finite verb indices. */
        if (isFiniteVerb (pos.value(), word)
            || isFiniteVerbParticle(posTags, gloss, i)
            || isFiniteGerund(posTags, gloss, i)) {
         
          finiteVerbIndices.add(i);
          hasFoundVerbComplex = true;
          
        } else if (pos.value().startsWith("VB") /* All other verbs are main verbs. */
                   || (pos.value().equals("RP") /* Particles that are attached to main verbs. */
                       && !isNot(word) )) {
          
          /* Don't add gerund verbs without preceding finite or main verb. */
          if (!pos.value().equals("VBG") || (i > 0 &&  (posTags.get(i-1).value().startsWith("VB")))) {
            mainVerbIndices.add(i);
            hasFoundVerbComplex = true;
          }
        } else if ((pos.value().equals("RP") || pos.value().equals("RB")) && isNot(word)) {
          /* Set negative particle index. */
          negativeParticlePos = i;
          
        /* Only extract one verb complex per clause. */
        } else if (hasFoundVerbComplex 
                   && !type.equals("S-INT") 
                   && !pos.value().equals("RB")) {
          break;
        }
      }
      
      
      if (finiteVerbIndices.isEmpty()) {
        finiteVerbIndices.addAll(mainVerbIndices);
        mainVerbIndices.clear();
      }
    }
    
    /* Returns the position before the first NP that is a direct child of the clause. */
    private int getClauseStart(List<Label> posTags, List<Label> gloss) {
      Label npLabel = null;
      int start = -1;
      /* Find the first NP child. */
      for (Tree child : tree.getChildrenAsList()) {
        if (child.value().equals("NP")) {
          npLabel = child.getLeaves().get(0).label();
          break;
        }
      }
      /* Get its index in the clause. */
      if (npLabel != null) {
        int i = 0;
        for (Label word : gloss) {
          if (word.equals(npLabel)) {
            start = i;
            break;
          }
          i++;
        }
      }
      return start;
    }
    
    /* Returns the position before final punctuation marks or another clause. */
    private int getClauseEnd(List<Label> posTags, List<Label> gloss) {
      int end = gloss.size() - 1;
      for (int i = 0; i < gloss.size(); i++) {
        if (gloss.get(i).value().startsWith("--C-") || gloss.get(i).value().equals(":")) {
          if (!finiteVerbIndices.isEmpty() && finiteVerbIndices.get(0) < i) {
            if (i > 0 && (!isWord(gloss.get(i - 1).value()) || posTags.get(i - 1).value().equals("IN")))
              i--;
          
            end = i - 1;
            break;
          }
        }
      }
      if (end == gloss.size() - 1) {
        for (int i = gloss.size() - 1; i > 0; i--) {
          if (isWord(gloss.get(i).value()))
            break;
          end--;
        }
      }
      return end;
    }
    
    
    public String preorder(HashMap<String, Clause> clauses, boolean outputPermutations) {
      finiteVerbIndices = new ArrayList<Integer>();
      mainVerbIndices = new ArrayList<Integer>();
      
      List<Label> posTags = tree.preTerminalYield();
      List<Label> gloss = tree.yield();
      
      extractVerbIndices(posTags, gloss);

      
      
      int len = gloss.size();

      int clauseStart = getClauseStart(posTags, gloss);
      int clauseEnd = getClauseEnd(posTags, gloss);
      
      

      if (type.equals("S-MAIN")) {
        if (!finiteVerbIndices.isEmpty() && !mainVerbIndices.isEmpty()) {
          int c = 0;
          if (negativeParticlePos != -1) {
            Label w = gloss.remove(negativeParticlePos);
            gloss.add(clauseEnd, w);
            c = 1;
          }
            
          for (int idx : mainVerbIndices) {
             Label w = gloss.remove(idx - c++);
             gloss.add(clauseEnd, w);
          }
        } 
      } else if (type.equals("S-EXTR")) {
        if (clauseStart > 0) {
          if (mainVerbIndices.isEmpty()) {
            int c = 0;
            for (int idx : finiteVerbIndices) {
              Label w = gloss.remove(idx);
              gloss.add(clauseStart + c++, w);
            }
          } else if (!finiteVerbIndices.isEmpty() && !mainVerbIndices.isEmpty()) {
            int c = 0;
            if (negativeParticlePos != -1) {
              Label w = gloss.remove(negativeParticlePos);
              gloss.add(clauseEnd, w);
              c = 1;
            }
            for (int idx : mainVerbIndices) {
              Label w = gloss.remove(idx - c++);
              gloss.add(clauseEnd, w);
            }
            c = 0;
            for (int idx : finiteVerbIndices) {
              Label w = gloss.remove(idx);
              gloss.add(clauseStart + c++, w);
            } 
          }
        }
      } else if (type.equals("S-SUB")) {
        int c = 0;
        if (negativeParticlePos != -1) {
          Label w = gloss.remove(negativeParticlePos);
          gloss.add(clauseEnd, w);
          c = 1;
        }
        if (mainVerbIndices.isEmpty()) {
          for (int idx : finiteVerbIndices) {
            Label w = gloss.remove(idx - c++);
            gloss.add(clauseEnd, w);
          } 
        } else {
          for (int idx : mainVerbIndices) {
            Label w = gloss.remove(idx - c++);
            gloss.add(clauseEnd, w);
          }
          c = 0;
          for (int idx : finiteVerbIndices) {
            Label w = gloss.remove(idx - c++);
            gloss.add(clauseEnd, w);
          } 
        } 
      } else if (type.equals("S-XCOMP")) {
        //Do nothing for very short clauses, seems to cause more harm
        //than it helps
        if (gloss.size() > 4) {
          //Add "to" to the list of main verbs
          finiteVerbIndices.add(0, 0);
          int c = 0;
          //Check if the first word is "not"
          if (gloss.get(0).value().toLowerCase().equals("not")) {
            finiteVerbIndices.add(1,1);
          }
          
          //Move verb complex to the end
          for (int idx : finiteVerbIndices) {
            Label w = gloss.remove(idx - c++);
            gloss.add(clauseEnd, w);
          } 
        }
      } else if (type.equals("S-INT")) {
        if (!finiteVerbIndices.isEmpty() && !mainVerbIndices.isEmpty()) {
          int c = 0;
          for (int idx : mainVerbIndices) {
            Label w = gloss.remove(idx - c++);
            gloss.add(clauseEnd, w);
          }
        }
      }
      
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < gloss.size(); i++) {
        String w = gloss.get(i).value();
        if (i > 0)
          sb.append(" ");
        if (w.startsWith("--C-"))
          sb.append(clauses.get(w).preorder(clauses, outputPermutations));
        else {
          CoreLabel cl = (CoreLabel) gloss.get(i);
          if (outputPermutations)
            sb.append(cl.index());
          else
            sb.append(cl.value());
        }
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
