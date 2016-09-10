package discourseparser;

import instructable.server.ccg.WeightedCcgExample;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import utils.ParsingUtils;

public class DataStatistics implements Serializable {
	
	private static final long serialVersionUID = -5549619597407886250L;
	
	private List<List<WeightedCcgExample>> ccgExamplesTrain;
	public HashMap<String, Integer> unigramCount;
	public ArrayList<String> unigramList;
	public ArrayList<String> mostCommon;
	public HashMap<String, HashMap<String, Integer>> bigramCount;
	public HashMap<String, List<String>> bestPMISuccessors;
	public HashMap<String, List<String>> bestProbSuccessors;
	public Random randomizer;
	
	class Successor{
		String str;
		double val;
		
		public Successor(String str, double val) {
			this.str = str;
			this.val = val;
		}
	}
	
	public DataStatistics(List<List<WeightedCcgExample>> ccgExamples) {
		
		calculateUnigramBigramCounts(ccgExamples);
		mostCommon = (ArrayList<String>) unigramCount.entrySet().stream()
						.sorted( (a,b) -> b.getValue().compareTo(a.getValue())).limit(100)
						.map( e -> e.getKey()).collect(Collectors.toList());
		
		calculateBestPMISuccessors();
		calculateBestProbSuccessors();
		
		this.ccgExamplesTrain = ccgExamples;
		this.randomizer = new Random();
	}
	
	private void calculateBestPMISuccessors() {
		bestPMISuccessors = new HashMap<String, List<String>>();
		for(String prevLogicalForm:bigramCount.keySet()){
			ArrayList<Successor> aL = new ArrayList<DataStatistics.Successor>();
			for(String successor:bigramCount.get(prevLogicalForm).keySet()){
				//Only calculate pmi if type occurs at least 5 times in the data
				if(unigramCount.get(successor)>=5)
					aL.add(new Successor(successor, 1.0 * bigramCount.get(prevLogicalForm).get(successor)/unigramCount.get(successor)) );
			}
			Collections.sort(aL, (o1, o2) -> new Double(o1.val).compareTo(new Double(o2.val)) );
			Collections.reverse(aL);
			/*
			System.out.println("\nPrinting best successors to: "+prevLogicalForm);
			for(int i=0; i<aL.size(); i++){
				System.out.println(aL.get(i).str+"\twith score\t"+aL.get(i).val);
			}
			System.out.println("COUNT IS: "+aL.size());
			*/
			bestPMISuccessors.put(prevLogicalForm, aL.stream().map(s -> s.str).collect(Collectors.toList()));
		}		
	}
	
	private void calculateBestProbSuccessors() {
		bestProbSuccessors = new HashMap<String, List<String>>();
		for(String prevLogicalForm:bigramCount.keySet()){
			ArrayList<Successor> aL = new ArrayList<DataStatistics.Successor>();
			for(String successor:bigramCount.get(prevLogicalForm).keySet()){
				//Only calculate pmi if type occurs at least 5 times in the data
				if(unigramCount.get(successor)>=5)
					aL.add(new Successor(successor, 1.0 * bigramCount.get(prevLogicalForm).get(successor)) );
			}
			Collections.sort(aL, (o1, o2) -> new Double(o1.val).compareTo(new Double(o2.val)) );
			Collections.reverse(aL);

			bestProbSuccessors.put(prevLogicalForm, aL.stream().map(s -> s.str).collect(Collectors.toList()));
		}		
	}

	public String getUniformRandomLogicalFormFromTraining(){
		return unigramList.get(randomizer.nextInt(unigramList.size()));
	}
	
	public String getWeightedRandomLogicalFormFromTraining(){
		int seqIdx = randomizer.nextInt(ccgExamplesTrain.size());
		int sentIdx = randomizer.nextInt(ccgExamplesTrain.get(seqIdx).size());
		return ParsingUtils.simplify(ccgExamplesTrain.get(seqIdx).get(sentIdx));
	}
	
	
	public void calculateUnigramBigramCounts(List<List<WeightedCcgExample>> ccgExamples){
		unigramCount = new HashMap<String, Integer>();
		unigramList = new ArrayList<String>();
		bigramCount = new HashMap<String, HashMap<String,Integer>>();
		
		for(List<WeightedCcgExample>sequence : ccgExamples){
			for(int i=0; i<sequence.size(); i++){
				String currentLogicalForm = ParsingUtils.simplify(sequence.get(i));
				if(unigramCount.containsKey(currentLogicalForm)){
					unigramCount.put(currentLogicalForm, unigramCount.get(currentLogicalForm) + 1);
				}else{
					unigramCount.put(currentLogicalForm, 1);
					unigramList.add(currentLogicalForm);
				}
				
				if(i>0){
					String previousLogicalForm = ParsingUtils.simplify(sequence.get(i-1));
					if(!bigramCount.containsKey(previousLogicalForm)){
						bigramCount.put(previousLogicalForm, new HashMap<String, Integer>());
						bigramCount.get(previousLogicalForm).put(currentLogicalForm, 1);
					}else if(!bigramCount.get(previousLogicalForm).containsKey(currentLogicalForm)){
						bigramCount.get(previousLogicalForm).put(currentLogicalForm, 1);
					}else{
						bigramCount.get(previousLogicalForm).put(currentLogicalForm, bigramCount.get(previousLogicalForm).get(currentLogicalForm)+1);
					}					
				}				
			}
		}
	}

}
