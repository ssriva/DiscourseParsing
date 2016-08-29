package testing;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import instructable.server.ccg.CcgUtils;
import instructable.server.ccg.WeightedCcgExample;
import parsing.SimpleParserSettings;
import utils.ParsingUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgBeamSearchInference;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils;



public class TestSimpleCcgParser {
		
	public static void main(String[] args){
		//SimpleParserSettings simpleParserSettings = ParsingUtils.createParser("data/lexiconEntries.txt", "data/lexiconSyn.txt", "data/", true);
		
		String trainingDirectory = "/Users/shashans/Work/DialogueData/dialogues/tabseparated/train/";
		String testFile = "/Users/shashans/Work/DialogueData/dialogues/tabseparated/testexamples.csv";
		SimpleParserSettings simpleParserSettings = ParsingUtils.createParser("data/lexiconEntries.txt", "data/lexiconSyn.txt", trainingDirectory, true);
	    
		//Decoder.getGoldAndCandidateParsesForSequence(simpleParserSettings.parser, simpleParserSettings.ccgExamples, 200, 15);
		
		/*Parser evaluation*/
	    ExpressionSimplifier simplifier = CcgUtils.getExpressionSimplifier();
	    ExpressionComparator comparator = new SimplificationComparator(simplifier);
	    CcgInference inferenceAlgorithm = new CcgBeamSearchInference(null, comparator, 100, -1, Integer.MAX_VALUE, Runtime.getRuntime().availableProcessors(), false);	    
	    //System.out.println("Training error");
	    //SemanticParserUtils.testSemanticParser(WeightedCcgExample.toCcgExamples(simpleParserSettings.ccgExamples), simpleParserSettings.parser, inferenceAlgorithm, simplifier, comparator);

	    System.out.println("Test error");
	    List<String[]> exampleStrings = CcgUtils.loadExamples(Paths.get(testFile));	    
	    List<WeightedCcgExample> testExamples = Lists.newArrayList();
	    for (String[] exampleString : exampleStrings) {	      
	      Expression2 expression = ExpressionParser.expression2().parseSingleExpression(exampleString[1]);
	      WeightedCcgExample example = CcgUtils.createCcgExample(exampleString[0], expression, simpleParserSettings.posUsed, false, simpleParserSettings.featureVectorGenerator);
	      testExamples.add(example);
	    }
	    SemanticParserUtils.testSemanticParser(WeightedCcgExample.toCcgExamples(testExamples), simpleParserSettings.parser, inferenceAlgorithm, simplifier, comparator);
	    
	    
		//AnnotatedSentence annotatedSent = ParsingUtils.getAnnotatedSentence("create email and set subject to hello", simpleParserSettings);
		//List<CcgParse> parses = simpleParserSettings.parser.beamSearch(annotatedSent, 200);	
		//System.out.println(parses.size()+"\n"+simplifier.apply(parses.get(0).getLogicalForm()));
		
	}	

}
