package features;

import java.util.HashMap;
import java.util.List;

import utils.CcgParseWrapper;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;

import discourseparser.Decoder;
import instructable.server.ccg.CcgUtils;
import instructable.server.ccg.WeightedCcgExample;

public class FeatureScorer {
	
	//Compute a local score, given a weight vector
	/*
	public static double stateScore(List<WeightedCcgExample> sequence, int index, String state, String curLogicalForm, String prevstate, String prevLogicalForm, HashMap<String, Double > hmap){		
		//Build featureVector and calculate w.f(x,y,z)
		double val = 0.0;
		for(String s:FeatureGenerator.generate(sequence, index, state, curLogicalForm, prevstate, prevLogicalForm)){
			if(hmap.containsKey(s)){
				val += hmap.get(s);
			}
		}
		return val;
	}
	*/
	
	public static ExpressionSimplifier simplifier = CcgUtils.getExpressionSimplifier();
    public static ExpressionComparator comparator = new SimplificationComparator(simplifier);
	
	private List<WeightedCcgExample> sequence;
	private List<List<CcgParseWrapper>> parseLists;
	private HashMap<String,Double> hmap;
	//private boolean[] missing;
	
	
	public FeatureScorer(List<WeightedCcgExample> sequence, HashMap<String, Double > weights, List<List<CcgParseWrapper>> parseBeams){ //, boolean[] missing){
		Preconditions.checkArgument(sequence.size()==parseBeams.size());
		this.sequence = sequence;
		this.hmap = weights;
		this.parseLists = parseBeams;
		//this.missing = missing;
	}
	
	public double stateScore(int index,	//Ranges from 1 to N+1
							String state, int curLogicalIdx,
							String prevstate, int prevLogicalIdx){	
		
		/*Build featureVector and calculate w.f(x,y,z) */
		String curLogicalForm = (index==sequence.size()+1) ? "<STOP>":parseLists.get(index-1).get(curLogicalIdx).getStringLogicalForm(); 	//|| missing[index-1]
		String prevLogicalForm = (index==1) ? "<START>":parseLists.get(index-2).get(prevLogicalIdx).getStringLogicalForm();	//|| missing[index-2]
		boolean isTrue = (index==sequence.size()+1) ? false: parseLists.get(index-1).get(curLogicalIdx).isTrueParse();
		
		//Do not add score from parse features for index=N+1 (no corresponding sentence), or if sentence at current index has missing parse
		double val = (index==sequence.size()+1)? 0.0	//|| missing[index-1] 
				: isTrue ? 0.0 //Math.log(parseLists.get(index-1).get(curLogicalIdx).getCcgParse().getSubtreeProbability()) 
				: 0.0;

		if(Double.isNaN(val)){
			System.out.println("Check 1: "+(index==sequence.size()+1));
			//System.out.println(parseLists.get(index-1).get(curLogicalIdx).getCcgParse().toString());
			System.out.println("Check 2: "+(isTrue)+" corresponds to log of "+parseLists.get(index-1).get(curLogicalIdx).getCcgParse().getSubtreeProbability()+", which is "+Math.log(parseLists.get(index-1).get(curLogicalIdx).getCcgParse().getSubtreeProbability()));
			System.out.println("val should not be initialized to NaN, resetting to 0.0 !");
			val = 0.0;
		}
		Preconditions.checkState(!Double.isNaN(val), "val should be a real number, but is initialized to "+val);

		double discourse_val = 0.0;
		for(String s:FeatureGenerator.generate(sequence, index, state, curLogicalForm, prevstate, prevLogicalForm, isTrue)){
			if(hmap.containsKey(s)){
				discourse_val += hmap.get(s);
				Preconditions.checkState(!Double.isNaN(discourse_val), "val should be a real number, but is NaN after adding weight "+hmap.get(s)+" for feature "+s);
			}
		}
		
		if(Decoder.verbose){	System.out.println("val: "+val+"\tdiscourse_val: "+discourse_val+"\tpercent: "+Math.abs(val)/(Math.abs(val)+Math.abs(discourse_val)));}
		return (val+discourse_val);
	}
	
}
