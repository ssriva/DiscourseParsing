package testing;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;

import instructable.server.ccg.CcgUtils;
import utils.ParsingUtils;

import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;




public class TestRoteLearner {
		
	public static void main(String[] args){
		
		String trainingDirectory = "/Users/shashans/Work/DialogueData/dialogues/tabseparated/train/";
		String testFile = "/Users/shashans/Work/DialogueData/dialogues/tabseparated/testexamples.csv";
	    
		HashMap<String,String> roteMap = new HashMap<String, String>();
	    
	    List<String[]> exampleStrings = CcgUtils.loadExamples(Paths.get(testFile));	    
	    for (String[] exampleString : exampleStrings) {	      
	      Expression2 expression = ExpressionParser.expression2().parseSingleExpression(exampleString[1]);
	      String str = ParsingUtils.simplifier.apply(expression).toString();
	      roteMap.put(exampleString[0], str);
	    }

	}	

}
