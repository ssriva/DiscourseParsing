package testing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import instructable.server.ccg.CcgUtils;
import utils.ParsingUtils;

import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;




public class TestRoteLearner {
		
	public static void main(String[] args) throws IOException{
		
		String trainingDirectory = args[0]; //"/Users/shashans/Work/DialogueData/dialogues/tabseparated/train/";
		String testDirectory = args[1]; //"/Users/shashans/Work/DialogueData/dialogues/tabseparated/testexamples.csv";
	    System.out.println("Training directory: "+trainingDirectory);
	    System.out.println("Test directory: "+testDirectory);
		
		HashMap<String,String> roteMap = new HashMap<String, String>();
	    
		//Training directory
		for(Path path:Files.list(new File(trainingDirectory).toPath()).filter(path -> path.getFileName().toString().endsWith(".csv")).collect(Collectors.toList())){
		    List<String[]> exampleStrings = CcgUtils.loadExamples(path);	    

		    for (String[] exampleString : exampleStrings) {	      
		      Expression2 expression = ExpressionParser.expression2().parseSingleExpression(exampleString[1]);
		      String str = ParsingUtils.simplifier.apply(expression).toString();
		      if(!roteMap.containsKey(exampleString[0])){
		    	  roteMap.put(exampleString[0], str);
		      }
		    }
		}

		//Test directory
		int correct = 0, incorrect = 0;
		for(Path path:Files.list(new File(trainingDirectory).toPath()).filter(path -> path.getFileName().toString().endsWith(".csv")).collect(Collectors.toList())){
		    List<String[]> exampleStrings = CcgUtils.loadExamples(path);	    

		    for (String[] exampleString : exampleStrings) {	      
		      Expression2 expression = ExpressionParser.expression2().parseSingleExpression(exampleString[1]);
		      String str = ParsingUtils.simplifier.apply(expression).toString();
		      if(roteMap.containsKey(exampleString[0]) && roteMap.get(exampleString[0]).equals(str) ){
		    	  correct++;
		      }else{
		    	  incorrect++;
		      }
		    }
		}
		
		double accuracy = (1.0 * correct)/(correct+incorrect);
		System.out.println("Accuracy on test directory: "+accuracy);


	}	

}
