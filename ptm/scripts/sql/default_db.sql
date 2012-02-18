--- 
--- Loads all required data into a blank database
--- for running tmapp
---
BEGIN;
INSERT INTO tm_languagespec VALUES (1,'en','English','ltr');
INSERT INTO tm_languagespec VALUES (2,'ar','Arabic','rtl');
INSERT INTO tm_languagespec VALUES (3,'fr','French','ltr');
INSERT INTO tm_languagespec VALUES (4,'de','German','ltr');
INSERT INTO tm_uispec VALUES (1,'tr');
INSERT INTO tm_uispec VALUES (2,'meedan');
INSERT INTO tm_uispec VALUES (3,'trados');
INSERT INTO tm_uispec VALUES (4,'sjc');
COMMIT;
