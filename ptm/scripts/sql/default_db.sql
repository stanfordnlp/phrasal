--- 
--- Loads all required data into a blank database
--- for running tmapp.
---
--- Also loads two debug experimental modules.
---
BEGIN;
INSERT INTO tm_languagespec VALUES (1,'en','English','ltr');
INSERT INTO tm_languagespec VALUES (2,'ar','Arabic','rtl');
INSERT INTO tm_languagespec VALUES (3,'fr','French','ltr');
INSERT INTO tm_languagespec VALUES (4,'de','German','ltr');
INSERT INTO tm_uispec VALUES (1,'tr','Interface A','False');
INSERT INTO tm_uispec VALUES (2,'meedan','Interface B','True');
INSERT INTO tm_uispec VALUES (3,'trados','Interface C','False');
INSERT INTO tm_uispec VALUES (4,'sjc','Interface D','False');
INSERT INTO tm_experimentmodule VALUES (1,1,'Debug tr','Schizophrenia_Wikipedia,Tornado_Wikipedia','Debug module for no assistance');
INSERT INTO tm_experimentmodule VALUES (2,2,'Debug meedan','Free_will_Wikipedia,Sun_Wikipedia','Debug module for post-editing');
COMMIT;
