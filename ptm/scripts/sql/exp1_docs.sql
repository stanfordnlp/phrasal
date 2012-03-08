--- 
--- Loads document descriptions for exp1 documents.
---
BEGIN;
INSERT INTO tm_sourcedocumentspec VALUES (1,'Autism_Wikipedia','Autism');
INSERT INTO tm_sourcedocumentspec VALUES (2,'Infinite_monkey_theorem_Wikipedia','a mathematical theory');
INSERT INTO tm_sourcedocumentspec VALUES (3,'Sun_Wikipedia','the Sun');
INSERT INTO tm_sourcedocumentspec VALUES (4,'Schizophrenia_Wikipedia','Schizophrenia');
COMMIT;
