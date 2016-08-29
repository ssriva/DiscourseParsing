package testing;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import instructable.server.ccg.CcgUtils;

import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;

import discourseparser.DiscourseParser;
import discourseparser.DiscourseParsingUtils;

public class TestStructuredCcgParser {

	public static ExpressionSimplifier simplifier = CcgUtils.getExpressionSimplifier();
	public static ExpressionComparator comparator = new SimplificationComparator(simplifier);

	public static void main(String[] args) {

		String trainingDirectory = (args.length>0) ? args[0]:"/Users/shashans/Work/DialogueData/dialogues/tabseparated/train/"; // "data/"
		int numIters = (args.length>1 && edu.stanford.nlp.util.StringUtils.isNumeric(args[1])) ? Integer.parseInt(args[1]) : 10;
		int beamSize = (args.length>2 && edu.stanford.nlp.util.StringUtils.isNumeric(args[2])) ? Integer.parseInt(args[2]) : 200;
		int maxLogicalForms =   (args.length>3 && edu.stanford.nlp.util.StringUtils.isNumeric(args[3])) ? Integer.parseInt(args[3]) : 15;
		int numStates =(args.length>4 && edu.stanford.nlp.util.StringUtils.isNumeric(args[4])) ? Integer.parseInt(args[4]) : 5;
		System.out.println("Training Directory:"+trainingDirectory);
		System.out.println("numIters:\t"+numIters+"\nbeamSize:\t"+beamSize+"\nmaxLog:\t"+maxLogicalForms+"\nnumStates:\t"+numStates+"\n\n");

		//Initialize discourse parser
		boolean pretrain = false;
		DiscourseParser discourseParser = new DiscourseParser("data/lexiconEntries.txt", "data/lexiconSyn.txt", trainingDirectory, numStates, pretrain);

		//Train discourse Parser
		discourseParser.trainWeights(numIters, beamSize, maxLogicalForms);
		
		//Evaluate discourse parser
		//String testDirectory = "/Users/shashans/Work/DialogueData/dialogues/tabseparated/train/";
		//DiscourseParsingUtils.testDiscourseParser(discourseParser.getNewCcgSequences(testDirectory), discourseParser, beamSize, maxLogicalForms, simplifier, comparator);
		DiscourseParsingUtils.testDiscourseParser(discourseParser.ccgExamples, discourseParser, beamSize, maxLogicalForms, simplifier, comparator);

		//Save model
		System.exit(0);
		String modelName=trainingDirectory+"model_"+numIters+"_"+beamSize+"_"+maxLogicalForms+"_"+numStates+"_"+pretrain;
		saveToFile(discourseParser.parserParameters, modelName+".parserParams");
		saveToFile(discourseParser.weights, modelName+".weights");
		
		/*Simple parser evaluation*/
		//CcgInference inferenceAlgorithm = new CcgBeamSearchInference(null, comparator, 100, -1, Integer.MAX_VALUE, Runtime.getRuntime().availableProcessors(), false);	    
		//System.out.println("training error");
		//List<WeightedCcgExample> flatExamples = dsParser.ccgExamples.stream().flatMap(l -> l.stream()).collect(Collectors.toList());
		//SemanticParserUtils.testSemanticParser(WeightedCcgExample.toCcgExamples(flatExamples), dsParser.parser, inferenceAlgorithm, simplifier, comparator);
		/**/    
	}

	public static void saveToFile(Object obj, String fileName){
		try{
			ObjectOutput output = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(fileName)));
			output.writeObject(obj);
			output.close();
		}catch(IOException e){
			e.printStackTrace();
		}
	}

	public static Object loadFromFile(String fileName){
		Object obj = null;
		try{
			ObjectInput input = new ObjectInputStream(new BufferedInputStream(new FileInputStream(fileName)));
			obj = input.readObject();
			input.close();
		}catch(Exception e){
			e.printStackTrace();
		}
		return obj;
	}
}