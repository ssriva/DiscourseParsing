package utils;

import com.jayantkrish.jklol.ccg.CcgParse;

public class CcgParseWrapper {
	
	/*
	 * Simple wrapper class to represent either of two types of parse annotations for a sentence:
	 * 1) An actual CCG derivation and its corresponding logical form, stored in the 'ccgparse' field
	 * 2) Intended logical form of the sentence only, without an accompanying derivation (in this case, the 'ccgparse' field is null).
	 * 
	 * In both cases, the intended logical form is stored as a String in the field 'stringLogicalForm'.
	 * 
	 * */
		
	private CcgParse ccgparse;
	private String stringLogicalForm;
	private boolean isTrueParse;
	
	public CcgParseWrapper(CcgParse parse){
		this.ccgparse = parse;
		this.stringLogicalForm = ParsingUtils.simplify(parse);
		this.isTrueParse = true;
	}
	
	public CcgParseWrapper(String stringLogicalForm) {
		this.ccgparse = null;
		this.stringLogicalForm = stringLogicalForm;
		this.isTrueParse = false;
	}
	
	public boolean isTrueParse(){	return this.isTrueParse;	}
		
	public CcgParse getCcgParse(){	return this.ccgparse;	}
	
	public String getStringLogicalForm(){	return this.stringLogicalForm;	}
}