package mt.tools;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.stats.ClassicCounter;

import mt.*;


public class NBestSourceHistogram {
	private NBestSourceHistogram() { }
	
	static public void main(String[] args) throws IOException {
		if (args.length != 1) {
			System.err.println("Usage:\n\tjava ...NBestSourceHistory (n-best file to analyze)");
			System.exit(-1);
		}
		
		String nbestFilename = args[0];
		MosesNBestList nbestListContainer = new MosesNBestList(nbestFilename);
		List<List<? extends ScoredFeaturizedTranslation<IString,String>>> nbestLists = nbestListContainer.nbestLists();
		
		ClassicCounter<Integer> rankCount = new ClassicCounter<Integer>();
		
		int nbestId = -1;
		long totalCounts = 0;
		for (List<? extends ScoredFeaturizedTranslation<IString,String>> nbestList : nbestLists) {
			nbestId++;
			ClassicCounter<Long> sourceCounts = new ClassicCounter<Long>();
			for (ScoredFeaturizedTranslation<IString, String> sfTrans : nbestList) {
				sourceCounts.incrementCount(sfTrans.latticeSourceId);
			}
			List<Long>  sourceIds = new ArrayList<Long>(sourceCounts.keySet());
			Collections.sort(sourceIds, new CompareIdsByCount(sourceCounts));
			System.out.printf("%d n-best list:\n===============\n", nbestId);
			int rank = -1;
			double sumPercent = 0;
			for (Long id : sourceIds) {
				rank++;
				double percentage = 100*sourceCounts.getCount(id)/nbestList.size();
				sumPercent += percentage;
				System.out.printf("\t%d (%d): %f (/%d %.3f %% cum: %.3f %%)\n", rank, id, sourceCounts.getCount(id), nbestList.size(), percentage, sumPercent);
				rankCount.incrementCount(rank, sourceCounts.getCount(id));
			}
			totalCounts += nbestList.size();
		}
		
		System.out.println();
		System.out.printf("Overall Counts by Rank:\n=================================\n");
		int maxRank = rankCount.size();
		double sumPercent = 0;
		for (int rank = 0; rank < maxRank; rank++) {
			double percentage = 100*rankCount.getCount(rank)/totalCounts;
			sumPercent += percentage;
			System.out.printf("\t%d: %f (%.3f %% cum: %.3f %%)\n", rank, rankCount.getCount(rank), percentage, sumPercent);
		}
	}
	
}

class CompareIdsByCount implements Comparator<Long> {
	ClassicCounter<Long> counts;
	
	public CompareIdsByCount(ClassicCounter<Long> counts) {
		this.counts = counts;
	}
	
	@Override
	public int compare(Long o1, Long o2) {
		return (int)Math.signum(counts.getCount(o2) - counts.getCount(o1));
	};
}