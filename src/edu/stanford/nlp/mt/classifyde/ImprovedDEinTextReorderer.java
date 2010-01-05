package mt.classifyde;

import java.util.*;
import java.io.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.ling.*;

public class ImprovedDEinTextReorderer {
  public static void main(String[] args) throws IOException {
    Properties props = StringUtils.argsToProperties(args);

    // (2) setting up the tree & sentence files
    String sentFile = props.getProperty("markedFile", null);
    String treeFile = props.getProperty("treeFile", null);
    SentTreeFileReader reader = new SentTreeFileReader(sentFile, treeFile);
    Tree parsedSent = null;

    while((parsedSent=reader.next())!=null) {
      // Get the index of the DEs
      List<Integer> markedDEIdxs = ExperimentUtils.getMarkedDEIndices(parsedSent.yield());
      List<String> yield = new ArrayList<String>();
      for (HasWord w : parsedSent.yield()) {
        yield.add(w.word());
      }

      Tree newTree = parsedSent.deepCopy();
      List<Tree> newLeaves = newTree.getLeaves();
      // collect the ones to operate on
      for (int deIdx : markedDEIdxs) {
        // first, everything that's not under an NP, remove the label for 的
        String rootLabel = ExperimentUtils.getNPwithDE_rootLabel(parsedSent, deIdx);
        // if not under NP, the 的 tag should be ignored
        if (!rootLabel.equals("NP")) {
          if (!newLeaves.get(deIdx).value().startsWith("的_")) throw new RuntimeException("...");
          newLeaves.get(deIdx).label().setValue("的");
          continue;
        }
        // if DE is not DEC/DEG, they're not going to be reordered.
        // remove the 的 tags as well
        Tree dePOS = newLeaves.get(deIdx).parent(newTree);
        if(!dePOS.value().equals("DEG") && 
           !dePOS.value().equals("DEC")) {
          String newLeaveVal = newLeaves.get(deIdx).value();
          if (newLeaveVal.equals("的_BprepA") || newLeaveVal.equals("的_relc") ||
              newLeaveVal.equals("的_swapped")) {
            newLeaves.get(deIdx).label().setValue("的");
            continue;
          }
        }
        
        String de = yield.get(deIdx);
        if (!de.startsWith("的_"))
          throw new RuntimeException(de+"("+deIdx+") in "+StringUtils.join(yield, " ")+" is not a valid DE");
        // mark the tree
        if (de.equals("的_AsB") || de.equals("的_AprepB") ||
            de.equals("的_AB")  || de.equals("的_noB") ||
            de.equals("的_ordered")) {
          // do nothing
        } else if (de.equals("的_BprepA") || de.equals("的_relc") ||
                   de.equals("的_swapped")) {
          ExperimentUtils.markRotatingDNPorCPinNP(newTree, deIdx);
        }
      }

      // traverse the new tree and reorder!
      Tree tPtr = newTree;
      Queue<Tree> q = new LinkedList<Tree>();
      while(!tPtr.isLeaf() || !q.isEmpty()) {
        if (tPtr.isLeaf()) {
          tPtr = q.remove();
          continue;
        }
        // determine if tPtr needs to be reordered
        boolean needReorder = false;
        for(Tree c : tPtr.children()) {
          if (!c.isLeaf() &&
              c.label().toString().endsWith("r")) {
            needReorder = true;
            //System.err.println("DEBUG: reorder for "+tPtr);
          }
        }
        // reorder if necessary
        List<Tree> newChildren = new ArrayList<Tree>();
        Stack<Tree> childrenStack = new Stack<Tree>();
        if (needReorder) {
          //System.err.println("DEBUG: need reorder");
          for(Tree c : tPtr.children()) {
            //if (c.label().toString().equals("DNPr") ||
            //    c.label().toString().equals("CPr")) {
            if (!c.isLeaf() &&
                c.label().toString().endsWith("r")) {
              Tree newC = ExperimentUtils.processInternalDEReordering(c);
              childrenStack.push(newC);
              //System.err.println("DEBUG: push stack"+c);
            }
            else {
              newChildren.add(c.deepCopy());
              //System.err.println("DEBUG: add child"+c);
            }
          }
          while(!childrenStack.empty()) {
            newChildren.add(childrenStack.pop());
          }
          tPtr.setChildren(newChildren);
        }
        // add children now (after it's updated)
        q.addAll(tPtr.getChildrenAsList());

        // get the next pointer, or quit
        if (q.isEmpty()) break;
        else tPtr = q.remove();
      }
      /*
      parsedSent.pennPrint();
      newTree.pennPrint();
      System.out.println("---------------------------------------------");
      */
      List<String> newYield = new ArrayList<String>();
      for (HasWord w : newTree.yield()) {
        newYield.add(w.word());
      }
      System.out.println(StringUtils.join(newYield, " "));
    }
    
  }
}

