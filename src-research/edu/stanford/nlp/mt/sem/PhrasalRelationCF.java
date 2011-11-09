package edu.stanford.nlp.mt.sem;

import java.util.Arrays;


/**
 * 
 * @author daniel cer
 *
 */
public class PhrasalRelationCF {
   final String type;
   final String[] children;
   final String[] gov;
   final boolean rightChildren;
   
   public PhrasalRelationCF(String type, String[] gov, String[] children, boolean rightChildren) {
      this.type = type;
      this.gov = Arrays.copyOf(gov, gov.length);
      this.children = Arrays.copyOf(children, children.length);
      this.rightChildren = rightChildren;
   }
   
   public String toString() {
      StringBuilder sbuilder = new StringBuilder();      
      if (rightChildren) {
         for (String w : gov) {
           sbuilder.append(w);
           sbuilder.append(" ");
         }
         sbuilder.append(" <<< ");
         sbuilder.append(type);
         sbuilder.append(" <<< ");
         for (String w : children) {
            sbuilder.append(w);
            sbuilder.append(" ");
          }
       } else {
          for (String w : children) {
            sbuilder.append(w);
            sbuilder.append(" ");
          }
          sbuilder.append(" >>> ");
          sbuilder.append(type);
          sbuilder.append(" >>> ");
          for (String w : gov) {
            sbuilder.append(w);
            sbuilder.append(" ");
          }
      }     
      return sbuilder.toString().substring(0, sbuilder.length()-1);
   }
   
   static PhrasalRelationCF fromString(String s) {
      final String type;
      final String[] children;
      final String[] gov;
      final boolean rightChildren;
      String[] fields;
      if (s.contains(" >>> ")) {
         fields = s.split(" >>> ");
         rightChildren = false;
         children = fields[1].split(" ");
         gov = fields[2].split(" ");
      } else if (s.contains(" <<< ")) {
         fields = s.split(" <<< ");
         rightChildren = true;
         children = fields[2].split(" ");
         gov = fields[1].split(" ");
      } else {
         throw new RuntimeException(String.format("Invalid PhrasalDepCF String: '%s'", s));
      }
      type = fields[1];
      
      return new PhrasalRelationCF(type, gov, children, rightChildren);
   }
}
