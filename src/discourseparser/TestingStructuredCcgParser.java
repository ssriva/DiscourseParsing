package discourseparser;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import instructable.server.ccg.CcgUtils;
import instructable.server.ccg.WeightedCcgExample;

import com.jayantkrish.jklol.ccg.CcgBeamSearchInference;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils;

public class TestingStructuredCcgParser {

	public static ExpressionSimplifier simplifier = CcgUtils.getExpressionSimplifier();
	public static ExpressionComparator comparator = new SimplificationComparator(simplifier);

	public static void main(String[] args) {

		String trainingDirectory = (args.length>0) ? args[0]:"/Users/shashans/Work/DialogueData/dialogues/tabseparated/train/"; // "data/"
		int numIters = (args.length>1 && edu.stanford.nlp.util.StringUtils.isNumeric(args[1])) ? Integer.parseInt(args[1]) : 10;
		int beamSize = (args.length>2 && edu.stanford.nlp.util.StringUtils.isNumeric(args[2])) ? Integer.parseInt(args[2]) : 200;
		int maxLogicalForms =   (args.length>3 && edu.stanford.nlp.util.StringUtils.isNumeric(args[3])) ? Integer.parseInt(args[3]) : 15;
		int numStates =(args.length>4 && edu.stanford.nlp.util.StringUtils.isNumeric(args[4])) ? Integer.parseInt(args[4]) : 5;
		String testDirectory = (args.length>5) ? args[5]: "/Users/shashans/Work/DialogueData/dialogues/tabseparated/test/";
		
		System.out.println("Training Directory:"+trainingDirectory);
		System.out.println("Testing Directory:"+testDirectory);
		System.out.println("numIters:\t"+numIters+"\nbeamSize:\t"+beamSize+"\nmaxLog:\t"+maxLogicalForms+"\nnumStates:\t"+numStates+"\n\n");
		
		
		//Initialize discourse parser
		boolean pretrain = false;
		DiscourseParser discourseParser = new DiscourseParser("data/lexiconEntries.txt", "data/lexiconSyn.txt", trainingDirectory, numStates, pretrain);
		
		/*
		System.out.println("simple evaluation");
		CcgInference inferenceAlgorithm = new CcgBeamSearchInference(null, comparator, 100, -1, Integer.MAX_VALUE, Runtime.getRuntime().availableProcessors(), false);	    
		List<WeightedCcgExample> flatExamples = discourseParser.ccgExamples.stream().flatMap(l -> l.stream()).collect(Collectors.toList());
		SemanticParserUtils.testSemanticParser(WeightedCcgExample.toCcgExamples(flatExamples), discourseParser.parser, inferenceAlgorithm, simplifier, comparator);
		System.exit(0);
		*/
		
		//Calculate total recall
		/*
		System.out.println("Testing total recall!");
		CcgInference inferenceAlgorithm = new CcgBeamSearchInference(null, comparator, 100, -1, Integer.MAX_VALUE, Runtime.getRuntime().availableProcessors(), false);
		List<WeightedCcgExample> flatExamples = discourseParser.ccgExamples.stream().flatMap(l -> l.stream()).collect(Collectors.toList());
		DiscourseParsingUtils.testTotalRecall(WeightedCcgExample.toCcgExamples(flatExamples), discourseParser.parser, inferenceAlgorithm, simplifier, comparator);
		System.exit(0);
		*/
		
		//Train discourse Parser
		System.out.println("Training weights...");
		discourseParser.trainWeights(numIters, beamSize, maxLogicalForms);
		
		//Evaluate discourse parser
		DiscourseParsingUtils.testDiscourseParser(discourseParser.ccgExamples, discourseParser, beamSize, maxLogicalForms, simplifier, comparator);
		DiscourseParsingUtils.testDiscourseParser(discourseParser.getNewCcgSequences(testDirectory), discourseParser, beamSize, maxLogicalForms, simplifier, comparator);
		//System.exit(0);
		
		//Save model	
		String modelName=trainingDirectory+"model_"+numIters+"_"+beamSize+"_"+maxLogicalForms+"_"+numStates+"_"+pretrain;
		saveToFile(discourseParser.parserParameters, modelName+".parserParams");
		writeWeights(discourseParser.weights, modelName+".weights");
		  
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
	
	public static void writeWeights(HashMap<String,Double> weights, String outFile){
		try {
			PrintWriter writer = new PrintWriter(outFile);
			for(Entry<String,Double> e:weights.entrySet()){
				writer.println(e.getKey()+"\t"+e.getValue());
			}
			writer.close();
		} catch (FileNotFoundException e) {
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