package edu.stanford.nlp.mt.sem;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.Pair;

/**
 * 
 * @author daniel cer
 *
 */
public class ExtractRelationTransferRules {
   static private class ExtractionFrame {
      public final TypedDependency eDep;
      public final TypedDependency fDep;
           
      public ExtractionFrame(TypedDependency eDep, TypedDependency fDep) {
         this.eDep = eDep;
         this.fDep = fDep;
      }
      
      private int hashCode = -1;
      public int hashCode() {
         if (hashCode == -1) {
            hashCode  = eDep.hashCode(); hashCode *= 31;
            hashCode += fDep.hashCode();            
         }
         return hashCode;
      }
      
      public String toString() {
         return " E:" + eDep + " <=== F: "+fDep;
      }
      
      /**
       * WARNING: equals backed by reference equality 
       */
      public boolean equals(Object o) {
         if (!(o instanceof ExtractionFrame)) {
            return false;
         }
         ExtractionFrame ef = (ExtractionFrame) o;
         return eDep == ef.eDep &&
                fDep == ef.fDep; 
      }
   }
   
   static class ExtendedExtractionFrame extends ExtractionFrame {
      public final TypedDependency eLinkedDep;
      public final TypedDependency fLinkedDep;
      
      public ExtendedExtractionFrame(TypedDependency eDep, TypedDependency fDep, TypedDependency eLinkedDep, TypedDependency fLinkedDep) {
         super(eDep, fDep);
         this.eLinkedDep = eLinkedDep;
         this.fLinkedDep = fLinkedDep;
      }
      
      private int hashCode = -1;
      public int hashCode() {
         if (hashCode == -1) {
            hashCode = super.hashCode();       hashCode *= 31;
            hashCode += eLinkedDep.hashCode(); hashCode *= 31;
            hashCode += fLinkedDep.hashCode();            
         }
         return hashCode;
      }
      
      /**
       * WARNING: equals backed by reference equality 
       */
      public boolean equals(Object o) {
         if (!(o instanceof ExtendedExtractionFrame)) {
            return false;
         }
         ExtendedExtractionFrame ef = (ExtendedExtractionFrame) o;
         return eLinkedDep == ef.eLinkedDep &&
                fLinkedDep == ef.fLinkedDep &&
                super.equals(o); 
      }
      
      public String toString() {
         return " E:" + eDep + "..." + eLinkedDep + " <=== F: "+fDep  + "..." + fLinkedDep;
      }
   }
   
   public static final int MAX_PHRASE_LENGTH = 5;
   
   static private int getPhraseBoundry(TypedDependency[][] children, TreeGraphNode node, boolean left) {
      return getPhraseBoundry(children, node, left, new HashSet<TreeGraphNode>());
   }
   
   static private int getPhraseBoundry(TypedDependency[][] children, TreeGraphNode node, boolean left, Set<TreeGraphNode> touched) {
     int b = node.index()-1;
     if (children[b] != null) for (TypedDependency child : children[b]) {
        if (touched.contains(child.dep())) continue;
        touched.add(child.dep());
        System.err.println("node: "+node+" child: "+child.dep());
        if (left) {          
            b = Math.min(b, getPhraseBoundry(children, child.dep(), left, touched));
        } else {
          b = Math.max(b, getPhraseBoundry(children, child.dep(), left, touched));
        }
     }
     return b;
   }
   
   public static String getPhrase(TreeGraphNode[] nodes, int l, int r) {
      StringBuilder sb = new StringBuilder();
      for (int i = l; i<=r;i++) {
         sb.append(nodes[i].label().word());
         if (i != r) sb.append(" ");
      }
      return sb.toString();
   }
   
   public static final boolean DEBUG = false;
   
   public static boolean checkAlignment(AlignedPair ap, int eR, int eL, int fR, int fL) {
      if (DEBUG) {
         System.err.println("\tChecking ("+eL+","+eR+"=>"+fL+","+fR+")"+getPhrase(ap.eLeaves, eL, eR) + " to "+getPhrase(ap.fLeaves, fL, fR));
      }
      for (int i = eL; i <= eR; i++) {
         if (ap.e2f[i] == null) continue;
         if (ap.e2f[i].index()-1 < fL || ap.e2f[i].index()-1 > fR) {
            if (DEBUG) {
               System.err.println("\tViolation "+ap.eLeaves[i].label() + " => " + ap.e2f[i].label()); 
            }
            return false;
         }
      }
      for (int i = fL; i <= fR; i++) {
         if (ap.f2e[i] == null) continue;
         if (ap.f2e[i].index()-1 < eL || ap.f2e[i].index()-1 > eR) { 
            if (DEBUG) {
               System.err.println("\tViolation "+ap.fLeaves[i].label() + " => " + ap.f2e[i].label()); 
            }
            return false;
         }
      }
      if (DEBUG) {
        System.err.println("\tOkay!");
      }
      return true;
   }
   
   public static int findInternalPhraseHead(
		   TypedDependency[][] children,
		   TreeGraphNode[] leaves,
		   int L, int R){
	   if (L == R) return L;
	   for (int i = L; i <= R; i++) {
		   int phrLRaw = getPhraseBoundry(children, leaves[i], true);
	       int phrRRaw = getPhraseBoundry(children, leaves[i], false);
	       if (phrLRaw == L && phrRRaw == R) {
	    	   return i;
	       }
	       
	   }
	   return -1;	   
   }
   
   public static List<String> extractPhrToPhrRule(AlignedPair aPair) {
      List<String> rules = new LinkedList<String>();
      List<Set<String>> headRules = new ArrayList<Set<String>>(aPair.fLeaves.length);
      for (int i = 0; i < aPair.fLeaves.length; i++) {
    	  headRules.add(new HashSet<String>());
      }
      for (int eL = 0; eL < aPair.eChildren.length; eL++) {
    	  for (int eR = eL; eR < aPair.eChildren.length; eR++) {
    		  if (eR - eL > MAX_PHRASE_LENGTH) continue;
    		  for (int fL = 0; fL < aPair.fChildren.length; fL++) {
    	    	  for (int fR = fL; fR < aPair.fChildren.length; fR++) {
    	    		  if (fR - fL > MAX_PHRASE_LENGTH) continue;
    	              if (!checkAlignment(aPair, eR, eL, fR, fL)) continue;    	              
    	              String zhPhrase = getPhrase(aPair.fLeaves, fL, fR);
                      String enPhrase = getPhrase(aPair.eLeaves, eL, eR);
                      String xRule = 
    	            	  String.format("[X] ||| %s ||| %s ", 
    	            			  zhPhrase,
    	            			  enPhrase);
    	              rules.add(xRule);
    	              
    	              int internalHead = findInternalPhraseHead(
    	           		   aPair.fChildren, aPair.fLeaves, fL, fR);
    	              if (internalHead != -1) {
    	            	  String iheadRule = 
        	            	  String.format("[%s] ||| %s ||| %s ",
        	            			  aPair.fLeaves[internalHead].label().word(),
        	            			  zhPhrase,
        	            			  enPhrase);
    	            	  rules.add(iheadRule);
    	            	  if (aPair.fChildren[internalHead] != null) {
    	            		  for (TypedDependency dep : aPair.fChildren[internalHead]) {
    	            			  
    	            			  String zhRHS;
    	            			  if (dep.dep().index() > dep.gov().index()) {
    	            				  zhRHS = String.format("[%s,1] [%s,2]", 
    	            						  dep.gov().label().word(),
    	            						  dep.dep().label().word());
    	            			  } else {
    	            				  zhRHS = String.format("[%s,1] [%s,2]", 
    	            						  dep.dep().label().word(),    	            						  
    	            						  dep.gov().label().word());
    	            			  }
    	            			  String enRHS; 
    	            			  // todo check all children
    	            			  TreeGraphNode enDep = aPair.f2e[dep.dep().index()-1];
    	            			  TreeGraphNode enGov = aPair.f2e[dep.gov().index()-1];
    	            			  /*System.err.println("head: " + dep.gov() + " child: "+dep.dep());
    	            			  System.err.println("=enHead: " + enGov + " child: "+enDep);
    	            			  System.err.println(); */
    	            			  if (enDep == null || enGov == null) {
    	            				  enRHS = zhRHS;
    	            			  } else if (enDep.index() > enGov.index()) {
    	            				  if (dep.dep().index() > dep.gov().index()) {
    	            				  enRHS = String.format("[%s,1] [%s,2]", 
    	            						  dep.gov().label().word(),
    	            						  dep.dep().label().word());
    	            				  } else {
    	            					  enRHS = String.format("[%s,2] [%s,1]", 
        	            						  dep.gov().label().word(),
        	            						  dep.dep().label().word());
    	            				  }
    	            			  } else {
    	            				  if (dep.dep().index() <= dep.gov().index()) {
    	            				  enRHS = String.format("[%s,1] [%s,2]", 
    	            						  dep.dep().label().word(),    	            						  
    	            						  dep.gov().label().word());
    	            				  } else {
    	            					  enRHS = String.format("[%s,2] [%s,1]", 
        	            						  dep.dep().label().word(),    	            						  
        	            						  dep.gov().label().word());
    	            				  }
    	            			  }
    	            			  String iheadToChildRule = 
    	        	            	  String.format("[%s] ||| %s ||| %s ",
    	        	            			  aPair.fLeaves[internalHead].label().word(),
    	        	            			  zhRHS,
    	        	            			  enRHS);
    	            			  headRules.get(internalHead).add(iheadToChildRule);
    	            			  System.err.println("internal gov: " + 
    	            					  aPair.fLeaves[internalHead].label().word() + " head: " +
    	            					  aPair.fParents[internalHead][0].gov());
    	            			  
    	            			  if (aPair.fParents[internalHead][0].gov().index() == 0) {
    	            				  String iheadToRoot = String.format("[ROOT] ||| [%s,1] ||| [%s,1] ",
    	        	            			  aPair.fLeaves[internalHead].label().word(),
    	        	            			  aPair.fLeaves[internalHead].label().word());
    	            				  headRules.get(internalHead).add(iheadToRoot);
    	            			  }
    	            	      }	            		  
    	            	  }
    	              }
    	    	  }
    		  }
    	  }     
      }
      for (int i = 0; i < aPair.fLeaves.length; i++) {
    	  rules.addAll(headRules.get(i));
      }
      return rules; 
   }
   public static List<String> extractPhrToPhrRuleOld(AlignedPair aPair) {
      // public RelationTransferRule(PhrasalRelationCF eDep, PhrasalRelationCF fDep) {
      // PhrasalRelationCF(String type, String[] gov, String[] children, boolean rightChildren) 
      List<String> rules = new LinkedList<String>();
      //System.err.printf("aPair:\n\n%s\n\n", aPair);
      
      Map<List<Integer>, Set<RelationTransferRule>> ruleClip = new HashMap<List<Integer>, Set<RelationTransferRule>>();
      
      List<ExtractionFrame> eFrames = new ArrayList<ExtractionFrame>();
      
      
      for (int eChildI = 0; eChildI < aPair.eLeaves.length; eChildI++) {          
         Set<String> rulesAtI = new HashSet<String>();
         if (aPair.e2f[eChildI] == null) {
            continue;
         }
         int ePhrLRaw = getPhraseBoundry(aPair.eChildren, aPair.eLeaves[eChildI], true);
         int ePhrRRaw = getPhraseBoundry(aPair.eChildren, aPair.eLeaves[eChildI], false);         
         int fPhrLRaw = getPhraseBoundry(aPair.fChildren, aPair.fLeaves[aPair.e2f[eChildI].index()-1], true);
         int fPhrRRaw = getPhraseBoundry(aPair.fChildren, aPair.fLeaves[aPair.e2f[eChildI].index()-1], false);
         
         int ePhrL = ePhrLRaw;
         int ePhrR = ePhrRRaw;
         int fPhrL = fPhrLRaw;
         int fPhrR = fPhrRRaw;
         
         for (int i = ePhrLRaw; i <= ePhrRRaw; i++) {
            if (aPair.e2f[i] == null) continue;
            int a = aPair.e2f[i].index() -1;
            fPhrL = Math.min(fPhrL, a);
            fPhrR = Math.max(fPhrR, a);
         }
         
         for (int i = fPhrLRaw; i <= fPhrRRaw; i++) {
            if (aPair.f2e[i] == null) continue;            
            int a = aPair.f2e[i].index() -1;
            System.err.printf("\t%s(%d)->%s(%d)\n", aPair.fLeaves[i], i, aPair.f2e[i], a);
            System.err.printf("[%d,%d]-%d\n", ePhrL, ePhrR, a);
            ePhrL = Math.min(ePhrL, a);
            ePhrR = Math.max(ePhrR, a);
         }
         
         /* System.err.println("e head: "+aPair.eLeaves[eChildI]); 
         System.err.println("f head: "+aPair.e2f[eChildI]);
         System.err.println("ePhrL: "+ePhrL+" ePhrR: "+ePhrR);
         System.err.println("fPhrL: "+fPhrL+" fPhrR: "+fPhrR);
         */
         
         
         String headAloneRule = String.format("[%s] ||| %s ||| %s ", aPair.e2f[eChildI].label().word(), 
               aPair.e2f[eChildI].label().word(), aPair.eLeaves[eChildI].label().word());
         if (DEBUG) {
            System.err.println("headAloneRule: " + headAloneRule);
         }
         rulesAtI.add(headAloneRule);
         
         String headXRule = String.format("[X] ||| [%s,1] ||| [%s,1] ", aPair.e2f[eChildI].label().word(),  aPair.e2f[eChildI].label().word(),  aPair.e2f[eChildI].label().word());
         rulesAtI.add(headXRule);         
         
         if (aPair.fParents[aPair.e2f[eChildI].index() -1] != null) {
           for (TypedDependency p : aPair.fParents[aPair.e2f[eChildI].index() -1]) {
             if (DEBUG) {
                System.err.printf("Parent: %s\n", p.gov());
             }
             if (p.gov().index() == 0) {
                String headRootRule = String.format("[ROOT] ||| [%s,1] ||| [%s,1] ", aPair.e2f[eChildI].label().word(),  aPair.e2f[eChildI].label().word(),  aPair.e2f[eChildI].label().word());
                rulesAtI.add(headRootRule);                
             }
           }
         }
        
         /*String headWholePhraseRule = String.format("[%s] ||| %s ||| %s ", aPair.e2f[eChildI].label().word(), 
               getPhrase(aPair.fLeaves, fPhrL, fPhrR),
               getPhrase(aPair.eLeaves, ePhrL, ePhrR));
               System.err.println("headWholePhraseRule: " + headWholePhraseRule); 
               rulesAtI.add(headWholePhraseRule);
               */
         if (DEBUG) {
            System.err.println("Zh Children: ");            
            if (aPair.e2f[eChildI] != null && aPair.fChildren[aPair.e2f[eChildI].index() -1] != null) {
               for (TypedDependency td : aPair.fChildren[aPair.e2f[eChildI].index() -1]) {
                  System.err.println("\t"+td);
               }
            } else {
               System.err.println("\tnone");
            }
            System.err.println("En Children: ");
            if (aPair.eChildren[eChildI] != null) {
               for (TypedDependency td : aPair.eChildren[eChildI]) {
                  System.err.println("\t"+td);
               }               
            } else {
               System.err.println("\tnone");
            }
            System.err.println("Dependents phrase zh:" + getPhrase(aPair.fLeaves, fPhrL, fPhrR));
            System.err.println("Dependents phrase en:" + getPhrase(aPair.eLeaves, ePhrL, ePhrR));
            
            System.err.println("Dependents phrase zh.orig: " + getPhrase(aPair.fLeaves, fPhrLRaw, fPhrRRaw));
            System.err.println("Dependents phrase en.orig: " + getPhrase(aPair.eLeaves, ePhrLRaw, ePhrRRaw));
         }
         for (int eL = ePhrL; eL <= ePhrR; eL++) {
            for (int eR = ePhrR; eR >= eL; eR--) {
               if (eR - eL > MAX_PHRASE_LENGTH) continue;
               for (int fL = fPhrL; fL <= fPhrR; fL++) {
                  for (int fR = fPhrR; fR >= fL; fR--) {
                     if (fR - fL > MAX_PHRASE_LENGTH) continue;
                     if (checkAlignment(aPair, eR, eL, fR, fL)) {
                        String zhPhrase = zhPhrase = getPhrase(aPair.fLeaves, fL, fR);
                        String enPhrase = getPhrase(aPair.eLeaves, eL, eR);
                        if (eL <= eChildI && eR >= eChildI && 
                           fL <= aPair.e2f[eChildI].index() -1 && fR >= aPair.e2f[eChildI].index() -1) {                           
                           String headPhraseRule = String.format("[%s] ||| %s ||| %s ", aPair.e2f[eChildI].label().word(), 
                                 zhPhrase,
                                 enPhrase);
                           rulesAtI.add(headPhraseRule);
                           if (DEBUG) {
                              System.err.println("Adding: " + headPhraseRule);
                           }
                           continue;
                        } else if ((eL <= eChildI && eR >= eChildI) || 
                                   (fL <= aPair.e2f[eChildI].index() -1 && fR >= aPair.e2f[eChildI].index() -1)) {
                           continue;
                        }
                        
                        if (eChildI < eL) {
                           enPhrase = String.format("[%s,1] %s", aPair.e2f[eChildI].label().word(), enPhrase);
                        } else {
                           enPhrase = String.format("%s [%s,1]", enPhrase, aPair.e2f[eChildI].label().word());
                        }
                        
                        if (aPair.e2f[eChildI].index() -1 < fL) {
                           zhPhrase = String.format("[%s,1] %s", aPair.e2f[eChildI].label().word(), zhPhrase);
                        } else {
                           zhPhrase = String.format("%s [%s,1]", zhPhrase, aPair.e2f[eChildI].label().word());
                        }
                        
                        String headPhraseRule = String.format("[%s] ||| %s ||| %s ", aPair.e2f[eChildI].label().word(), 
                              zhPhrase,
                              enPhrase);
                        rulesAtI.add(headPhraseRule);
                        if (DEBUG) {
                           System.err.println("AddingNewX: " + headPhraseRule);
                        }
                     }
                  }
               }
            }
         }
         
        
         if (aPair.eChildren[eChildI] != null) { 
            for (TypedDependency child : aPair.eChildren[eChildI]) {
               int eCCI = child.dep().index()-1;
               if (aPair.e2f[eCCI] == null) {
                  continue;
               }
               String relRuleE;
               String relRuleF;
   
               
               if (eCCI < eChildI) {
                  if (aPair.e2f[eCCI].index() < aPair.e2f[eChildI].index()) {
                    relRuleE = String.format("[%s,1] [%s,2]", aPair.e2f[eCCI].label().word(), aPair.e2f[eChildI].label().word());
                  } else {
                    relRuleE = String.format("[%s,2] [%s,1]", aPair.e2f[eCCI].label().word(), aPair.e2f[eChildI].label().word());
                  }
               } else {
                  if (aPair.e2f[eCCI].index() >= aPair.e2f[eChildI].index()) { 
                    relRuleE = String.format("[%s,1] [%s,2]", aPair.e2f[eChildI].label().word(), aPair.e2f[eCCI].label().word());
                  } else {
                    relRuleE = String.format("[%s,2] [%s,1]", aPair.e2f[eChildI].label().word(), aPair.e2f[eCCI].label().word());
                  }
               }
               
               if (aPair.e2f[eCCI].index() < aPair.e2f[eChildI].index()) {
                  relRuleF = String.format("[%s,1] [%s,2]", aPair.e2f[eCCI].label().word(), aPair.e2f[eChildI].label().word());               
               } else {
                  relRuleF = String.format("[%s,1] [%s,2]", aPair.e2f[eChildI].label().word(), aPair.e2f[eCCI].label().word());               
               }
               String headDepRule = String.format("[%s] ||| %s ||| %s ", aPair.e2f[eChildI].label().word(),
                     relRuleF,
                     relRuleE);
               rulesAtI.add(headDepRule);            
            }
         }
         
       /* if (ePhrR-ePhrL < MAX_PHRASE_LENGTH && fPhrR-fPhrL < MAX_PHRASE_LENGTH) {                
         
           //System.err.println("added rule "+headRule);
         } */
         
         
         if (DEBUG) {
            System.err.println("Adding Rules:");
            System.err.println(rulesAtI);
         }
         rules.addAll(rulesAtI);         
      }
      
      return rules;      
   }
   
   
   
   private static final int maxPhraseOffset = 5;
   
   static private class PhraseIndices {
      public final int eStart;
      public final int eEnd;
      public final int fStart;
      public final int fEnd;
      
      public PhraseIndices(int eStart, int eEnd, int fStart, int fEnd) {
         this.eStart = eStart;
         this.eEnd = eEnd;
         this.fStart = fStart;
         this.fEnd = fEnd;
      }
   }
   
   static private List<PhraseIndices> extractPhrasesAroundPoint(int ePoint, int fPoint, AlignedPair pair) {
      List<PhraseIndices> allPhraseIndices = new ArrayList<PhraseIndices>();
      for (int eStart = ePoint; eStart >= 0 && eStart > ePoint - maxPhraseOffset; eStart--) {
         for (int eEnd = ePoint; eEnd < pair.e2f.length && eEnd < ePoint + maxPhraseOffset; eEnd++) {
            if (eEnd-eStart > maxPhraseOffset) continue;
            for (int fStart = fPoint;  fStart >= 0 && fStart > fPoint - maxPhraseOffset; fStart--) {
               for (int fEnd = fPoint; fEnd < pair.f2e.length && fEnd < fPoint + maxPhraseOffset; fEnd++) {
                  if (fEnd-fStart > maxPhraseOffset) continue;
                  //System.out.printf("(%d,%d), %d,%d - %d,%d : %s\n", ePoint, fPoint, eStart, eEnd, fStart, fEnd, checkAlignment(eStart, eEnd, fStart, fEnd, pair));
                  if (checkAlignment(eStart, eEnd, fStart, fEnd, pair))
                     allPhraseIndices.add(new PhraseIndices(eStart, eEnd, fStart, fEnd));
               }
            }
         }
      }
      return allPhraseIndices;
   }
   
   static private boolean checkAlignment(int eStart, int eEnd, int fStart, int fEnd, AlignedPair pair) {
      for (int i = eStart; i <= eEnd; i++) {
         if (pair.e2f[i] == null) continue;
         int fIdx = pair.e2f[i].index() -1;
         if (fIdx < fStart || fIdx > fEnd) return false;
      }
      
      for (int i = fStart; i <= fEnd; i++) {
         if (pair.f2e[i] == null) continue;
         int eIdx = pair.f2e[i].index() -1;
         if (eIdx < eStart || eIdx > eEnd) return false;
      }
      
      return true;
   }
   
   static List<TypedDependency> findAll(TypedDependency startDep, TypedDependency[][] links, boolean parents) {
      List<TypedDependency> discoveredDeps = new ArrayList<TypedDependency>();      
      boolean[] reachableNodes = new boolean[links.length];
      reachableNodes[startDep.gov().index()-1] = true;
      reachableNodes[startDep.dep().index()-1] = true;
      discoveredDeps.add(startDep);
      for (LinkedList<TypedDependency> agenda = new LinkedList<TypedDependency>(Arrays.asList(startDep)); agenda.size() != 0; ) {
         TypedDependency dep = agenda.remove();                  
         if (links[(parents ? dep.gov() : dep.dep()).index()-1] == null) continue;
         for (TypedDependency td : links[(parents ? dep.gov() : dep.dep()).index()-1]) {
               int idx = (parents ? td.gov() : td.dep()).index() - 1;
               if (idx == -1) continue;
               discoveredDeps.add(td);
               if (!reachableNodes[idx]) {
                  reachableNodes[idx] = true;
                  agenda.add(td);
               }
         }     
         
      }           
      return discoveredDeps;
   }
      
   
   public static List<RelationTransferRule> extractWordToWordRules(AlignedPair aPair) {
      // public RelationTransferRule(PhrasalRelationCF eDep, PhrasalRelationCF fDep) {
      // PhrasalRelationCF(String type, String[] gov, String[] children, boolean rightChildren) 
      List<RelationTransferRule> rules = new LinkedList<RelationTransferRule>();
      //System.err.printf("aPair:\n\n%s\n\n", aPair);
      
      for (int i = 0; i < aPair.eLeaves.length; i++) {
         if (aPair.e2f[i] == null) {
            continue;
         }  
         if (aPair.eParents[i] == null) {
            continue;
         }
         if (aPair.fParents[aPair.e2f[i].index()-1] == null) {
            continue;
         }
         for (TypedDependency etd : aPair.eParents[i]) {
            int eGov = etd.gov().index() -1;
            if (eGov == -1) continue;
            for (TypedDependency ftd : aPair.fParents[aPair.e2f[i].index()-1]) {
               int fGov = ftd.gov().index() -1;
               if (fGov == -1) continue;
               if (aPair.e2f[eGov] == null) continue;
               if (aPair.e2f[eGov].index()-1 == fGov) {                  
                  PhrasalRelationCF eDep = new PhrasalRelationCF(etd.reln().getShortName(), new String[]{aPair.eLeaves[eGov].label().word().toString()}, 
                        new String[]{aPair.eLeaves[i].label().word().toString()}, (eGov < i));
                  PhrasalRelationCF fDep = new PhrasalRelationCF(ftd.reln().getShortName(), new String[]{aPair.fLeaves[fGov].label().word().toString()}, 
                        new String[]{aPair.fLeaves[aPair.e2f[i].index()-1].label().word().toString()}, (fGov < aPair.e2f[i].index()-1));
                  RelationTransferRule r = new RelationTransferRule(eDep,fDep);
                  rules.add(r);
               }
            }
         }
      }
      return rules;
   }
   
   public static void main(String[] args) throws IOException {
      LineNumberReader reader = new LineNumberReader(new InputStreamReader(System.in));
      ClassicCounter<String> ruleCounts = new ClassicCounter<String>();
      long startTime = System.currentTimeMillis();
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
         AlignedPair aPair = AlignedPair.fromString(line);
         List<String> rules = extractPhrToPhrRule(aPair);
         System.err.println("Final Rules: " + rules);
         for (String r : rules) {
            ruleCounts.incrementCount(r.toString());
         }
         if (reader.getLineNumber() % 100 == 0) {
            System.err.printf("Lines processed >= %d (%.2f lines/second)\n", reader.getLineNumber(),
                  reader.getLineNumber()/((System.currentTimeMillis()-startTime)/1000.0));
         }
      }
      long endTime = System.currentTimeMillis();
      double seconds = (endTime-startTime)/1000.0;
      double linesPerSecond = reader.getLineNumber()/(seconds);
      System.err.println("Done.");
      System.err.printf("Lines processed %d in %.2f seconds (%.2f lines/second)\n", reader.getLineNumber(), seconds, linesPerSecond);
      System.err.printf("Sorting by count\n");
      double total = ruleCounts.totalCount();
      System.out.printf("[ROOT] ||| [X,1] ||| [X,1] ||| 2.718 1 1\n");
      System.out.printf("[X] ||| [X,1] [X,2] ||| [X,1] [X,2] ||| 2.718 1 1\n");
      for (Pair<String, Double> p : Counters.toSortedListWithCounts(ruleCounts)) {                 
         System.out.printf("%s ||| 1 2.718 %f\n", p.first.replace("[,", "[<comma>"), p.second);
      }
   }
}
