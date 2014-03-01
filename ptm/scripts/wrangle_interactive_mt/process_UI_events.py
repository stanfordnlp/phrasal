#!/usr/bin/env python
#
# Get a database log dump and extract out the log json object
# Get a db log dump by executing: 
#./export_sqlite3_script.sh ../tm/db.sqlite3 sql/dump_logs.sql dump.txt
import sys
import codecs
import json
import time
import os

sys.stdout = codecs.getwriter('utf-8')(sys.stdout)

# Cols in the source dump file
SrcInputCols = ["id",
                "user_id",
                "src_document_id",
                "tgt_language_id",
                "interface",
                "order",
                "create_time",
                "start_time",
                "end_time",
                "complete",
                "training",
                "valid",
                "text",
                "log"]

#Get the user token array (remove last entry if empty)    
def getTokens(entry):
    userTokens = entry['keyValues']['userTokens']
    if userTokens[-1] == "":
        userTokens = userTokens[:-1]
    return userTokens

#Find the different tokens in a new set of user tokens
def getNewTokens(entry,state):
    newUserTokens = getTokens(entry)
    #Find the changed token
    idx = len(state.userTokens)
    if idx > len(newUserTokens): return None
    for i,t in enumerate(state.userTokens):
        if t != newUserTokens[i]:
            idx = i
    if idx >= len(state.userTokens)-1:
        return (' ').join(newUserTokens[idx:])
    else:
        return newUserTokens[idx]

#Maintain the state for a given sentence (subElement)
class SentenceState:
    userTokens = [""]
    firstSuggestion = []
    sourceSuggestions = []
    targetSuggestions = []
    lastTargetSuggestion = ""
    lastSourceSuggestion = ""
    def update(self,entry):
        #Capture first suggestions
        if entry['element'] == 'targetBoxes' and 'firstSuggestion' in entry['keyValues']:
            if entry['keyValues']['firstSuggestion'] != "":
                self.firstSuggestion = entry['keyValues']['firstSuggestion']
        #Capture target suggestions
        if entry['element'] == 'targetSuggestions' and 'candidates' in entry['keyValues']:
            if len(entry['keyValues']['candidates']) > 0:
                self.targetSuggestions = entry['keyValues']['candidates']
        #Capture user focus on target suggestion
        if entry['element'] == 'targetSuggestions' and 'optionIndex' in entry['keyValues'] and entry['keyValues']['optionIndex'] is not None:
            self.lastTargetSuggestion = self.targetSuggestions[entry['keyValues']['optionIndex']]
        #Capture source suggestions
        if entry['element'] == 'sourceSuggestions' and 'targets' in entry['keyValues']:
            if len(entry['keyValues']['targets']) > 0:
                self.sourceSuggestions = entry['keyValues']['targets']
        #Capture user focus source suggestion
        if entry['element'] == 'sourceSuggestions' and 'optionIndex' in entry['keyValues'] and entry['keyValues']['optionIndex'] is not None:
            self.lastSourceSuggestion = self.sourceSuggestions[entry['keyValues']['optionIndex']]
        #Capture User tokens
        if entry['element'] == 'targetBoxes' and 'userTokens' in entry['keyValues']:
            self.userTokens = getTokens(entry)

    #Determin the origin of a new user token(s)
    def checkNewToken(self,newToken):
        #Check if token is a suggested word
        if newToken == self.firstSuggestion:
            return "User accepted suggestion \"" + newToken + "\""
        elif newToken in self.targetSuggestions and self.targetSuggestions.index(newToken) > 0:
            return "User changed \"" + self.firstSuggestion + "\" to target suggestion \"" + newToken+"\""
        elif newToken == self.lastSourceSuggestion:
            return "User selected source suggestion \""+newToken +"\"" 
        elif newToken in self.sourceSuggestions and self.sourceSuggestions.index(newToken) > 0:
            return "User changed \"" + self.firstSuggestion + "\" to source suggestion \"" + newToken+"\""
        else:
            return "User typed \"" + newToken + "\""

#Extract the source sentences/tokens
def sourceFromLog(log):
    for entry in log:
        #Capture source sentence
        if entry['element'] == 'ptm' and 'segments' in entry['keyValues']:
            if len(entry['keyValues']['segments']) > 0:
                return entry['keyValues']['segments']

#Create an event sequence from a log file
def processLog(log):
    events = []
    #Get log source
    source = sourceFromLog(log)
    #Maintain state for each sentence
    state = {}
    for entry in log:
        #Get current subelement
        subElement = entry['subElement']
        if subElement not in state:
            state[subElement] = SentenceState()
        #Check for user entry event
        if entry['element'] == 'targetBoxes' and 'userTokens' in entry['keyValues']:
            newToken = getNewTokens(entry,state[subElement])
            if newToken is None: continue
            events.append([entry['time'],state[subElement].checkNewToken(newToken)])
        #Check for sourceBox event
        if entry['element'] == 'sourceSuggestions' and 'source' in entry['keyValues'] and entry['keyValues']['source'] != "":
            events.append([entry['time'],"User hovered on source: "+ entry['keyValues']['source']])
        #Update sentence state
        state[subElement].update(entry)
    return events

def main():
    if len(sys.argv) < 2:
        print "Usage: please enter db dump filename argument"
        return

    #Get the db dump file
    with codecs.open(sys.argv[1],encoding='utf-8') as dbdump:
    #Events structure
    #[timestamp, eventType, sourceword, {metadata(hovertime, targetword etc..)}]
        allEvents = []

        for i,row in enumerate(dbdump):
            # Extract the log
            try:
                log = json.loads(row.split('|')[SrcInputCols.index("log")])
            except ValueError:
                sys.stderr.write('No json file on line: %d%s' % (i,os.linesep))
                continue
            # Process the log
            allEvents.append(processLog(log))
    
        for i,session in enumerate(allEvents):
            print "Session " + str(i)
            for event in session:
                print event[1] + " at "+str(event[0])

if __name__ == '__main__': 
    main()
