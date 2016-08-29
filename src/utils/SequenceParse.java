package utils;

import java.util.List;

public class SequenceParse {
	
	public List<CcgParseWrapper> parses;
	public List<String> bestPath;

	public SequenceParse(List<CcgParseWrapper> bestParses, List<String> path) {
		this.parses = bestParses;
		this.bestPath = path;
	}
}
