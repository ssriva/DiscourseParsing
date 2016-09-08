package discourseparser;

import instructable.server.ccg.WeightedCcgExample;

import java.util.List;

import utils.CcgParseWrapper;
import utils.ParsingUtils;
import utils.PerceptronUtils;

import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplificationException;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;

public class DiscourseParsingUtils {
	
	public static void testDiscourseParser(List<List<WeightedCcgExample>> testExampleSequences, DiscourseParser discourseParser, 
			int beamSize, int maxLog,
			ExpressionSimplifier simplifier, ExpressionComparator comparator) {
		int numCorrect = 0;
		int numParsed = 0;
		int counter = 0;
		
		int numCorrectDerived = 0 , numCorrectUnderived = 0, numDerived = 0, numUnderived =0;

		for (List<WeightedCcgExample> exampleSequence : testExampleSequences) {

			List<CcgParseWrapper> bestPredictedSequenceParse = Decoder.decode(exampleSequence, discourseParser, beamSize, maxLog, false).parses;
			//CcgParse parse = inferenceAlg.getBestParse(parser, exampleSequence.getSentence(), null, log);

			for(int i=0; i<exampleSequence.size();i++){
				
				counter++;
				WeightedCcgExample example = exampleSequence.get(i);
				CcgParseWrapper parse = bestPredictedSequenceParse.get(i);
				
				Boolean couldDerive = (PerceptronUtils.filterParsesByLogicalForm(example.getLogicalForm(), ParsingUtils.comparator, discourseParser.parser.beamSearch(example.getSentence(), 200), true).size() > 0);
				
				if(Decoder.verbose) System.out.println("====");
				if(Decoder.verbose) System.out.println("SENT: " + example.getSentence().getWords());
				if (parse != null) {
					int correct = 0; 
					String lf = null;									//Expression2 lf = null;
					String correctLf = ParsingUtils.simplify(example);	//Expression2 correctLf = simplifier.apply(example.getLogicalForm()); 

					try {
						lf = parse.getStringLogicalForm();				//lf = simplifier.apply(parse.getLogicalForm());
						correct = lf.equals(correctLf) ? 1 : 0;			//correct = comparator.equals(lf, correctLf) ? 1 : 0;						
					} catch (ExpressionSimplificationException e) {
						// Make lf print out as null.
						lf = null;										//lf = Expression2.constant("null");
					}

					if(Decoder.verbose) System.out.println("PREDICTED: " + lf);
					if(Decoder.verbose) System.out.println("TRUE:      " + correctLf);
					//System.out.println("DEPS: " + parse.getAllDependencies());
					if(Decoder.verbose) System.out.println("CORRECT: " + correct);
					if(Decoder.verbose) System.out.println("IS_TRUE: " + parse.isTrueParse());

					numCorrect += correct;
					numParsed++;
					
					if(couldDerive){
						if(correct==1){
							numCorrectDerived++;
						}
						numDerived++;
					}else{
						if(correct==1){
							numCorrectUnderived++;
						}
						numUnderived++;
					}
					
				} else {
					System.out.println("NO PARSE");
				}
			}
		}

		double precision = ((double) numCorrect) / numParsed;
		double recall = ((double) numCorrect) / counter;
		System.out.println("\nPrecision: " + precision);
		System.out.println("Recall: " + recall);
		System.out.println("Accuracy(derived): "+((double) numCorrectDerived) / numDerived);
		System.out.println("Accuracy(underived): "+((double) numCorrectUnderived) / numUnderived);

		return;
	}
	

	public static void testSemanticParser(List<CcgExample> testExamples, CcgParser parser,
			CcgInference inferenceAlg, ExpressionSimplifier simplifier, ExpressionComparator comparator) {
		int numCorrect = 0;
		int numParsed = 0;

		LogFunction log = new NullLogFunction();
		for (CcgExample example : testExamples) {
			CcgParse parse = inferenceAlg.getBestParse(parser, example.getSentence(), null, log);
			System.out.println("====");
			System.out.println("SENT: " + example.getSentence().getWords());
			if (parse != null) {
				int correct = 0; 
				Expression2 lf = null;
				Expression2 correctLf = simplifier.apply(example.getLogicalForm()); 

				try {
					lf = simplifier.apply(parse.getLogicalForm());
					correct = comparator.equals(lf, correctLf) ? 1 : 0;
				} catch (ExpressionSimplificationException e) {
					// Make lf print out as null.
					lf = Expression2.constant("null");
				}

				System.out.println("PREDICTED: " + lf);
				System.out.println("TRUE:      " + correctLf);
				System.out.println("DEPS: " + parse.getAllDependencies());
				System.out.println("CORRECT: " + correct);

				numCorrect += correct;
				numParsed++;
			} else {
				System.out.println("NO PARSE");
			}
		}

		double precision = ((double) numCorrect) / numParsed;
		double recall = ((double) numCorrect) / testExamples.size();
		System.out.println("\nPrecision: " + precision);
		System.out.println("Recall: " + recall);

		return;
	}
	
	public static void testTotalRecall(List<CcgExample> testExamples, CcgParser parser,
			CcgInference inferenceAlg, ExpressionSimplifier simplifier, ExpressionComparator comparator) {
		int numCorrect = 0;
		int numParsed = 0;

		LogFunction log = new NullLogFunction();
		for (CcgExample example : testExamples) {			
			List<CcgParse> parses = parser.beamSearch(example.getSentence(), 200);
			List<CcgParse> correctParses = PerceptronUtils.filterParsesByLogicalForm(example.getLogicalForm(), ParsingUtils.comparator, parses, true);
			int correct = (correctParses.size() > 0) ? 1:0;
			numCorrect += correct;
			numParsed++;
		}

		double precision = ((double) numCorrect) / numParsed;
		double recall = ((double) numCorrect) / testExamples.size();
		System.out.println("\nPrecision: " + precision);
		System.out.println("Recall: " + recall);
		return;
	}
}
