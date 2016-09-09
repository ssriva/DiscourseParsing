package testing;


import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;


public class PrintAssociationRules {

	public static void main(String[] args) {
		// TODO Auto-generated method stub

		try {
			HashMap<String,Set<String>> associatedLogicalForms = new HashMap<String, Set<String>>();

			List<String> logicalVocab = Files.readAllLines(Paths.get("data/actions.txt")).stream().map(s -> s.trim()).filter(s -> s.length()>0).collect(Collectors.toList());
			PrintWriter writer = new PrintWriter("data/allAssociationRules.txt");

			List<String> lines_1 = Files.readAllLines(Paths.get("data/lexiconEntriesCleaned.txt"));
			for(String line:lines_1){
				String toks[] = line.split("\t");

				String word = toks[0];
				if(!associatedLogicalForms.containsKey(word)){
					associatedLogicalForms.put(word, new HashSet<String>());
				}
				associatedLogicalForms.get(word).addAll(Arrays.asList(toks[1].split(" ")).stream().filter( l -> logicalVocab.contains(l)).collect(Collectors.toSet()));
				//associatedLogicalForms.put(toks[0], toks[1]);
			}

			List<String> lines_2 = Files.readAllLines(Paths.get("data/lexiconSynCleaned.txt"));
			for(String line:lines_2){
				String toks[] = line.split("\t");
				String word = toks[0];
				String synonyms[] = toks[1].split(" ");
				for(String synonym:synonyms){
					if(associatedLogicalForms.containsKey(synonym)){
						if(!associatedLogicalForms.containsKey(word)){
							associatedLogicalForms.put(word, new HashSet<String>());
						}
						associatedLogicalForms.get(word).addAll(associatedLogicalForms.get(synonym));
					}
				}
			}
			
			for(Entry<String,Set<String>>e:associatedLogicalForms.entrySet()){
				if(e.getValue().size()>0){
					writer.println(e.getKey()+"\t"+String.join(" ", e.getValue()));
				}
			}
			writer.close();
				

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
