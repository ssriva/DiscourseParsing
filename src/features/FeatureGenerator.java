package features;

import instructable.server.ccg.WeightedCcgExample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import discourseparser.Decoder;
import utils.CcgParseWrapper;


public class FeatureGenerator {

	public static List<String> logicalVocab; 
	public static HashMap<String,Set<String>> invokedLogicalPredicates = new HashMap<String, Set<String>>();
	static{
		try {
			logicalVocab = Files.readAllLines(Paths.get("data/actions.txt")).stream().map(s -> s.trim()).filter(s -> s.length()>0).collect(Collectors.toList());
			for(String line:Files.readAllLines(Paths.get("data/allAssociationRules.txt"))){
				String[] toks = line.split("\t");
				String word = toks[0].trim();
				invokedLogicalPredicates.put(word, new HashSet<String>(Arrays.asList(toks[1].split(" "))));
			}
		} catch (IOException e) {e.printStackTrace();}
	}
	
	public static List<String> generate( List<WeightedCcgExample> sequence, 		//sequence has length N
										 int index,									//varies from 1 to N+1
										 String state, String curLogicalForm,
										 String prevstate, String prevLogicalForm,
										 boolean isTrue){
		
		ArrayList<String> featureVec = new ArrayList<String>();
		
		
		if(index!=sequence.size()+1){
			String sentence  = String.join(" ", sequence.get(index-1).getSentence().getWords());
			for(String phrase:invokedLogicalPredicates.keySet()){
				if(sentence.contains(phrase)){
					Set<String> intersection = new HashSet<String>(invokedLogicalPredicates.get(phrase));
					intersection.retainAll(logicalVocab.stream().filter(s -> Arrays.asList(curLogicalForm.split("[()\\s]+")).contains(s)).collect(Collectors.toSet()));
					for(String matchedLogicalPredicate:intersection){
						featureVec.add(genKey("Lex_Wi",phrase,"Li",matchedLogicalPredicate));
						//System.out.println("ADDING "+genKey("Lex_Wi",phrase,"Li",matchedLogicalPredicate));
					}
				}
			}
		}
		
		/*
		//Add transition features:
		featureVec.add(genKey("Zi",state,"Zi-1",prevstate));
		
		//Add emission features for single logical lexemes 
		for(String matchedPred : logicalVocab.stream().filter(s -> Arrays.asList(curLogicalForm.split("[()\\s]+")).contains(s)).collect(Collectors.toSet())){
			featureVec.add(genKey("Zi",state,"Li",matchedPred));
		}
		if(index>0){
			for(String matchedPred : logicalVocab.stream().filter(s -> Arrays.asList(prevLogicalForm.split("[()\\s]+")).contains(s)).collect(Collectors.toSet())){
				featureVec.add(genKey("Zi",state,"Li-1",matchedPred));
			}
		}else{
			System.err.println("Shouldn't be here");
			System.exit(-1);
		}
		
		//Lexical matching
		if(index!=sequence.size()+1){
			String sentence  = String.join(" ", sequence.get(index-1).getSentence().getWords());
			for(String phrase:invokedLogicalPredicates.keySet()){
				if(sentence.contains(phrase)){
					Set<String> intersection = new HashSet<String>(invokedLogicalPredicates.get(phrase));
					intersection.retainAll(logicalVocab.stream().filter(s -> Arrays.asList(curLogicalForm.split("[()\\s]+")).contains(s)).collect(Collectors.toSet()));
					for(String matchedLogicalPredicate:intersection){
						featureVec.add(genKey("Lex_Wi",phrase,"Li",matchedLogicalPredicate));
						//System.out.println("ADDING "+genKey("Lex_Wi",phrase,"Li",matchedLogicalPredicate));
					}
				}
			}
		}
		
		//Add emission features for complete logical forms
		featureVec.add(genKey("Zi",state,"Li",curLogicalForm));
		featureVec.add(genKey("Zi",state,"Li-1",prevLogicalForm));
		featureVec.add(genKey("Li-1",prevLogicalForm,"Li", curLogicalForm));
		featureVec.add(genKey("Zi",state,"Li-1",prevLogicalForm,"Li", curLogicalForm));
		
		
		//Add features based on pairs of lexemes
		
		//Add Features based on most common commands
		
		//Features based on Si-1
		
		//Features based on whether the current parse is an assigned parse.
		if(index != sequence.size()+1){
			if(!isTrue){
				int len = (sequence.size()/7)*7;
				featureVec.add(genKey("assignedParse",1));
				featureVec.add(genKey("assignedParseLen",len));
			}
		}
		
		//Add features based on whether an utterance is inside a procedure
		int insideProcedure=0, beginIdx = -1;
		for(int i=Math.max(0,index-10); i<index-1; i++){
			if(sequence.get(i).getSentence().getWords().get(1).equals("yes")){ insideProcedure = 1; beginIdx = i;}
		}
		
		if(insideProcedure==1){
			for(int i=beginIdx+1; i<index-1;i++){
				if(sequence.get(i).getSentence().getWords().get(1).equals("end") || sequence.get(i).getSentence().getWords().get(1).equals("cancel")){
					insideProcedure=0;
				}
			}
		}
		
		featureVec.add(genKey("Zi",state,"InProcedure",insideProcedure));
		featureVec.add(genKey("Zi",state,"Li", curLogicalForm,"InProcedure",insideProcedure));
		featureVec.add(genKey("Li",curLogicalForm,"InProcedure",insideProcedure));
		featureVec.add(genKey("Li",curLogicalForm,"Li-1", prevLogicalForm,"InProcedure",insideProcedure));
		*/
		return featureVec;
	}
	
	private static String genKey(Object... args){
		String key= args[0]+"="+String.valueOf(args[1]);
		for(int i=2;i<args.length-1;i+=2){
			key=key+":"+args[i]+"="+String.valueOf(args[i+1]);
		}
		return key;
	}
	
	//Compute a global bag of features over an entire sequence
	public static List<String> getFeatureList(List<WeightedCcgExample> sequence, List<CcgParseWrapper> parses, List<String> path) {

		int N = sequence.size();
		String lastLogicalForm = (parses.get(N-1)==null) ? "": parses.get(N-1).getStringLogicalForm();	//ParsingUtils.simplify(parses.get(N-1));
		
		List<String> featureVec = new ArrayList<String>(generate(sequence, N+1, "<STOP>", "<STOP>", path.get(N-1), lastLogicalForm, false));
		for(int i=sequence.size(); i>=2; i--){
			String currLogicalForm = (parses.get(i-1)==null) ? "": parses.get(i-1).getStringLogicalForm();	//ParsingUtils.simplify(parses.get(i-1));
			String prevLogicalForm = (parses.get(i-2)==null) ? "": parses.get(i-2).getStringLogicalForm();	//ParsingUtils.simplify(parses.get(i-2));
			featureVec.addAll(generate(sequence, i, 
					path.get(i-1), currLogicalForm, 
					path.get(i-2), prevLogicalForm,
					parses.get(i-1).isTrueParse())
					);
		}
		
		String firstLogicalForm = (parses.get(0)==null) ? "": parses.get(0).getStringLogicalForm();	//ParsingUtils.simplify(parses.get(0));
		featureVec.addAll(generate(sequence, 1, path.get(0), firstLogicalForm, "<START>", "<START>", parses.get(0).isTrueParse()));
		return featureVec;
	}

	//Convert global bag of features into a vector with feature counts
	public static HashMap<String, Double> getFeatureMap( List<WeightedCcgExample> sequence, List<CcgParseWrapper> parses, List<String> path) {
		HashMap<String, Double> hmap = new HashMap<String, Double>();
		
		List<String> featureList = getFeatureList(sequence, parses, path);
		for(int i=0;i<featureList.size();i++){
			if(!hmap.containsKey(featureList.get(i))){
				hmap.put(featureList.get(i), 1.0);
			}else{
				hmap.put(featureList.get(i), hmap.get(featureList.get(i))+1.0);
			}
		}
		return hmap;
	}
	
	static HashMap<String,Double> cache = new HashMap<String, Double>();
	
	public static double getLogicalFormsOverlap(String candidateLogicalForm, String sentence){	
		
		String key = candidateLogicalForm+"|"+sentence;
		if(cache.containsKey(key)){
			if(Decoder.verbose) System.out.println("Retrieving from cache!!");
			return cache.get(key);
		}
		
		int count = 0, matchedpred = 0;		
		Set<String> observedLogicalPredicates = logicalVocab.stream().filter(s -> Arrays.asList(candidateLogicalForm.split("[()\\s]+")).contains(s)).collect(Collectors.toSet());
		Set<String> firingPredicates = new HashSet<String>();
		
		//Of matching phrases in sentence, how many match with actual logical form (precision)
		for(String phrase:invokedLogicalPredicates.keySet()){
			if(sentence.contains(phrase)){
				firingPredicates.addAll(invokedLogicalPredicates.get(phrase));
				
				Set<String> intersection = new HashSet<String>(invokedLogicalPredicates.get(phrase));
				intersection.retainAll(observedLogicalPredicates);
				matchedpred += ((intersection.size() > 0 ) ? 1:0);
				count++;
			}
		}
		double precision = (count>0) ? (1.0*matchedpred)/count : 0.0;
		
		//Of all predicates in logical form, how many could be matched with words in sentence (recall)
		Set<String> intersection = new HashSet<String>(observedLogicalPredicates);
		intersection.retainAll(firingPredicates);
		double recall = (1.0 * intersection.size()/observedLogicalPredicates.size());
		
		double fm = (precision+recall > 0.0) ? 2*precision*recall/(precision+recall):0;
		if(Decoder.verbose) System.out.println("Match of sentence: "+sentence+" with logical form: "+candidateLogicalForm+" is: "+count+ " p: "+precision+" r: "+recall+" fmeasure:"+fm);
		
		String[] entities = {"alex",
				"charlie",
				"mom",
				"aaron",
				"alextimetowork@myworkplace.com",
				"charlieisasleep4@myworkplace.com",
				"momthebest7@bestforyou.com",
				"momsthebest7@bestemailforall.com",
				"aaronworkshard3@myworkplace.com",
				"caseyousoon8@myworkplace.com"
				};
		
		for(String entity:entities){
			if( ( observedLogicalPredicates.contains(entity) && !firingPredicates.contains(entity))
			|| (!observedLogicalPredicates.contains(entity) && firingPredicates.contains(entity))){ 
				if(Decoder.verbose) System.out.println("Veto candidate because of missing entity:"+entity+"!"); 
				return 0.0;
			}
		}
		
		cache.put(key, fm);
		return fm;
	}
}
