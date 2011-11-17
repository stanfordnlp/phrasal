package edu.stanford.nlp.mt.sem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.GrammaticalRelation;
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
   
   static private class ExtendedExtractionFrame extends ExtractionFrame {
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
   
   public static List<RelationTransferRule> extractPhrToPhrRule(AlignedPair aPair) {
      // public RelationTransferRule(PhrasalRelationCF eDep, PhrasalRelationCF fDep) {
      // PhrasalRelationCF(String type, String[] gov, String[] children, boolean rightChildren) 
      List<RelationTransferRule> rules = new LinkedList<RelationTransferRule>();
      //System.err.printf("aPair:\n\n%s\n\n", aPair);
      
      Map<List<Integer>, Set<RelationTransferRule>> ruleClip = new HashMap<List<Integer>, Set<RelationTransferRule>>();
      
      List<ExtractionFrame> eFrames = new ArrayList<ExtractionFrame>();
      
      for (int eChildI = 0; eChildI < aPair.eLeaves.length; eChildI++) {         
         if (aPair.e2f[eChildI] == null) {
            continue;
         }         
         if (aPair.eParents[eChildI] == null) {
            continue;
         }

         int fChildI = aPair.e2f[eChildI].index()-1;
         
         if (aPair.fParents[fChildI] == null) {
            continue;
         }   
                  
         for (TypedDependency etd : aPair.eParents[eChildI]) {            
            int eGovI = etd.gov().index() -1;
            if (eGovI == -1) continue;
            for (TypedDependency ftd : aPair.fParents[fChildI]) {
               int fGovI = ftd.gov().index() -1;
               if (fGovI == -1) continue;               
               
               if ((aPair.e2f[eGovI] == null || aPair.f2e[fGovI] == null || aPair.e2f[eGovI] == ftd.gov())) {
                  ExtractionFrame ef = new ExtractionFrame(etd, ftd);
                  eFrames.add(ef);
               }
            }
         }
         /*
         Set<ExtendedExtractionFrame> extParentFrames = new HashSet<ExtendedExtractionFrame>();
         
         for (ExtractionFrame eFrame : eFrames) {
            extParentFrames.add(new ExtendedExtractionFrame(eFrame.eDep, eFrame.fDep, eFrame.eDep, eFrame.fDep));
           / * System.err.printf("eFindAllParnt: %s\n",   findAll(eFrame.eDep, aPair.eParents, true));
            System.err.printf("eFindAllChild: %s\n", findAll(eFrame.eDep, aPair.eChildren, false));
            System.err.printf("fFindAllParnt: %s\n",   findAll(eFrame.fDep, aPair.fParents, true));
            System.err.printf("fFindAllChild: %s\n\n", findAll(eFrame.fDep, aPair.fChildren, false)); * /
            Set<TypedDependency> eCanidateDeps = new HashSet<TypedDependency>();
            eCanidateDeps.addAll(findAll(eFrame.eDep, aPair.eParents, true));
            eCanidateDeps.addAll(findAll(eFrame.eDep, aPair.eChildren, false));
            
            Set<TypedDependency> fCanidateDeps = new HashSet<TypedDependency>();
            fCanidateDeps.addAll(findAll(eFrame.fDep, aPair.fParents, true));
            fCanidateDeps.addAll(findAll(eFrame.fDep, aPair.fChildren, false));
            
            for (TypedDependency eCanidate : eCanidateDeps) {            
               for (TypedDependency fCanidate : fCanidateDeps) {
                  extParentFrames.add(new ExtendedExtractionFrame(eFrame.eDep, eFrame.fDep, eCanidate, fCanidate));
               }
            }            
         }
          
         List<ExtractionFrame> sortedFrames = new ArrayList<ExtractionFrame>(extParentFrames);
         Collections.sort(sortedFrames, new Comparator<ExtractionFrame>() {

            @Override
            public int compare(ExtractionFrame o1, ExtractionFrame o2) {
               return o1.toString().compareTo(o2.toString());
            }
         });
         for (ExtractionFrame frame : sortedFrames) {
            //System.err.printf("frame: %s\n", frame);
         }
         */
         
         //System.out.println("Extraction:\n======================");
        
         for (ExtractionFrame frame : eFrames) {
             //System.out.printf("frame: %s\n", frame);
             List<PhraseIndices> childPhrases = extractPhrasesAroundPoint(frame.eDep.dep().index()-1, frame.fDep.dep().index()-1, aPair);         
             List<PhraseIndices> parentPhrases = extractPhrasesAroundPoint(frame.eDep.gov().index()-1, frame.fDep.gov().index()-1, aPair);
             for (PhraseIndices childPhraseIndex : childPhrases) {
                for (PhraseIndices parentPhraseIndex : parentPhrases) {                   
                   PhrasalRelationCF epr = PhrasalRelationCF.fromPoints(frame.eDep.reln(), childPhraseIndex.eStart, childPhraseIndex.eEnd, parentPhraseIndex.eStart, parentPhraseIndex.eEnd, aPair.eLeaves);
                   PhrasalRelationCF fpr = PhrasalRelationCF.fromPoints(frame.fDep.reln(), childPhraseIndex.fStart, childPhraseIndex.fEnd, parentPhraseIndex.fStart, parentPhraseIndex.fEnd, aPair.fLeaves);
                  // System.out.printf("epr: %s\n", epr);
                  // System.out.printf("fpr: %s\n", epr);
                   if (epr != null && fpr != null) {
                      //System.out.println(new RelationTransferRule(epr, fpr));
                      //rules.add(new RelationTransferRule(epr, fpr));
                      List<Integer> clipKey = Arrays.asList(childPhraseIndex.eStart, childPhraseIndex.eEnd, childPhraseIndex.fStart, childPhraseIndex.fEnd);
                      Set<RelationTransferRule> ruleSet = ruleClip.get(clipKey);
                      if (ruleSet == null) {
                         ruleSet = new HashSet<RelationTransferRule>();
                         ruleClip.put(clipKey, ruleSet);
                      }
                      ruleSet.add(new RelationTransferRule(epr, fpr));
                   }                   
                }
             }
         }
      }
      for (Map.Entry<List<Integer>, Set<RelationTransferRule>> entry : ruleClip.entrySet()) {
         rules.addAll(entry.getValue());
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
   
   private static List<TypedDependency> findAll(TypedDependency startDep, TypedDependency[][] links, boolean parents) {
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
         List<RelationTransferRule> rules = extractPhrToPhrRule(aPair);
         for (RelationTransferRule r : rules) {
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
      for (Pair<String, Double> p : Counters.toSortedListWithCounts(ruleCounts)) {
         System.out.printf("%s\t%d\n", p.first, (int)(double)p.second);
      }
   }
}
