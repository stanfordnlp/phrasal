package edu.stanford.nlp.mt.sem;

/**
 * 
 * @author daniel cer
 *
 */
public class RelationTransferRule {
   final PhrasalRelationCF eDep;
   final PhrasalRelationCF fDep;
   private int hashCode;
   
   public RelationTransferRule(PhrasalRelationCF eDep, PhrasalRelationCF fDep) {
      this.eDep = eDep;
      this.fDep = fDep;
   }
   
   public String toString() {
      StringBuilder sbuilder = new StringBuilder();
      sbuilder.append(fDep);
      sbuilder.append(" ||| ");
      sbuilder.append(eDep);
      return sbuilder.toString();
   }
   
   public static RelationTransferRule fromString(String s) {
      String[] f = s.split(" ||| ");
      PhrasalRelationCF eDep;
      PhrasalRelationCF fDep;
      fDep = PhrasalRelationCF.fromString(f[0]);
      eDep = PhrasalRelationCF.fromString(f[1]);
      return new RelationTransferRule(eDep, fDep);
   }
   
   private int lenStrRep;

   public boolean equals(Object o) {
      if (o instanceof RelationTransferRule) {
         RelationTransferRule r = (RelationTransferRule)o;
         if (r.hashCode() != hashCode()) return false;
         if (r.lenStrRep  != lenStrRep)  return false;
         return toString().equals(r.toString());
      } 
      return false;
   }
   
   public int hashCode() {
      if (hashCode == 0) {
        String rep = toString();
        hashCode = rep.hashCode();
        lenStrRep = rep.length();
      }
      return hashCode;
   }
}
