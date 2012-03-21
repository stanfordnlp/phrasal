--- 
--- Loads additional experimental modules and descriptions
---
BEGIN;
INSERT INTO tm_uidescription VALUES(1,1,'In this interface, you will see a single source sentence to translate. Type the translation into the textbox and press Submit. The idle timer will appear in the lower left of the interface. If at anytime you do not enter any text for more than 3 minutes, then the timer will expire, and your translation will be automatically submitted. The site will redirect you to the next sentence.');
INSERT INTO tm_uidescription VALUES(2,2,'In this interface, you will see a single source sentence to translate. The textbox will contain a suggested translation, which was created by a machine. Edit the suggested translation, or delete it and create a new translation. Your decision should be based on speed: choose the fastest method to translate the source sentence. Press Submit to enter your translation. If at anytime you do not enter any text for more than 3 minutes, then the timer will expire, and your translation will be automatically submitted. The site will redirect you to the next sentence.');
--- First module
INSERT INTO tm_experimentmodule VALUES (3,1,'first-tr-MO','Infinite_monkey_theorem_Wikipedia,1896_Summer_Olympics_Wikipedia','x');
INSERT INTO tm_experimentmodule VALUES (4,1,'first-tr-OM','1896_Summer_Olympics_Wikipedia,Infinite_monkey_theorem_Wikipedia','x');
INSERT INTO tm_experimentmodule VALUES (5,1,'first-tr-JS','Flag_of_Japan_Wikipedia,Schizophrenia_Wikipedia','x');
INSERT INTO tm_experimentmodule VALUES (6,1,'first-tr-SJ','Schizophrenia_Wikipedia,Flag_of_Japan_Wikipedia','x');
INSERT INTO tm_experimentmodule VALUES (7,2,'first-meedan-MO','Infinite_monkey_theorem_Wikipedia,1896_Summer_Olympics_Wikipedia','x');
INSERT INTO tm_experimentmodule VALUES (8,2,'first-meedan-OM','1896_Summer_Olympics_Wikipedia,Infinite_monkey_theorem_Wikipedia','x');
INSERT INTO tm_experimentmodule VALUES (9,2,'first-meedan-JS','Flag_of_Japan_Wikipedia,Schizophrenia_Wikipedia','x');
INSERT INTO tm_experimentmodule VALUES (10,2,'first-meedan-SJ','Schizophrenia_Wikipedia,Flag_of_Japan_Wikipedia','x');
--- Second module
INSERT INTO tm_experimentmodule VALUES (11,1,'second-tr-MO','Infinite_monkey_theorem_Wikipedia,1896_Summer_Olympics_Wikipedia','x');
INSERT INTO tm_experimentmodule VALUES (12,1,'second-tr-OM','1896_Summer_Olympics_Wikipedia,Infinite_monkey_theorem_Wikipedia','x');
INSERT INTO tm_experimentmodule VALUES (13,1,'second-tr-JS','Flag_of_Japan_Wikipedia,Schizophrenia_Wikipedia','x');
INSERT INTO tm_experimentmodule VALUES (14,1,'second-tr-SJ','Schizophrenia_Wikipedia,Flag_of_Japan_Wikipedia','x');
INSERT INTO tm_experimentmodule VALUES (15,2,'second-meedan-MO','Infinite_monkey_theorem_Wikipedia,1896_Summer_Olympics_Wikipedia','x');
INSERT INTO tm_experimentmodule VALUES (16,2,'second-meedan-OM','1896_Summer_Olympics_Wikipedia,Infinite_monkey_theorem_Wikipedia','x');
INSERT INTO tm_experimentmodule VALUES (17,2,'second-meedan-JS','Flag_of_Japan_Wikipedia,Schizophrenia_Wikipedia','x');
INSERT INTO tm_experimentmodule VALUES (18,2,'second-meedan-SJ','Schizophrenia_Wikipedia,Flag_of_Japan_Wikipedia','x');
COMMIT;
