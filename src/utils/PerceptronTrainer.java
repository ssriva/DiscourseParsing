package utils;

import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.parallel.Mappers;
import com.jayantkrish.jklol.training.GradientEvaluation;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.GradientReducer;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer.Regularizer;
import com.jayantkrish.jklol.training.StochasticGradientTrainer.StochasticL2Regularizer;

public class PerceptronTrainer implements GradientOptimizer {

	private final long numIterations;
	private final LogFunction log = new NullLogFunction();
	private final double stepSize = 1.0;
	private final boolean decayStepSize = true;
	private final Regularizer regularizer = new StochasticL2Regularizer(0.01, 1.0);
	private final boolean returnAveragedParameters;

	public PerceptronTrainer(long numIterations, boolean returnAveragedParameters){
		this.numIterations = numIterations;
		this.returnAveragedParameters = returnAveragedParameters;
	}

	public <M, E, T extends E> SufficientStatistics train(GradientOracle<M, E> oracle,
			SufficientStatistics initialParameters, Iterable<T> trainingData) {

		Iterator<T> cycledTrainingData = Iterators.cycle(trainingData);
		MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();

		SufficientStatistics averagedParameters = null;
		if (returnAveragedParameters) {
			averagedParameters = oracle.initializeGradient();
			averagedParameters.increment(initialParameters, 1.0);
		}

		SufficientStatistics gradientSumSquares = null;

		GradientEvaluation gradientAccumulator = null;

		for (long i = 0; i < numIterations; i++) {
			
			System.out.println("Iter"+i);
			List<T> batchData = getBatch(cycledTrainingData, 1);
			M currentModel = oracle.instantiateModel(initialParameters);
			
			//This section only serves to calculate the batch gradient in gradientAccumulator
			//GradientReducer extends reducer and has fields instantiatedModel(M), modelParameters(SufficientStatistics) and oracle(CcgPerceptronOracle). The reducer calls:
			//oracle.accumulateGradient(gradientAccumulator.getGradient(), instantiatedModelParameters, instantiatedModel, item, log) to update the gradient in gradient accumulator by calling
			//family.incrementSufficientStatistics(gradient, currentParameters, example.getSentence(), bestPredictedParse, -1.0);
			GradientReducer<M, T> reducer = new GradientReducer<M, T>(currentModel, initialParameters, oracle, log);
			gradientAccumulator = executor.mapReduce(batchData, Mappers.<T>identity(), reducer, gradientAccumulator);
			SufficientStatistics gradient = gradientAccumulator.getGradient();

			// Apply regularization and take a gradient step.
			double currentStepSize = decayStepSize ? (stepSize / Math.sqrt(i + 2)) : stepSize;
			regularizer.apply(gradient, initialParameters, gradientSumSquares, currentStepSize);

			if (returnAveragedParameters) {
				averagedParameters.increment(initialParameters, 1.0 / numIterations);
			}

			gradientAccumulator.zeroOut();
		}

		if (returnAveragedParameters) {
			System.out.println("Returning averaged parameters");
			return averagedParameters;
		} else {
			System.out.println("Returning final parameters");
			return initialParameters;
		}
	}

	private <S> List<S> getBatch(Iterator<S> trainingData, int batchSize) {
		List<S> batchData = Lists.newArrayListWithCapacity(batchSize);
		for (int i = 0; i < batchSize && trainingData.hasNext(); i++) {
			batchData.add(trainingData.next());
		}
		return batchData;
	}
}
