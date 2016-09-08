package utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import instructable.server.ccg.WeightedCcgExample;
import instructable.server.ccg.WeightedCcgPerceptronOracle;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgBeamSearchInference;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
//import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
//import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;

public class PerceptronUtils {

	private static final int beamSize= 300;
	private static final Boolean decayStepSize = true;
	private static final double stepSize = 1.0;
	private static final double l2penalty = 0.01;
	private static LogFunction log = new NullLogFunction();
	private static ExpressionSimplifier simplifier = new ExpressionSimplifier(Arrays.<ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(), new VariableCanonicalizationReplacementRule()));
	private static ExpressionComparator comparator = new SimplificationComparator(simplifier);
	private static CcgBeamSearchInference inferenceAlgorithm = new CcgBeamSearchInference(null, comparator, beamSize, -1, Integer.MAX_VALUE, Runtime.getRuntime().availableProcessors(), false);


	public static SufficientStatistics updateOnGivenExamples( 
			ParametricCcgParser parserFamily, 
			SufficientStatistics parserParameters, 
			List<WeightedCcgExample> ccgExamples,
			int iter){

		
		parserParameters = (parserParameters == null) ? parserFamily.getNewSufficientStatistics(): parserParameters;

		SufficientStatistics gradient = parserFamily.getNewSufficientStatistics();
		for(int i=0; i<ccgExamples.size();i++){
			
			System.out.println("ITER"+(iter+i));
			WeightedCcgExample example = ccgExamples.get(i);

			List<CcgParse> parses = inferenceAlgorithm.beamSearch(parserFamily.getModelFromParameters(parserParameters), example.getSentence(), null, log);

			if (parses.size() == 0) { 
				System.err.println("No parse found for sentence: "+example.getSentence());
				System.exit(0);
				continue;
			}
			CcgParse bestPredictedParse = parses.get(0);

			List<CcgParse> correctParses = filterParsesByLogicalForm(example.getLogicalForm(), comparator, parses, example.getWeight() > 0);
			if (correctParses.size() == 0) {
				System.out.println("Search error (Correct): " + example.getSentence() + " " + example.getLogicalForm());
				System.out.println("predicted: " + bestPredictedParse.getLogicalForm());
				continue;
			}
			CcgParse bestCorrectParse = correctParses.get(0);

			//System.out.println(Math.min(0.0, Math.log(bestPredictedParse.getSubtreeProbability()) - Math.log(bestCorrectParse.getSubtreeProbability())));
			parserFamily.incrementSufficientStatistics(gradient, parserParameters, example.getSentence(), bestPredictedParse, -1.0 * Math.abs(example.getWeight()));
			parserFamily.incrementSufficientStatistics(gradient, parserParameters, example.getSentence(), bestCorrectParse, 1.0 * Math.abs(example.getWeight()));

			double currentStepSize = decayStepSize ? (stepSize / Math.sqrt(iter + i+ 2)) : stepSize;
			parserParameters.multiply(1.0 - (currentStepSize * l2penalty));
			parserParameters.increment(gradient, currentStepSize);
			gradient.zeroOut();
		}

		return parserParameters;
	}

	public static List<CcgParse> filterParsesByLogicalForm(Expression2 observedLogicalForm,
			ExpressionComparator comparator, Iterable<CcgParse> parses, boolean returnCorrect) {
		List<CcgParse> correctParses = Lists.newArrayList();
		for (CcgParse parse : parses) {
			Expression2 predictedLogicalForm = parse.getLogicalForm();
			if (predictedLogicalForm != null && (comparator.equals(predictedLogicalForm, observedLogicalForm) == returnCorrect)) {
				correctParses.add(parse);
			} 
		}
		return correctParses;
	}
	
	public static SufficientStatistics trainOnGivenExamples(
		ParametricCcgParser parserFamily, 
		SufficientStatistics parserParameters, 
		List<WeightedCcgExample> ccgExamples,
		int numPasses,
		Boolean returnAveragedParameters){
		
		parserParameters = (parserParameters == null) ? parserFamily.getNewSufficientStatistics(): parserParameters;
		SufficientStatistics averagedParameters = null;
		long numIterations = numPasses * ccgExamples.size();
		if (returnAveragedParameters) {
			averagedParameters = parserFamily.getNewSufficientStatistics();
			averagedParameters.increment(parserParameters, 1.0);
		}
		
		for (int passIdx = 0; passIdx < numPasses; passIdx++) {
			System.out.println("Pass "+passIdx);
			List<WeightedCcgExample> shuffledExamples = new LinkedList<WeightedCcgExample>(ccgExamples);
			Collections.shuffle(shuffledExamples);
			
			parserParameters = PerceptronUtils.updateOnGivenExamples(parserFamily, parserParameters, shuffledExamples, passIdx*shuffledExamples.size());
			if (returnAveragedParameters) {
				averagedParameters.increment(parserParameters, 1.0 / numIterations);
			}
		}
		SufficientStatistics retVal = ((returnAveragedParameters) ? averagedParameters:parserParameters);
		return retVal;
	}
	
	public static SufficientStatistics train(ParametricCcgParser parametricCcgParser, List<WeightedCcgExample> trainingData,
			int numPasses, SufficientStatistics initialParameters) {
		
		GradientOracle<CcgParser, WeightedCcgExample> oracle = new WeightedCcgPerceptronOracle(parametricCcgParser, inferenceAlgorithm, comparator);
		initialParameters = (initialParameters==null)? oracle.initializeGradient() : initialParameters;
		int numIterations = numPasses * trainingData.size();
			
		/**/
		//Regularizer regularizer = new StochasticL2Regularizer(0.01, 1.0);
		Iterator<WeightedCcgExample> cycledTrainingData = Iterators.cycle(trainingData);
		//MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();
		
		//GradientEvaluation gradientAccumulator = null;

		for (long i = 0; i < numIterations; i++) {
			
			System.out.println("Iter"+i);
			List<WeightedCcgExample> batchData = getBatch(cycledTrainingData, 1);
			CcgParser currentModel = oracle.instantiateModel(initialParameters);
			
			//This section only serves to calculate the batch gradient in gradientAccumulator
			//GradientReducer<CcgParser, WeightedCcgExample> reducer = new GradientReducer<CcgParser, WeightedCcgExample>(currentModel, initialParameters, oracle, log);
			//gradientAccumulator = executor.mapReduce(batchData, Mappers.<WeightedCcgExample>identity(), reducer, gradientAccumulator);
			//SufficientStatistics gradient = gradientAccumulator.getGradient();
			
			/**/
			SufficientStatistics gradient = parametricCcgParser.getNewSufficientStatistics();
			WeightedCcgExample example = batchData.get(0);
			List<CcgParse> parses = inferenceAlgorithm.beamSearch(currentModel, example.getSentence(), null, log);

			if (parses.size() == 0) { //throw new ZeroProbabilityError();
				System.out.println("No parses for this sentence");
				System.exit(0);
				continue;
			}
			CcgParse bestPredictedParse = parses.get(0);

			List<CcgParse> correctParses = filterParsesByLogicalForm(example.getLogicalForm(), comparator, parses, example.getWeight() > 0);
			if (correctParses.size() == 0) {
				System.out.println("Search error (Correct): " + example.getSentence() + " " + example.getLogicalForm());
				System.out.println("predicted: " + bestPredictedParse.getLogicalForm());
				//throw new ZeroProbabilityError();
				continue;
			}
			CcgParse bestCorrectParse = correctParses.get(0);

			//System.out.println(Math.min(0.0, Math.log(bestPredictedParse.getSubtreeProbability()) - Math.log(bestCorrectParse.getSubtreeProbability())));
			parametricCcgParser.incrementSufficientStatistics(gradient, initialParameters, example.getSentence(), bestPredictedParse, -1.0 * Math.abs(example.getWeight()));
			parametricCcgParser.incrementSufficientStatistics(gradient, initialParameters, example.getSentence(), bestCorrectParse, 1.0 * Math.abs(example.getWeight()));

			/**/

			// Apply regularization and take a gradient step.
			double currentStepSize = stepSize / Math.sqrt(i + 2);
			initialParameters.multiply(1.0 - (currentStepSize * 0.01));
			initialParameters.increment(gradient, currentStepSize);

			//gradientAccumulator.zeroOut();
		}
		return initialParameters;
	/**/		

	}
	
	private static <S> List<S> getBatch(Iterator<S> trainingData, int batchSize) {
		List<S> batchData = Lists.newArrayListWithCapacity(batchSize);
		for (int i = 0; i < batchSize && trainingData.hasNext(); i++) {
			batchData.add(trainingData.next());
		}
		return batchData;
	}
	
	private PerceptronUtils(){
		/*Static class*/
	}
}

/*
 * 1) SequenceParserSettings.java:
 * this.parserParameters = ParsingUtils.train(family, ccgExamples, initialTraining, null);
 * 
 * 2) ParsingUtils.java
 * 
 * 	public static SufficientStatistics train(ParametricCcgParser parametricCcgParser, List<WeightedCcgExample> trainingExamples,
			int numPasses, SufficientStatistics initialParameters) {

		ExpressionSimplifier simplifier = new ExpressionSimplifier(Arrays.<ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(), new VariableCanonicalizationReplacementRule()));
		ExpressionComparator comparator = new SimplificationComparator(simplifier);

		int beamSize = 300;

		//1. Instantiate an inference algorithm for gradient computation for SP, and instantiate gradient oracle
		CcgBeamSearchInference inferenceAlgorithm = new CcgBeamSearchInference(null, comparator, beamSize, -1, Integer.MAX_VALUE, Runtime.getRuntime().availableProcessors(), false);
		GradientOracle<CcgParser, WeightedCcgExample> oracle = new WeightedCcgPerceptronOracle(parametricCcgParser, inferenceAlgorithm, comparator);

		boolean averagedParameters = true;
		if (initialParameters == null) {
			initialParameters = oracle.initializeGradient();
			averagedParameters = false;
		}

		int numIterations = numPasses * trainingExamples.size();

		//2. Instantiate a StochasticGradientTrainer to set parameters of optimization procedure
		//GradientOptimizer trainer = StochasticGradientTrainer.createWithL2Regularization(numIterations, 1, 1.0, true, averagedParameters, l2Regularization, new NullLogFunction());
		GradientOptimizer trainer = new PerceptronTrainer(numIterations, averagedParameters);

		//3. Run gradient updates to get learnt parameters
		SufficientStatistics parameters = trainer.train(oracle, initialParameters,trainingExamples);

		return parameters;
	}
 *
 * 3) PErceptronTrainer.java	
	public <M, E, T extends E> SufficientStatistics train(GradientOracle<M, E> oracle, SufficientStatistics initialParameters, Iterable<T> trainingData) {

		Iterator<T> cycledTrainingData = Iterators.cycle(trainingData);
		MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();

		SufficientStatistics averagedParameters = null;
		if (returnAveragedParameters) {
			averagedParameters = oracle.initializeGradient();
			averagedParameters.increment(initialParameters, 1.0);
		}

		SufficientStatistics gradientSumSquares = null;

		double gradientL2 = 0.0;
		GradientEvaluation gradientAccumulator = null;

		for (long i = 0; i < numIterations; i++) {

			List<T> batchData = getBatch(cycledTrainingData, 1);
			M currentModel = oracle.instantiateModel(initialParameters);

			int iterSearchErrors = 0;


			//This section only serves to calculate the batch gradient in gradientAccumulator
			//GradientReducer extends reducer and has fields instantiatedModel(M), modelParameters(SufficientStatistics) and oracle(CcgPerceptronOracle). The reducer calls:
			//oracle.accumulateGradient(gradientAccumulator.getGradient(), instantiatedModelParameters, instantiatedModel, item, log) to update the gradient in gradient accumulator by calling
			//family.incrementSufficientStatistics(gradient, currentParameters, example.getSentence(), bestPredictedParse, -1.0);
			GradientReducer<M, T> reducer = new GradientReducer<M, T>(currentModel, initialParameters, oracle, log);
			gradientAccumulator = executor.mapReduce(batchData, Mappers.<T>identity(), reducer, gradientAccumulator);		

			iterSearchErrors = gradientAccumulator.getSearchErrors();
			SufficientStatistics gradient = gradientAccumulator.getGradient();

			// Apply regularization and take a gradient step.
			double currentStepSize = decayStepSize ? (stepSize / Math.sqrt(i + 2)) : stepSize;
			regularizer.apply(gradient, initialParameters, gradientSumSquares, currentStepSize);

			// System.out.println(initialParameters);
			gradientL2 = gradient.getL2Norm();
			double objectiveValue = gradientAccumulator.getObjectiveValue();

			if (returnAveragedParameters) {
				averagedParameters.increment(initialParameters, 1.0 / numIterations);
			}

			gradientAccumulator.zeroOut();
		}

		if (returnAveragedParameters) {
			return averagedParameters;
		} else {
			return initialParameters;
		}
	}
 * */
