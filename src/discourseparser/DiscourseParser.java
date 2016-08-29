package discourseparser;

import features.FeatureGenerator;
import instructable.server.ccg.CcgUtils;
import instructable.server.ccg.WeightedCcgExample;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import parsing.SimpleParserSettings;
import utils.ParsingUtils;
import utils.SequenceParse;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public class DiscourseParser implements Serializable {

	private static final long serialVersionUID = 6598755083763521398L;
	
	/*HashMap to store parameters of structured features*/
	public HashMap<String,Double> weights;
	protected int numStates;

	/*Unstructured parser component*/
	public List<List<String[]>> examplesList;
	public List<List<WeightedCcgExample>> ccgExamples;
	public DataStatistics dataStatistics;
	public CcgParser parser;
	protected ParametricCcgParser parserFamily;
	public SufficientStatistics parserParameters;
	protected SimpleParserSettings sps;

	public DiscourseParser(String lexicon, String lexiconSyn, String trainingDirectory, int numStates, boolean pretrain) {
		
		//Load example sequences, Instantiate unstructured model, but do not train weights
		this.sps = ParsingUtils.createParser(lexicon, lexiconSyn, trainingDirectory, pretrain);
		
		this.examplesList = sps.examples;
		this.ccgExamples = this.getExampleCcgSequences(sps);
		this.dataStatistics = new DataStatistics(ccgExamples);
		
		this.parserFamily = sps.parserFamily;
		this.parserParameters = (pretrain)? sps.parserParameters : parserFamily.getNewSufficientStatistics();
		this.parser = parserFamily.getModelFromParameters(parserParameters);
		
		//Instantiate structured model
		this.numStates = numStates;
		weights = new HashMap<String, Double>();
		
		//Decoder.getGoldAndDistinctIndividualParsesForSequence(this.parser, ccgExamples.stream().flatMap(l -> l.stream()).collect(Collectors.toList()), 200, 15);
		//System.exit(0);
	}
	
	public List<List<WeightedCcgExample>> getNewCcgSequences(String testDirectory){
		List<List<WeightedCcgExample>> exampleCcgSequences = Lists.newArrayList();

		for(List<String[]>sequence:ParsingUtils.loadExampleSequences(testDirectory)){
			List<WeightedCcgExample> exampleCcgSequence = Lists.newArrayList();
			for (int i = 0; i < sequence.size(); i++)
			{
				String exSentence = sequence.get(i)[0];
				String exLogicalForm = sequence.get(i)[1];
				Expression2 expression = ExpressionParser.expression2().parseSingleExpression(exLogicalForm);
				WeightedCcgExample item = CcgUtils.createCcgExample(exSentence, expression, sps.posUsed, false, sps.featureVectorGenerator);
				exampleCcgSequence.add(item);
			}
			
			List<CcgExample> reformattedExamples = Lists.newArrayList();
			for (WeightedCcgExample w : exampleCcgSequence) {
				reformattedExamples.add(new CcgExample(w.getSentence(), null, null, w.getLogicalForm(), null));
			}
			
			exampleCcgSequence = CcgUtils.featurizeExamples(exampleCcgSequence, sps.featureVectorGenerator);
			exampleCcgSequences.add(exampleCcgSequence);		
		}
		return exampleCcgSequences;
	}

	public void displayWeights() {
		System.out.println("Weights:");
		weights.keySet().stream().forEach(key -> System.out.println(key+": "+weights.get(key)));
		System.out.println();
	}

	public void trainWeights(int numPasses, int beamSize, int maxLog){

		Boolean returnAveragedParameters = false;
		Boolean decayStepSize = true;
		double l2penalty = 0.01;
		//int numIterations = numPasses * ccgExamples.size();

		int iter=0,counter=0;
		//parserParameters = (parserParameters==null) ? parserFamily.getNewSufficientStatistics() : parserParameters;
		SufficientStatistics averagedParameters = (returnAveragedParameters)? parserFamily.getNewSufficientStatistics():null;

		for (int passIdx = 0; passIdx < numPasses; passIdx++) {
			System.out.println("Pass "+passIdx);
			List<List<WeightedCcgExample>> shuffledSequences = new LinkedList<List<WeightedCcgExample>>(ccgExamples);
			//Collections.shuffle(shuffledSequences);
			
			int epochCorrect = 0, epochCounter = 0;

			for(List<WeightedCcgExample>sequence:shuffledSequences){
				//Update on single sequence
				//List<WeightedCcgExample> shuffledExamples = new LinkedList<WeightedCcgExample>(sequence);
				//Collections.shuffle(shuffledExamples); parserParameters = PerceptronUtils.updateOnGivenExamples(parserFamily, parserParameters, shuffledExamples, iter);

				/*Begin update on sequence*/	
				SequenceParse bestPredictedSequenceParse = Decoder.decode(sequence, this, beamSize, maxLog, false);
				SequenceParse bestCorrectSequenceParse = Decoder.decode(sequence, this, beamSize, maxLog, true);
				
				
				//Accumulated Batch update for parser parameters
				System.out.println("Making parser updates");
				SufficientStatistics gradient = parserFamily.getNewSufficientStatistics();
				for(int i=0; i<sequence.size();i++){
					
					if(Decoder.verbose) System.out.println("ITER"+(counter++)); //(iter+i));
					WeightedCcgExample example = sequence.get(i);

					/*
					if (bestCorrectSequenceParse.parses.get(i) == null) {
					      //Earlier: If no correct parses in candidates, do not learn parserParams on this example.
					      System.out.println("Search error (Correct): " + example.getSentence() + " " + example.getLogicalForm());
					      //System.out.println("predicted: " + bestPredictedSequenceParse.parses.get(i).getLogicalForm());
					      continue;
					}
					parserFamily.incrementSufficientStatistics(gradient, parserParameters, example.getSentence(), bestPredictedSequenceParse.parses.get(i), -1.0 * Math.abs(example.getWeight()));
					parserFamily.incrementSufficientStatistics(gradient, parserParameters, example.getSentence(), bestCorrectSequenceParse.parses.get(i), 1.0 * Math.abs(example.getWeight()));
					*/
					
					if(bestPredictedSequenceParse.parses.get(i).isTrueParse()){
						parserFamily.incrementSufficientStatistics(gradient, parserParameters, example.getSentence(), bestPredictedSequenceParse.parses.get(i).getCcgParse(), -1.0 * Math.abs(example.getWeight()));
					}
					if(bestCorrectSequenceParse.parses.get(i).isTrueParse()){
						parserFamily.incrementSufficientStatistics(gradient, parserParameters, example.getSentence(), bestCorrectSequenceParse.parses.get(i).getCcgParse(), 1.0 * Math.abs(example.getWeight()));
					}		
					
					int correct = bestPredictedSequenceParse.parses.get(i).getStringLogicalForm().equals(ParsingUtils.simplify(example)) ? 1 : 0;
					epochCorrect+=correct;
					epochCounter++;
				}
				double currentStepSize = decayStepSize ? (1.0 / Math.sqrt(iter + 2)) : 1.0;
				parserParameters.multiply(1.0 - (currentStepSize * l2penalty));
				parserParameters.increment(gradient, currentStepSize);
				this.parser = parserFamily.getModelFromParameters(parserParameters);
				gradient.zeroOut();	
				
				//Update for discourse weights
				HashMap<String, Double> featureMapPrediction = FeatureGenerator.getFeatureMap(sequence, bestPredictedSequenceParse.parses, bestPredictedSequenceParse.bestPath);
				HashMap<String, Double> featureMapCorrect = FeatureGenerator.getFeatureMap(sequence, bestCorrectSequenceParse.parses, bestCorrectSequenceParse.bestPath);
				
				HashSet<String> first = new HashSet<String>(featureMapPrediction.keySet());
				HashSet<String> second = new HashSet<String>(featureMapCorrect.keySet());
				first.addAll(second);
				
				for(String s:first){
					if(!featureMapCorrect.containsKey(s))
						featureMapCorrect.put(s, 0.0);
					if(!featureMapPrediction.containsKey(s))
						featureMapPrediction.put(s, 0.0);
					if(!weights.containsKey(s)){
						weights.put(s, 0.0);
						//cumulativeweights.put(s, 0.0);
					}
					
					weights.put(s, weights.get(s)*(1.0 - (currentStepSize * l2penalty)) );
					weights.put(s, weights.get(s) + currentStepSize*(featureMapCorrect.get(s) - featureMapPrediction.get(s)) );
					//cumulativeweights.put(s, cumulativeweights.get(s)+weights.get(s));
				}
				/*End of Update on Sequence*/
				
				iter+=1; //shuffledExamples.size();
			}
			System.out.println("TRAINING ACCURACY during ITER"+passIdx+" is:"+((double)epochCorrect)/epochCounter);
		}
		SufficientStatistics retVal = ((returnAveragedParameters) ? averagedParameters:parserParameters);
		this.parserParameters = retVal;
		this.parser = parserFamily.getModelFromParameters(parserParameters);
	}

	public List<List<WeightedCcgExample>> getExampleCcgSequences(SimpleParserSettings sps){
		List<List<WeightedCcgExample>> exampleCcgSequences = Lists.newArrayList();

		for(List<String[]>sequence:sps.examples){
			List<WeightedCcgExample> exampleCcgSequence = Lists.newArrayList();
			for (int i = 0; i < sequence.size(); i++)
			{
				String exSentence = sequence.get(i)[0];
				String exLogicalForm = sequence.get(i)[1];
				Expression2 expression = ExpressionParser.expression2().parseSingleExpression(exLogicalForm);
				WeightedCcgExample item = CcgUtils.createCcgExample(exSentence, expression, sps.posUsed, true, sps.featureVectorGenerator);
				exampleCcgSequence.add(item);
			}
			
			List<CcgExample> reformattedExamples = Lists.newArrayList();
			for (WeightedCcgExample w : exampleCcgSequence) {
				reformattedExamples.add(new CcgExample(w.getSentence(), null, null, w.getLogicalForm(), null));
			}
			
			exampleCcgSequence = CcgUtils.featurizeExamples(exampleCcgSequence, sps.featureVectorGenerator);
			exampleCcgSequences.add(exampleCcgSequence);		
		}
		return exampleCcgSequences;
	}

	public void updateModel(){
		parser = parserFamily.getModelFromParameters(parserParameters);
	}
	
}
