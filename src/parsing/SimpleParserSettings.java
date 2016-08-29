package parsing;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.*;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.ccg.lexicon.StringContext;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.preprocessing.DictionaryFeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.util.IndexedList;

import instructable.server.LispExecutor;
import instructable.server.ccg.CcgUtils;
import instructable.server.ccg.WeightedCcgExample;

import java.util.*;
import java.util.stream.Collectors;

import utils.PerceptronUtils;


/**
 * Modifies instructable.server.ccg.ParserSettings
 * by removing execution/evaluation components, and intaking
 * a list of sequences(of sentences), rather than a list of sentences.
 */
public class SimpleParserSettings
{
	public List<WeightedCcgExample> ccgExamples;
	public List<List<String[]>>examples;
	public Map<String, Expression2> learnedExamples;
	public List<LexiconEntry> lexicon;
	public List<CcgUnaryRule> unaryRules;
	public FeatureVectorGenerator<StringContext> featureVectorGenerator;

	public CcgParser parser;
	public ParametricCcgParser parserFamily;
	public SufficientStatistics parserParameters;

	public Environment env;
	public IndexedList<String> symbolTable;
	public Set<String> posUsed;

	static final int initialTraining = 10;
	static final int retrainAfterNewCommand = 1;
	static final boolean treatCorpusAsLearnedExamples = true;    

	public SimpleParserSettings(List<String> lexiconEntries, List<String> synonyms, List<String> userDefinedEntries, String[] unaryRules,
			FeatureGenerator<StringContext, String> featureGenerator, List<List<String[]>> examplesList, Boolean trainModel)
	{
		final String midRowComment = "//";
		final String fullRowComment = "#";
		env = Environment.empty();
		symbolTable = IndexedList.create();
		learnedExamples = new HashMap<>();

		// remove all that appears after a "//" or starts with # (parseLexiconEntries only removes lines that start with "#")
		List<String> lexiconWithoutComments = lexiconEntries.stream().filter(e -> !e.contains(fullRowComment)).map(e -> (e.contains(midRowComment) ? e.substring(0, e.indexOf(midRowComment)) : e)).collect(Collectors.toList());

		// add synonyms. format: newWord, {meaning1,meaning2,...} (i.e, newWord = meaning1 U meaning2)
		List<String> lexEntriesFromSyn = new LinkedList<>();
		for (String synonym : synonyms)
		{
			if (synonym.trim().length() <= 1 || synonym.contains(fullRowComment) || synonym.trim().startsWith(midRowComment)) //comment row
				continue;
			String newWord = synonym.substring(0, synonym.indexOf(","));
			newWord = newWord.replace("^", ",");
			//newWord.replace("\"","");
			String[] meanings = synonym.substring(synonym.indexOf("{") + 1, synonym.indexOf("}")).split(",");
			for (String meaning : meanings)
			{
				String meaningWOQuotes = meaning.trim().replace("\"", "");
				for (String lexiconEntry : lexiconWithoutComments) //could save time if transferred all entries to a map, but this is anyway done only once
				{
					String entryWord = lexiconEntry.substring(0, lexiconEntry.indexOf(",")).replace("\"", "");
					if (entryWord.equals(meaningWOQuotes))
					{
						String newLexiconEntry = newWord + lexiconEntry.substring(lexiconEntry.indexOf(","));
						lexEntriesFromSyn.add(newLexiconEntry);
					}
				}
			}
		}

		List<String> allLexiconEntries = new LinkedList<>(lexiconWithoutComments);
		allLexiconEntries.addAll(lexEntriesFromSyn);
		allLexiconEntries.addAll(userDefinedEntries);

		for (String lexEntry : allLexiconEntries) //binding names. this will not bind names which appear inside logical forms, but it probably good enough.
		{
			String[] tokens = lexEntry.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1);
			//go over all names and bind them
			String name = tokens[2].trim();//get the third item (after two commas, but not in quotes
			if (name.startsWith("\"") && name.endsWith("\""))
				name = name.substring(1,name.length()-1);
			if (!name.startsWith("(") && !name.startsWith("*"))
				env.bindName(name, name.replace("_", " "), symbolTable);
		}

		List<LexiconEntry> lexicon = LexiconEntry.parseLexiconEntries(allLexiconEntries);
		lexicon = CcgUtils.induceLexiconHeadsAndDependencies(lexicon);

		List<CcgUnaryRule> unaryRulesList = Lists.newArrayList();
		for (String unaryRule : unaryRules)
		{
			unaryRulesList.add(CcgUnaryRule.parseFrom(unaryRule));
		}

		posUsed = new HashSet<>();
		posUsed.add(ParametricCcgParser.DEFAULT_POS_TAG);
		posUsed.add(CcgUtils.START_POS_TAG);
		ccgExamples = Lists.newArrayList();
		this.examples = examplesList;
		for(List<String[]>sequence:examplesList){
			for (int i = 0; i < sequence.size(); i++)
			{
				String exSentence = sequence.get(i)[0];
				//System.out.println(exSentence); //SSRIV
				String exLogicalForm = sequence.get(i)[1];
				//System.out.println(exLogicalForm); //SSRIV
				Expression2 expression = ExpressionParser.expression2().parseSingleExpression(exLogicalForm);
				WeightedCcgExample example = CcgUtils.createCcgExample(exSentence, expression, posUsed, true, null);
				ccgExamples.add(example);
				if (treatCorpusAsLearnedExamples)
				{
					//actually already tokenizes and POS exSentence above and will be doing it again in addToLearnedExamples, but this isn't significant.
					addToLearnedExamples(exSentence, expression, false);
				}
				List<String> allFunctionNames = LispExecutor.allFunctionNames();
				Set<String> freeSet = StaticAnalysis.getFreeVariables(example.getLogicalForm());
				for (String free : freeSet)
				{
					//add all that's not a function name and not a string (starts and ends with ")
					if (!(free.startsWith("\"") && free.endsWith("\"")) && !allFunctionNames.contains(free))
						env.bindName(free, free.replace("_", " "), symbolTable);
				}
			}
		}

		List<CcgExample> reformattedExamples = Lists.newArrayList();
		for (WeightedCcgExample w : ccgExamples) {
			reformattedExamples.add(new CcgExample(w.getSentence(), null, null, w.getLogicalForm(), null));
		}

		List<StringContext> allContexts = StringContext.getContextsFromExamples(reformattedExamples);
		FeatureVectorGenerator<StringContext> featureVectorGenerator = DictionaryFeatureVectorGenerator.createFromData(allContexts, featureGenerator, true);
		this.ccgExamples = CcgUtils.featurizeExamples(ccgExamples, featureVectorGenerator);

		/*Meat of the process*/

		//1. Create model family
		ParametricCcgParser family = CcgUtils.buildParametricCcgParser(lexicon, unaryRulesList, posUsed, featureVectorGenerator);
		this.lexicon = Lists.newArrayList(lexicon);
		this.unaryRules = Lists.newArrayList(unaryRulesList);
		this.featureVectorGenerator = featureVectorGenerator;

		if(trainModel){
		//2. Train parser to get model parameters 
			//this.parserParameters = ParsingUtils.train(family, ccgExamples, initialTraining, null);
			//this.parserParameters = PerceptronUtils.train(family, ccgExamples, initialTraining, null);
			this.parserParameters = PerceptronUtils.trainOnGivenExamples(family, parserParameters, ccgExamples, initialTraining, false);
			//this.parserParameters = PerceptronUtils.updateOnGivenExamples(family, parserParameters, ccgExamples,0);
			//System.out.println(family.getParameterDescription(parserParameters));

		//3. Get model by passing parameters to model family
			this.parser = family.getModelFromParameters(this.parserParameters);
		}
		this.parserFamily = family;

	}

	/**
	 * Adds new lexicon entries and unary rules to the grammar of the
	 * CCG parser in {@code settings}.
	 * Not using unaryRules. If required, need to add customizable unaryRules to parserKnowledgeSeeder
	 */
	public void updateParserGrammar(List<LexiconEntry> lexiconEntries)
	{
		lexicon.addAll(lexiconEntries);
		this.unaryRules.addAll(unaryRules);
		updateGrammarFromExisting();
	}

	private void updateGrammarFromExisting()
	{
		ParametricCcgParser newFamily = CcgUtils.buildParametricCcgParser(lexicon, this.unaryRules,
				posUsed, featureVectorGenerator);
		SufficientStatistics newParameters = newFamily.getNewSufficientStatistics();
		newParameters.transferParameters(parserParameters);
		parserParameters = newParameters;
		parserFamily = newFamily;
		parser = newFamily.getModelFromParameters(parserParameters);
	}

	public void updateParserGrammar(String newLexicon)
	{
		List<String> lexiconAsList = new LinkedList<>();
		lexiconAsList.add(newLexicon);
		List<LexiconEntry> lexiconEntries = LexiconEntry.parseLexiconEntries(lexiconAsList);
		this.updateParserGrammar(lexiconEntries);//, new LinkedList<>());
	}

	public void removeFromParserGrammar(String lexiconToRemove)
	{
		//if (parserKnowledgeSeeder.hasUserDefinedLex(lexiconToRemove))
		{

			List<String> lexiconAsList = new LinkedList<>();
			lexiconAsList.add(lexiconToRemove);
			List<LexiconEntry> lexiconEntries = LexiconEntry.parseLexiconEntries(lexiconAsList);
			LexiconEntry lexiconEntryToRemove = lexiconEntries.get(0);
			lexicon = lexicon.stream().filter(lex -> !lex.toCsvString().startsWith(lexiconEntryToRemove.toCsvString())).collect(Collectors.toList()); //the lexicon may have additional information (all the number thingies)
			updateGrammarFromExisting();
		}
	}

	public void addTrainingEg(String originalCommand, List<Expression2> commandsLearnt)
	{
		Expression2 expressionLearnt = CcgUtils.combineCommands(commandsLearnt);
		//we first tokenize the sentence (after adding the start symbol) then we join back the tokens, to make sure that it matches future sentences with identical tokens.
		addToLearnedExamples(originalCommand, expressionLearnt, true);

		WeightedCcgExample example = CcgUtils.createCcgExample(originalCommand, expressionLearnt, posUsed, false, featureVectorGenerator);

		List<LexiconEntry> newEntries = CcgUtils.induceLexiconEntriesHeuristic(example, parser);
		System.out.println(newEntries);

		updateParserGrammar(newEntries);//, Lists.newArrayList());
		ccgExamples.add(example);
		retrain(retrainAfterNewCommand);
	}

	private void addToLearnedExamples(String originalCommand, Expression2 expressionLearnt, boolean updateDB)
	{
		List<String> tokens = new LinkedList<>();
		tokens.add(CcgUtils.startSymbol);
		List<String> dummy = new LinkedList<>(); //don't need POS
		CcgUtils.tokenizeAndPOS(originalCommand, tokens, dummy, false, posUsed);
		String jointTokenizedSentence = String.join(" ", tokens);
		learnedExamples.put(jointTokenizedSentence, expressionLearnt);
	}

	public void retrain(int iterations)
	{
		SufficientStatistics newParameters = CcgUtils.train(parserFamily,
				ccgExamples, iterations, parserParameters);

		parser = parserFamily.getModelFromParameters(newParameters);
		this.parserParameters = newParameters;
	}

}
