
cut -f1 ptmuser1.reject.txt > ptmuser1.reject.AID.txt

ans2csv.sh ptmuser1-c01.answers.log ptmuser1-c01 ptmuser1/output/ptmuser1_server_info.txt filterList=ptmuser1.reject.AID.txt 

ans2csv.sh ptmuser1-c02.answers.log ptmuser1-c02 ptmuser1/output/ptmuser1_server_info.txt filterList=ptmuser1.reject.AID.txt

ans2csv.sh ptmuser1-c03.answers.log ptmuser1-c03 ptmuser1/output/ptmuser1_server_info.txt filterList=ptmuser1.reject.AID.txt

echo Arabic
./find_baddies.py 48 ptmuser1-c01.RNK_results.csv | sort | uniq -c

echo German
./find_baddies.py 18 ptmuser1-c02.RNK_results.csv | sort | uniq -c

echo French
./find_baddies.py 41 ptmuser1-c03.RNK_results.csv | sort | uniq -c

