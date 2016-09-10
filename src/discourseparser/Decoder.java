package discourseparser;

import features.FeatureScorer;
import instructable.server.ccg.CcgUtils;
import instructable.server.ccg.WeightedCcgExample;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import utils.CcgParseWrapper;
import utils.ParsingUtils;
import utils.PerceptronUtils;
import utils.SequenceParse;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;

public class Decoder {

	public static boolean verbose = false;
	public static boolean breakSequences = true;
	public static int subsequenceSize = 5;
	
	public static int foundInCandidatesCount = 0;
	public static int notFoundInCandidatesCount = 0;
	
	private static ExpressionSimplifier simplifier = CcgUtils.getExpressionSimplifier();
	private static ExpressionComparator comparator = new SimplificationComparator(simplifier);
	
	//Get parses for individual sentences in a sequence: [N X b] parses
	public static List<List<CcgParse>> getIndividualParsesForSequence(CcgParser parser, List<WeightedCcgExample> sequence, int beamSize){
		return sequence.stream().map( e -> parser.beamSearch(e.getSentence(), beamSize)).collect(Collectors.toList());
	}

	
	public static List<List<List<CcgParseWrapper>>> getGoldAndCandidateParsesForSequence(DiscourseParser discourseParser,
																						 List<WeightedCcgExample> sequence, 
																						 int beamSize, 
																						 int maxLog){

		List<List<List<CcgParseWrapper>>> retVal = new ArrayList<List<List<CcgParseWrapper>>>();	
		List<List<CcgParse>> parseLists = sequence.stream().map( e -> discourseParser.parser.beamSearch(e.getSentence(), beamSize)).collect(Collectors.toList());

		//First add all distinct parses
		List<List<CcgParseWrapper>> candidateParsesList = new ArrayList<List<CcgParseWrapper>>();		
		for(int i=0; i<parseLists.size(); i++){
			//For each sentence in the sequence
			System.out.println("Processing "+parseLists.get(i).size()+" parses for sentence: "+sequence.get(i).getSentence());
			List<CcgParse> parses = parseLists.get(i);
			
			List<CcgParseWrapper> distinctParsesForSentence = new ArrayList<CcgParseWrapper>();
			HashSet<String> hs = new HashSet<String>();		
			HashSet<String> hs_best = new HashSet<String>();
			
			for(int j=0; j<parses.size(); j++){
				if(!hs.contains(ParsingUtils.simplify(parses.get(j))) ){
					if(distinctParsesForSentence.size()>0 && (ParsingUtils.simplify(parses.get(j)).startsWith("(lambda") || ParsingUtils.simplify(parses.get(j)).startsWith("(string") || ParsingUtils.simplify(parses.get(j)).startsWith("**skip")) ){ continue; }
					
					if(verbose) System.out.println("DISTINCT PARSE:\t"+ParsingUtils.simplify(parses.get(j))+" for sentence: "+sequence.get(i).getSentence());
					hs.add(ParsingUtils.simplify(parses.get(j)));
					
					if(distinctParsesForSentence.size()<maxLog){
						distinctParsesForSentence.add(new CcgParseWrapper(parses.get(j)));
						hs_best.add(ParsingUtils.simplify(parses.get(j)));
					}
					
					if(j==0 && ParsingUtils.simplify(parses.get(j)).equals(ParsingUtils.simplify(sequence.get(i))) ){
						if(parses.size()>1){
							if(verbose) System.out.println("FIRST IS AWESOME: gold:"+ParsingUtils.simplify(sequence.get(i))+"|0:"+ParsingUtils.simplify(parses.get(0))+"|1:"+ParsingUtils.simplify(parses.get(1))+" for sentence: "+sequence.get(i).getSentence());
						}else{
							if(verbose) System.out.println("FIRST IS AWESOME: gold:"+ParsingUtils.simplify(sequence.get(i))+"|0:"+ParsingUtils.simplify(parses.get(0))+" for sentence: "+sequence.get(i).getSentence());
						}
					}else if(j!=0 && ParsingUtils.simplify(parses.get(j)).equals(ParsingUtils.simplify(sequence.get(i))) ){
						if(verbose) System.out.println("LIST "+j+" IS AWESOME!! gold:"+ParsingUtils.simplify(sequence.get(i))+"|0:"+ParsingUtils.simplify(parses.get(0))+"|j:"+ParsingUtils.simplify(parses.get(j))+" for sentence: "+sequence.get(i).getSentence());
					}
				}
			}
						
			//Supplement parse beam with other candidate logical forms. Possible strategies:1.Oracle 2.Training 3.PMI 4.VSrepresentations
			candidateParsesList.add(new ArrayList<CcgParseWrapper>());
			candidateParsesList.get(i).addAll(distinctParsesForSentence);	//Add original parser beam
			Preconditions.checkState(distinctParsesForSentence.size()>0, "No distinct parses found for sentence: "+sequence.get(i).getSentence()+" with "+parses.size()+" parses: "+parses);

			//S1: Oracle expansion, if not already present, add true logical form and 9 others as assigned candidate logical forms
			
			/*
			if((new Random()).nextDouble()<=0.8){
				if(!hs_best.contains(ParsingUtils.simplify(sequence.get(i)))){
					candidateParsesList.get(i).add( new CcgParseWrapper(ParsingUtils.simplify(sequence.get(i))) );
					hs_best.add(ParsingUtils.simplify(sequence.get(i)));
				}
			}
			
			for(int c=0;c<9;c++){
				String str = discourseParser.dataStatistics.getUniformRandomLogicalFormFromTraining(); //.mostCommon.get(c); //discourseParser.dataStatistics.getWeightedRandomLogicalFormFromTraining();
				if(!hs_best.contains(str)){
					candidateParsesList.get(i).add(new CcgParseWrapper(str));
					hs_best.add(str);
				}
			}
			*/
			
			
			//S2: Training set expansion: add most common candidates from training set
			/*
			if((new Random()).nextDouble()<=0.0){
				if(!hs_best.contains(ParsingUtils.simplify(sequence.get(i)))){
					candidateParsesList.get(i).add( new CcgParseWrapper(ParsingUtils.simplify(sequence.get(i))) );
					hs_best.add(ParsingUtils.simplify(sequence.get(i)));
				}
			}
			
			int numCands = 0;
			for(int c=0; c<50; c++){
				String str = discourseParser.dataStatistics.mostCommon.get(c);
				if(!hs_best.contains(str) ){
					double fm = features.FeatureGenerator.getLogicalFormsOverlap(str, String.join(" ", sequence.get(i).getSentence().getWords()));
					if(fm>=0.4){
						if(Decoder.verbose) System.out.println("fmeasure GOOD "+fm);
						if(ParsingUtils.simplify(sequence.get(i)).equals(str)){
							System.out.println("Grand success!");
						}
						candidateParsesList.get(i).add(new CcgParseWrapper(str));
						hs_best.add(str);
						numCands++;
					}else{
						if(Decoder.verbose) System.out.println("fmeasure BAD "+fm);
					}
				}
			}
			System.out.println("Training set expansion added "+numCands+" candidates. Gold: "+hs_best.contains(ParsingUtils.simplify(sequence.get(i))));
			*/
			
			//S3: Prob
			/*
			if(i>0){
				List<String> highestProb = new ArrayList<String>();
				for(int j=0;j<parseLists.get(i-1).size();j++){
					List<String>candidates = discourseParser.dataStatistics.bestProbSuccessors.get(ParsingUtils.simplify(parseLists.get(i-1).get(j)));
					if(candidates!=null && candidates.size() > 0){
						highestProb.addAll(candidates);
						break;
					}
				}
				//List<String> highestProb = discourseParser.dataStatistics.bestProbSuccessors.get(ParsingUtils.simplify(sequence.get(i-1)));
				if(highestProb.size() > 0){
					System.out.println("Highest Prob is NOT NULL");
					for(int c=0; c<Math.min(highestProb.size(), 50); c++){
						String str = highestProb.get(c);
						if(!hs_best.contains(str)){
							double fm = features.FeatureGenerator.getLogicalFormsOverlap(str, String.join(" ", sequence.get(i).getSentence().getWords()));
							if(fm > 0.3){
								candidateParsesList.get(i).add(new CcgParseWrapper(str));
								hs_best.add(str);
							}
						}
					}
				}else{
					System.out.println("Highest Prob is NULL");
				}
			}
			*/
			
			/*
			if(i>0){
				List<String> highestProb = discourseParser.dataStatistics.bestProbSuccessors.get(ParsingUtils.simplify(parseLists.get(i-1).get(0)));
				for(int c=0; c<Math.min(highestProb.size(), 10); c++){
					String str = highestProb.get(c);
					if(!hs_best.contains(str)){
						candidateParsesList.get(i).add(new CcgParseWrapper(str));
						hs_best.add(str);
					}
				}
			}*/
			
			//S4: PMI
			/*
			if(i>0){
				List<String> highestPMI = new ArrayList<String>();
				for(int j=0;j<parseLists.get(i-1).size();j++){
					List<String>candidates = discourseParser.dataStatistics.bestPMISuccessors.get(ParsingUtils.simplify(parseLists.get(i-1).get(j)));
					if(candidates!=null && candidates.size() > 0){
						highestPMI.addAll(candidates);
						break;
					}
				}
				//List<String> highestProb = discourseParser.dataStatistics.bestProbSuccessors.get(ParsingUtils.simplify(sequence.get(i-1)));
				if(highestPMI.size()>0){
					System.out.println("Highest PMI is NOT NULL");
					for(int c=0; c<Math.min(highestPMI.size(), 50); c++){
						String str = highestPMI.get(c);
						if(!hs_best.contains(str)){
							double fm = features.FeatureGenerator.getLogicalFormsOverlap(str, String.join(" ", sequence.get(i).getSentence().getWords()));
							if(fm > 0.3){
								candidateParsesList.get(i).add(new CcgParseWrapper(str));
								hs_best.add(str);
							}
						}
					}
				}else{
					System.out.println("Highest PMI is NULL");
				}
			}*/
			
			/*
			if(i>0){
				List<String> highestPMI = discourseParser.dataStatistics.bestPMISuccessors.get(ParsingUtils.simplify(parseLists.get(i-1).get(0)));
				for(int c=0; c<Math.min(highestPMI.size(), 10); c++){
					String str = highestPMI.get(c);
					if(!hs_best.contains(str)){
						candidateParsesList.get(i).add(new CcgParseWrapper(str));
						hs_best.add(str);
					}
				}
			}*/
						
			if(verbose) System.out.println("SZDIST: "+candidateParsesList.get(0).size());
			
			//candidateParsesList.add(distinctParsesForSentence.subList(0, Math.min(maxLog, distinctParsesForSentence.size() ) ) );
		}		
		retVal.add(candidateParsesList);
		
		//Now add best gold parses. If none are present, add a CcgParseWrapper with only the true label (and print NOT AWESOME).
		List<List<CcgParseWrapper>> bestCorrectParsesList = new ArrayList<List<CcgParseWrapper>>();
		for(int i=0;i<parseLists.size();i++){
			
			Boolean foundCorrectInCandidates = false;
			List<CcgParse>correctParses = PerceptronUtils.filterParsesByLogicalForm(sequence.get(i).getLogicalForm(), comparator, parseLists.get(i), true);

			if (correctParses!=null && correctParses.size() > 0) {
				foundCorrectInCandidates = true;
				bestCorrectParsesList.add(Arrays.asList(new CcgParseWrapper(correctParses.get(0)) ) );
			}else{
				
				if(verbose) System.out.println("NOT AWESOME");
				for(int j=0; j<candidateParsesList.get(i).size();j++){
					if(candidateParsesList.get(i).get(j).getStringLogicalForm().equals(ParsingUtils.simplify(sequence.get(i)))){
						Preconditions.checkState(!candidateParsesList.get(i).get(j).isTrueParse(),"This shouldn't be a true parse");
						foundCorrectInCandidates = true;
						bestCorrectParsesList.add(Arrays.asList(new CcgParseWrapper(ParsingUtils.simplify(sequence.get(i))) ) );
						break;
					}
				}
				
				if(!foundCorrectInCandidates){
					bestCorrectParsesList.add(new ArrayList<CcgParseWrapper>(candidateParsesList.get(i)));
				}
				//bestCorrectParsesList.add(Arrays.asList(new CcgParseWrapper(ParsingUtils.simplify(sequence.get(i))) ) );
			}
					
			if(!foundCorrectInCandidates){
				notFoundInCandidatesCount++;
				if(Decoder.verbose) System.out.println("True parse not found in candidate set. Sz: "+bestCorrectParsesList.get(i).size());
			}else{
				foundInCandidatesCount++;
				if(Decoder.verbose) System.out.println("True parse found in candidate set. Sz: "+bestCorrectParsesList.get(i).size());
			}

		}
		retVal.add(bestCorrectParsesList);

		Preconditions.checkState(sequence.size()==candidateParsesList.size() && sequence.size()==bestCorrectParsesList.size());
		return retVal;
	}
	
	
	public static SequenceParse decode(List<WeightedCcgExample> sequence, 
									DiscourseParser discourseParser, 
									int beamSize, 
									int maxLog, 
									Boolean useLabels
									){
		
		System.out.println("\nDecoding new sequence with "+sequence.size()+" elements.");
		List<List<List<CcgParseWrapper>>> pLists = getGoldAndCandidateParsesForSequence(discourseParser, sequence, beamSize, maxLog);
		//boolean[] missing = new boolean[sequence.size()];
		//IntStream.range(0, sequence.size()).forEach( i -> missing[i]=(pLists.get(1).get(i).size()==0));; 		

		System.out.println("Initialization...");
		List<List<CcgParseWrapper>> parseBeams= (useLabels) ? pLists.get(1) : pLists.get(0);
		pLists.get(0).stream().forEach( parses -> Preconditions.checkState(parses.size()>0, "Empty candidate set for sentence!"));
		pLists.get(1).stream().forEach( parses -> Preconditions.checkState(parses.size()>0, "Empty gold set for sentence!"));
		
		FeatureScorer featureScorer = new FeatureScorer(sequence, discourseParser.weights, parseBeams); //, missing);		
				
		/*Initialize states and arrays*/
		String[] states = new String[discourseParser.numStates];
		IntStream.range(0, discourseParser.numStates).forEach( i -> states[i]=("STATE_"+String.valueOf(i)));
		
		int nParse = Math.max(1, Collections.max(parseBeams.stream().map(parseList -> parseList.size()).collect(Collectors.toList())));
		double[][][] vals = new double[states.length][nParse][sequence.size()+2];
		int[][][] statesBackPtr = new int[states.length][nParse][sequence.size()+2];
		int[][][] parseBackPtr = new int[states.length][nParse][sequence.size()+2];

		for(int i=0;i<sequence.size()+2;i++){
			for(int j=0;j<states.length;j++){
				for(int k=0;k<nParse;k++){
					vals[j][k][i] = Double.NEGATIVE_INFINITY;
				}
			}
		}
		
		System.out.println("First token...");
		/*First token, n=1*/
		for(int j=0;j<states.length;j++){
						
			for(int k=0; k<Math.max(parseBeams.get(0).size(),1); k++){
				vals[j][k][1] = featureScorer.stateScore(1, states[j], k, "<START>", -1);
				statesBackPtr[j][k][1] = -1;
				parseBackPtr[j][k][1] = -1;
				//System.out.println("Posn: 1"+"State:"+states[j]+"PrevState:"+"<START>"+" Value:" + vals[j][1]);
			}
		}
		
		System.out.println("Intermediate chart...");
		/*Intermediate tokens from 2 to N*/
		for(int i=2;i<=sequence.size();i++){
			{
				if(verbose) System.out.println("For i="+i+", we have "+states.length+" states, "+parseBeams.get(i-1).size()+" and "+parseBeams.get(i-2).size()+" logical forms!");
				if(verbose) System.out.println("Sentence is "+sequence.get(i-1).getSentence().toString());
				for(int j1=0;j1<states.length;j1++){
					for(int k1=0; k1<Math.max(parseBeams.get(i-1).size(),1); k1++){
						vals[j1][k1][i] = featureScorer.stateScore( i, states[j1], k1, states[0], 0) + vals[0][0][i-1];
						//Preconditions.checkState(Double.isNaN(vals[j1][k1][i]), "vals[%s][%s][%s] is initialized to NaN!\nC1: %s\nC2: %s", j1, k1, i, featureScorer.stateScore( i, states[j1], k1, states[0], 0), vals[0][0][i-1]);
						if(Double.isNaN(vals[j1][k1][i])){
							System.out.println("vals["+j1+"]["+k1+"]["+i+"] is initialized to NaN!");
							System.out.println("C1: "+featureScorer.stateScore( i, states[j1], k1, states[0], 0));
							System.out.println("C2: "+vals[0][0][i-1]);
							System.exit(-1);
						}
						statesBackPtr[j1][k1][i] = 0;
						parseBackPtr[j1][k1][i] = 0;
						for(int j2=0;j2<states.length;j2++){
							for(int k2=0; k2<Math.max(parseBeams.get(i-2).size(),1); k2++){
								if(featureScorer.stateScore( i, states[j1], k1, states[j2], k2) + vals[j2][k2][i-1] > vals[j1][k1][i]){
									vals[j1][k1][i] = featureScorer.stateScore( i, states[j1], k1, states[j2], k2) + vals[j2][k2][i-1];
									if(Double.isNaN(vals[j1][k1][i])){
										System.out.println("vals["+j1+"]["+k1+"]["+i+"] is set to NaN!");
										System.out.println("C1: "+featureScorer.stateScore( i, states[j1], k1, states[j2], k2));
										System.out.println("C2: "+vals[j2][k2][i-1]);
										System.exit(-1);
									}
									statesBackPtr[j1][k1][i] = j2;
									parseBackPtr[j1][k1][i] = k2;
								}
							}
						}
						if(Double.isNaN(vals[j1][k1][i])){
							System.out.println("vals["+j1+"]["+k1+"]["+i+"] is NaN!");
							System.exit(-1);
						}
					}
				}
			}
		}
		
		System.out.println("Final token");
		/*Stop token, n=N+1*/
		double finalv;
		{
			int i= sequence.size()+1;
			finalv = featureScorer.stateScore( i, "<STOP>", -1, states[0], 0) + vals[0][0][i-1];
			if(Double.isNaN(finalv)){
				System.out.println("NaN value for finalv at initialization! "+finalv);
				System.out.println("C1 "+featureScorer.stateScore( i, "<STOP>", -1, states[0], 0));
				System.out.println("C2 "+vals[0][0][i-1]);
				System.exit(0);
			}
			//No sentence probabilities for this token.
					
			statesBackPtr[0][0][i] = 0;
			parseBackPtr[0][0][i] = 0;
			for(int j=0;j<states.length;j++){
				for(int k=0; k<Math.max(parseBeams.get(i-2).size(),1); k++){
					if(featureScorer.stateScore( i, "<STOP>", -1, states[j], k) + vals[j][k][i-1] > finalv){
						finalv = featureScorer.stateScore( i, "<STOP>", -1, states[j], k) + vals[j][k][i-1];
						System.out.println("finalv: "+finalv);
						statesBackPtr[0][0][i] = j;
						parseBackPtr[0][0][i] = k;
					}
				}
			}	
		}
		System.out.println("FinalVal:"+finalv);
		if(Double.isNaN(finalv)){
			System.out.println("NaN value for finalv! "+finalv);
			System.exit(0);
		}
		
		System.out.println("Retracing backpointers...");
		/*Retrace backpointers from backPtr[0][0][sequence.size()+1]*/
		String[] optimalTags = new String[sequence.size()+2];
		int[] optimalParses = new int[sequence.size()+2];
		optimalTags[0]="<START>";
		optimalParses[0]=-1;
		optimalTags[sequence.size()+1]="<STOP>";
		optimalParses[sequence.size()+1]=-1;
		int si=0, pi=0, stmp, ptmp;
		
		for(int i=sequence.size()+1; i>=2; i--){
			optimalTags[i-1] = states[statesBackPtr[si][pi][i]];
			optimalParses[i-1] = parseBackPtr[si][pi][i];
			stmp = statesBackPtr[si][pi][i];
			ptmp = parseBackPtr[si][pi][i];
			si = stmp;
			pi = ptmp;
			featureScorer.stateScore( i, optimalTags[i], optimalParses[i], optimalTags[i-1], optimalParses[i-1]);
			//System.out.println("L is"+l+states[l]);
		}
		featureScorer.stateScore( 1, optimalTags[1], optimalParses[1], "<START>", -1);

		if(verbose) System.out.println("Calculating return value");
		/*Return pair of best path and best individual parses*/
		List<CcgParseWrapper> bestParses = new ArrayList<CcgParseWrapper>();
		for(int i=1;i<sequence.size()+1;i++){
			//if(!missing[i-1]){
			bestParses.add(parseBeams.get(i-1).get(optimalParses[i]));
			//}else{
			//	bestParses.add(null);
			//}
		}
		List<String> bestPath = Arrays.asList(optimalTags).subList(1, sequence.size()+1);

		Preconditions.checkState(bestParses.size()==sequence.size() && bestPath.size()==sequence.size());
		System.out.println("Candidate set recall(running): "+1.0*foundInCandidatesCount/(foundInCandidatesCount+notFoundInCandidatesCount));
		
		return new SequenceParse(bestParses, bestPath);
		
	}	

}
