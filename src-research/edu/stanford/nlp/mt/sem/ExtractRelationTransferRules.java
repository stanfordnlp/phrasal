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
     int b = node.index()-1;
     if (children[b] != null) for (TypedDependency child : children[b]) {
        if (left) {
          b = Math.min(b, getPhraseBoundry(children, child.dep(), left));
        } else {
          b = Math.max(b, getPhraseBoundry(children, child.dep(), left));
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
   
   public static final boolean DEBUG = true;
   
   public static boolean checkAlignment(AlignedPair ap, int eR, int eL, int fR, int fL) {
      if (DEBUG) {
         System.err.println("\nChecking ("+eL+","+eR+"=>"+fL+","+fR+")"+getPhrase(ap.eLeaves, eL, eR) + " to "+getPhrase(ap.fLeaves, fL, fR));
      }
      for (int i = eL; i <= eR; i++) {
         if (ap.e2f[i] == null) continue;
         if (ap.e2f[i].index()-1 < fL || ap.e2f[i].index()-1 > fR) {
            if (DEBUG) {
               System.err.println("Violation "+ap.eLeaves[i].label() + " => " + ap.e2f[i].label()); 
            }
            return false;
         }
      }
      for (int i = fL; i <= fR; i++) {
         if (ap.f2e[i] == null) continue;
         if (ap.f2e[i].index()-1 < eL || ap.f2e[i].index()-1 > eR) { 
            if (DEBUG) {
               System.err.println("Violation "+ap.fLeaves[i].label() + " => " + ap.f2e[i].label()); 
            }
            return false;
         }
      }
      if (DEBUG) {
        System.err.println("Okay!");
      }
      return true;
   }
   
   public static List<String> extractPhrToPhrRule(AlignedPair aPair) {
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
         int ePhrL = getPhraseBoundry(aPair.eChildren, aPair.eLeaves[eChildI], true);
         int ePhrR = getPhraseBoundry(aPair.eChildren, aPair.eLeaves[eChildI], false);         
         int fPhrL = getPhraseBoundry(aPair.fChildren, aPair.fLeaves[aPair.e2f[eChildI].index()-1], true);
         int fPhrR = getPhraseBoundry(aPair.fChildren, aPair.fLeaves[aPair.e2f[eChildI].index()-1], false);
         
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
         
         // String headXRule = String.format("[X] ||| [%s,1] ||| [%s,1] ", aPair.e2f[eChildI].label().word(),  aPair.e2f[eChildI].label().word(),  aPair.e2f[eChildI].label().word());
         // rulesAtI.add(headXRule);
         
         
         if (aPair.eParents[aPair.e2f[eChildI].index() -1] != null) {
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
          
         for (int eL = ePhrL; eL <= ePhrR; eL++) {
            for (int eR = ePhrR; eR >= eL; eR--) {
               for (int fL = fPhrL; fL <= fPhrR; fL++) {
                  for (int fR = fPhrR; fR >= fL; fR--) {
                     if (checkAlignment(aPair, eR, eL, fR, fL)) {
                        String headPhraseRule = String.format("[%s] ||| %s ||| %s ", aPair.e2f[eChildI].label().word(), 
                              getPhrase(aPair.fLeaves, fL, fR),
                              getPhrase(aPair.eLeaves, eL, eR));
                        rulesAtI.add(headPhraseRule);         
                        if (DEBUG) {
                           System.err.println("Adding: " + headPhraseRule);
                        }
                     }
                  }
               }
            }
         }
         
        
         if (aPair.eChildren[eChildI] != null) for (TypedDependency child : aPair.eChildren[eChildI]) {
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
         
       /* if (ePhrR-ePhrL < MAX_PHRASE_LENGTH && fPhrR-fPhrL < MAX_PHRASE_LENGTH) {                
         
           //System.err.println("added rule "+headRule);
         } */
         
         
         
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
      for (Pair<String, Double> p : Counters.toSortedListWithCounts(ruleCounts)) {
         if (p.first.contains("[X]")) {
            System.out.printf("%s ||| %d\n", p.first, -100);
         } else {
            System.out.printf("%s ||| %f %f 2.718\n", p.first, p.second/total, p.second/total);
         }
      }
   }
}
