package edu.stanford.nlp.mt.sem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import scala.actors.threadpool.Arrays;

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
      public final int eGovI;
      public final int fGovI;
      public final int eChildI;
      public final int fChildI;
      public final String eDep;
      public final String fDep;
           
      public ExtractionFrame(int eGovI, int eChildI, int fGovI, int fChildI, String eDep, String fDep) {
         this.eGovI = eGovI;
         this.fGovI = fGovI;
         this.eChildI = eChildI;
         this.fChildI = fChildI;
         this.eDep = eDep;
         this.fDep = fDep;
      }
      
      private int hashCode = -1;
      public int hashCode() {
         if (hashCode == -1) {
            hashCode  = eDep.hashCode(); hashCode *= 31;
            hashCode += fDep.hashCode(); hashCode *= 31;
            hashCode += eGovI;          hashCode *= 31;
            hashCode += fGovI;          hashCode *= 31;
            hashCode += eChildI;        hashCode *= 31;
            hashCode += fChildI;        hashCode *= 31;
         }
         return hashCode;
      }
      
      public String toString() {
         return "F: "+fDep+"("+fGovI+", "+fChildI+")" + " ==> E:" + eDep + "("+eGovI+", "+eChildI+")";
      }
      
      public boolean equals(Object o) {
         if (!(o instanceof ExtractionFrame)) {
            return false;
         }
         ExtractionFrame ef = (ExtractionFrame) o;
         return eGovI == ef.eGovI &&
                fGovI == ef.fGovI &&
                eChildI == ef.eChildI &&
                fChildI == ef.fChildI &&
                eDep.equals(ef.eDep) &&
                fDep.equals(ef.fDep);
        
      }
   }
   @SuppressWarnings("unchecked")
   public static List<RelationTransferRule> extractPhrToPhrRule(AlignedPair aPair) {
      // public RelationTransferRule(PhrasalRelationCF eDep, PhrasalRelationCF fDep) {
      // PhrasalRelationCF(String type, String[] gov, String[] children, boolean rightChildren) 
      List<RelationTransferRule> rules = new LinkedList<RelationTransferRule>();
      //System.err.printf("aPair:\n\n%s\n\n", aPair);
      
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
                  ExtractionFrame ef = new ExtractionFrame(eGovI, eChildI, fGovI, fChildI, etd.reln().getShortName(), ftd.reln().getShortName());
                  eFrames.add(ef);
               }
            }
         }
         
         Set<ExtractionFrame> extParentFrames = new HashSet<ExtractionFrame>();
         
         for (ExtractionFrame eFrame : eFrames) {
            System.err.printf("eFrame: %s\n", eFrame); 
            Set<Integer> eAllParents = new HashSet<Integer>();
            eAllParents.add(eFrame.eGovI);
            if (aPair.eParents[eFrame.eGovI] != null) { 
               for (TypedDependency td : aPair.eParents[eFrame.eGovI]) {
                   int govIdx = td.gov().index() - 1;
                   if (govIdx == -1) continue;
                   eAllParents.add(govIdx);
               }
                              
               for (LinkedList<Integer> agenda = new LinkedList(eAllParents); agenda.size() != 0; ) {
                  Integer nodeI = agenda.remove();                  
                  if (aPair.eParents[nodeI] == null) continue;
                  for (TypedDependency td : aPair.eParents[nodeI]) {
                        int govIdx = td.gov().index() - 1;
                        if (govIdx == -1) continue;
                        eAllParents.add(govIdx);
                        agenda.add(govIdx);
                  }     
               }               
            }
            
            System.err.printf("eAllParents: %s\n", eAllParents);
            Set<Integer> fAllParents = new HashSet<Integer>();
            fAllParents.add(eFrame.fGovI);
            
            if (aPair.fParents[eFrame.fGovI] != null) { 
               for (TypedDependency td : aPair.fParents[eFrame.fGovI]) {
                   int govIdx = td.gov().index() - 1;
                   if (govIdx == -1) continue;
                   fAllParents.add(govIdx);
               }
                              
               for (LinkedList<Integer> agenda = new LinkedList(fAllParents); agenda.size() != 0; ) {
                  Integer nodeI = agenda.remove();                  
                  if (aPair.fParents[nodeI] == null) continue;
                  for (TypedDependency td : aPair.fParents[nodeI]) {
                        int govIdx = td.gov().index() - 1;
                        if (govIdx == -1) continue;
                        fAllParents.add(govIdx);
                        agenda.add(govIdx);
                  }
               }
            }
            
            System.err.printf("fAllParents: %s\n", fAllParents);
         }
      }
      
      return rules;      
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
      BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
      ClassicCounter<String> ruleCounts = new ClassicCounter<String>();      
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
         AlignedPair aPair = AlignedPair.fromString(line);
         List<RelationTransferRule> rules = extractPhrToPhrRule(aPair);
         for (RelationTransferRule r : rules) {
            ruleCounts.incrementCount(r.toString());
         }
      }
      for (Pair<String, Double> p : Counters.toSortedListWithCounts(ruleCounts)) {
         System.out.printf("%s\t%d\n", p.first, (int)(double)p.second);
      }
   }
}
