package testing;

import instructable.server.ccg.WeightedCcgExample;

import java.util.List;
import java.util.stream.Collectors;

import utils.ParsingUtils;
import discourseparser.DiscourseParser;

public class PrintExpansionStrategiesStatistics {

	public static void main(String args[]){

		DiscourseParser discourseParser = new DiscourseParser("data/lexiconEntries.txt", "data/lexiconSyn.txt", "/Users/shashans/Work/DialogueData/dialogues/tabseparated/", 1, false);
		List<List<WeightedCcgExample>> ccgExamples = discourseParser.ccgExamples;

		//1. Training set
		List<String> mostCommon = discourseParser.dataStatistics.mostCommon;
		int sz = 0;
		for(List<WeightedCcgExample>sequence:ccgExamples){
			sz +=  sequence.size();
		}

		System.out.println("\n---------------------------------------------------\n");
		System.out.println("Strategy 1: Most common logical forms from training set of size "+sz+" and "+ccgExamples.size()+" sequences\n");
		for(String key:mostCommon){	System.out.println(key+": "+discourseParser.dataStatistics.unigramCount.get(key));}
		System.out.println("\n---------------------------------------------------\n");

		/*
		int sum =0; for(int i=0; i<10; i++){ sum+=discourseParser.dataStatistics.unigramCount.get(mostCommon.get(i));}
		System.out.println("Sum of top 10: "+sum);
		sum =0; for(int i=0; i<15; i++){ sum+=discourseParser.dataStatistics.unigramCount.get(mostCommon.get(i));}
		System.out.println("Sum of top 15: "+sum);
		 */

		int lengths[] = {5,10,15,25,50,100,200};

		for(int k=0; k<lengths.length; k++){
			int count = 0, correct = 0;
			for(List<WeightedCcgExample>sequence:ccgExamples){
				for(int i=0; i<sequence.size();i++){
					if(mostCommon.subList(0, lengths[k]).contains(ParsingUtils.simplify(sequence.get(i)))){
						correct++;
					}
					count++;
				}
			}
			System.out.println("Recall for "+lengths[k]+" most frequent logical forms is: "+ 1.0*correct/count + " with "+correct+" present out of "+count);
		}
		System.out.println("\n---------------------------------------------------\n");

		//2. PMI
		//System.out.println("Strategy 2: highest PMI with previous logical form");
		{
			int count =0, correct = 0;
			for(List<WeightedCcgExample>sequence:ccgExamples){
				for(int i=0; i<sequence.size()-1;i++){
					List<String> highestPMI = discourseParser.dataStatistics.bestPMISuccessors.get(ParsingUtils.simplify(sequence.get(i)));
					if(highestPMI.subList(0, Math.min(highestPMI.size(),10)).contains(ParsingUtils.simplify(sequence.get(i+1)))){
						correct++;
					}
					count++;
				}
			}
			System.out.println("Recall for high PMI logical forms is: "+ 1.0*correct/count + " with "+correct+" present out of "+count);
			System.out.println("\n---------------------------------------------------\n");
		}
		
		//3. Prob
		{
			int count =0, correct = 0;
			for(List<WeightedCcgExample>sequence:ccgExamples){
				for(int i=0; i<sequence.size()-1;i++){
					List<String> highestProb = discourseParser.dataStatistics.bestProbSuccessors.get(ParsingUtils.simplify(sequence.get(i)));
					if(highestProb.subList(0, Math.min(highestProb.size(),10)).contains(ParsingUtils.simplify(sequence.get(i+1)))){
						correct++;
					}
					count++;
				}
			}
			System.out.println("Recall for high probability logical forms is: "+ 1.0*correct/count + " with "+correct+" present out of "+count);
			System.out.println("\n---------------------------------------------------\n");
		}


		//3. Vector Space Representation
	}
}
