package utils;

import instructable.server.ccg.CcgUtils;
import instructable.server.ccg.StringFeatureGenerator;
import instructable.server.ccg.WeightedCcgExample;
import instructable.server.ccg.WeightedCcgPerceptronOracle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import parsing.SimpleParserSettings;

import com.jayantkrish.jklol.ccg.CcgBeamSearchInference;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.ccg.lexicon.SpanFeatureAnnotation;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.GradientOracle;

public class ParsingUtils {

	public static ExpressionSimplifier simplifier = CcgUtils.getExpressionSimplifier();
    public static ExpressionComparator comparator = new SimplificationComparator(simplifier);
    
    public static String simplify(CcgParse parse){
    	return simplifier.apply(parse.getLogicalForm()).toString();
    }
    
	public static String simplify(WeightedCcgExample ccgExample) {
		return simplifier.apply(ccgExample.getLogicalForm()).toString();
	}

	public static AnnotatedSentence getAnnotatedSentence(String sentence, SimpleParserSettings parserSettings){
		List<String> tokens = new LinkedList<>();
		List<String> poss = new LinkedList<>();
		tokens.add(CcgUtils.startSymbol);
		poss.add(CcgUtils.START_POS_TAG);
		CcgUtils.tokenizeAndPOS(sentence, tokens, poss, false, parserSettings.posUsed);
		AnnotatedSentence supertaggedSentence = new AnnotatedSentence(tokens, poss);
		SpanFeatureAnnotation annotation = SpanFeatureAnnotation.annotate(supertaggedSentence, parserSettings.featureVectorGenerator);
		supertaggedSentence = supertaggedSentence.addAnnotation(CcgUtils.STRING_FEATURE_ANNOTATION_NAME, annotation);
		return supertaggedSentence;
	}

	//This method is a modification of instructable.server.dal.CreateParserFromFiles.createParser(Optional<String> userId, String lexicon, String lexiconSyn, String trainingExamples)
	public static SimpleParserSettings createParser(String lexicon, String lexiconSyn, String trainingExamples, Boolean trainModel)
	{

		List<String> lexiconEntries = null;
		List<String> synonyms = null;
		try
		{
			lexiconEntries = Files.readAllLines(Paths.get(lexicon));
			synonyms = Files.readAllLines(Paths.get(lexiconSyn));
		} catch (IOException e)
		{
			e.printStackTrace();
		}

		String[] unaryRules = new String[]{
				"Field{0} FieldVal{0},(lambda x (evalField x))",
				"FieldVal{0} S{0},(lambda x x)",
				"FieldName{0} Field{0},(lambda x (getProbFieldByFieldName x))",
				"FieldName{0} FieldVal{0},(lambda x (evalField (getProbFieldByFieldName x)))", //this one just combines the two above (and below).
				//"MutableField{0} FieldVal{0},(lambda x (evalField x))", //no need to evaluate a mutable field, if needs mutable, why will it try to evaluate?
				"FieldName{0} MutableField{0},(lambda x (getProbMutableFieldByFieldName x))",
				"Field{0} S{0},(lambda x (evalField x))",
				"MutableField{0} S{0},(lambda x (evalField x))",
				"InstanceName{0} Instance{0},(lambda x (getProbInstanceByName x))",
				"ConceptName{0} ExactInstance{0}/InstanceName{0}, (lambda x (lambda y (getInstance x y)))",
				"InstanceName{0} MutableField{0}/FieldName{0}, (lambda x y (getProbMutableFieldByInstanceNameAndFieldName x y))",
				"InstanceName{0} Field{0}/FieldName{0}, (lambda x (lambda y (getProbFieldByInstanceNameAndFieldName x y)))",
				"EmailAmbiguous{0} InstanceName{0}, (lambda x x)", //only if the parser doesn't manage to identify which kind of email the user means, will this be used.
				"EmailAmbiguous{0} Instance{0},(lambda x (getProbInstanceByName x))",
				//"StringV{0} Num{0}, (lambda x ( string2num x))", //SSRIV
				"Num{0} S{0}, (lambda x x)",//SSRIV: type-raise a number to S{0}
				"S{0} Exp{0}, (lambda x x)",//SSRIV: type-lower any S{0} to an expression
				"StringV{0} Exp{0}, (lambda x x)",//SSRIV: type-raise any string to an expression
				"Num{0} Num{1}/Num{2}, (lambda x y  (sumList (createNumListNew x y)) )",
				"ExactInstance{0} Instance{0},(lambda x x)" //exact instance is also an instance (that can be read).
				//"PP_StringV{1} (S{0}\\(S{0}/PP_StringV{1}){0}){0}, (lambda x x)", //these two are for handling sentences like: "set body to hello and subject to see you"
				//"MutableField{1} ((S{0}/PP_StringV{2}){0}\\(S{0}/PP_StringV{2}){0}/MutableField{1}){0}){0}, (lambda x y x y)"
		};

		return new SimpleParserSettings(lexiconEntries, synonyms, new LinkedList<>(), unaryRules, new StringFeatureGenerator(), ParsingUtils.loadExampleSequences(trainingExamples), trainModel);
	}

	//Modifies CcgUtils.loadExamples(Paths.get(trainingExamples))
	public static List<List<String[]>> loadExampleSequences(String trainingExamples) {
		List<List<String[]>> retVal = new LinkedList<List<String[]>>();
		try {
			System.out.println("Loading sequences from directory "+Files.list(new File(trainingExamples).toPath()).filter(path -> path.getFileName().toString().endsWith(".csv")).collect(Collectors.toList()));
			Files.list(new File(trainingExamples).toPath())
			.filter(path -> path.getFileName().toString().endsWith(".csv"))
			.forEach(path -> retVal.add(CcgUtils.loadExamples(path)));
			System.out.println("Processing files: "+Files.list(new File(trainingExamples).toPath()).filter(path -> path.getFileName().toString().endsWith(".csv")).collect(Collectors.toList()).toString());
		} catch (IOException e) {e.printStackTrace();}
		return retVal;
	}
	
	//From CcgUtils.train
	public static SufficientStatistics train(ParametricCcgParser parametricCcgParser, List<WeightedCcgExample> trainingExamples,
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
		
		/*
		System.out.println("-------------------------------------------------------------------------");
		System.out.println(parametricCcgParser.getParameterDescription(parameters));
		parameters.multiply(0);
		*/
		return parameters;
	}

	private ParsingUtils(){
		//static class
	}


}
