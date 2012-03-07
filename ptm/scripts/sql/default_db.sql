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
INSERT INTO tm_experimentmodule VALUES (1,1,'Debug tr','Schizophrenia_Wikipedia,Tornado_Wikipedia','In this interface, you will see a single source sentence to translate. Type the translation into the textbox and select Submit. The idle timer will appear in the lower left of the interface. If you do not enter any text into the textbox for longer than 3 minutes, then the timer will expire, and your translation will be automatically submitted. You will proceed to the next sentence.');
INSERT INTO tm_experimentmodule VALUES (2,2,'Debug meedan','Free_will_Wikipedia,Sun_Wikipedia','In this interface, you will see a single source sentence to translate. A suggested translation, which was created by a machine, will appear in the textbox. Either edit the suggested translation, or delete it and create a new translation. Your decision should be based on speed: choose the fastest method to translate the source sentence. The idle timer will appear in the lower left of the interface. If you do not enter any text into the textbox for longer than 3 minutes, then the timer will expire, and your translation will be automatically submitted. You will proceed to the next sentence.');
COMMIT;
