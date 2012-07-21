Human Ranking of MT Output using MTurk
Described by: Callison-Burch (2009)
Code: http://www.cs.jhu.edu/~ozaidan/maise/

0) Build MAISE

  cd lib/
  javac *.java

0.5) Add user details (access key and secret key)

  cd MTurkSDKCode
  vim mturk.properties

0.7) Set the target for the tasks, either sandbox or the live service:

  cd MTurkSDKCode
  vim mturk.properties

0.9) Modify the HTML/JS template for the task. Also need to update the homeurl() function

  vim maintaskfiles/RNK.shtml
  
1) Create the task (in our case the ranking task, so we will only run this once:

  java -cp lib/ CreateServerInfo maisetutorial/maisetutorial_task-specs.txt

2) Create batches of HITs for the task:

  java -Xmx300m -cp lib/ CreateBatches serverInfo=maisetutorial/maisetutorial_output/maisetutorial_server_info.txt batchInfo=maisetutorial/maisetutorial_batch-specs.txt templateLoc=maintaskfiles/

2.1) Change the URL in the question file. Upload RNK.shtml and the context-html directory to nlp.stanford.edu/~spenceg/html/

  vim maisetutorial/maisetutorial_output/*.question

3) Upload to turk (set the appropriate target in mturk.properties):

  ant uploader -Dfile=maisetutorial-c01.uploadinfo

============
Turkers complete the HITs....
============
  
4) Retrieve results for an existing batch:

  ant retriever -Danswers=maisetutorial-c01.answers.log -Dfield=keywords -Dquery=maisetutorial-c01

5) Remove HITS for an existing batch:

  ant cleaner -DdelAssignable=true -DdelCompleted=true -Dfield=keywords -Dquery=maisetutorial-c01

6) Analyze results of the task (generates a CSV file):

  java -cp lib/ AnalyzeRNKResults answers=maisetutorial-c01.answers.log collection=maisetutorial-c01 serverInfo=maisetutorial/maisetutorial_output/maisetutorial_server_info.txt
