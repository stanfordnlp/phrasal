Human Ranking of MT Output using MTurk
Described by: Callison-Burch (2009)
Code: http://www.cs.jhu.edu/~ozaidan/maise/

0) Build MAISE

  cd lib/
  javac *.java

1) Add user details (access key and secret key)

  cd MTurkSDKCode
  vim mturk.properties

2) Set the target for the tasks, either sandbox or the live service:

  cd MTurkSDKCode
  vim mturk.properties

3) Modify the HTML/JS template for the task. Also need to update the homeurl() function

  vim maintaskfiles/RNK.shtml
  
4) Create the task (in our case the ranking task, so we will only run this once:

  mk_task.sh

5) Create batches of HITs for the task:

  mk_batch.sh

6) Upload to turk (set the appropriate target in mturk.properties):

  mk_hits.sh

============
Turkers complete the HITs....
============
  
4) Retrieve results for an existing batch:

   mk_hits.sh

5) Remove HITS for an existing batch:

   mk_hits.sh

6) Analyze results of the task (generates a CSV file):

  java -cp lib/ AnalyzeRNKResults answers=maisetutorial-c01.answers.log collection=maisetutorial-c01 serverInfo=maisetutorial/maisetutorial_output/maisetutorial_server_info.txt
