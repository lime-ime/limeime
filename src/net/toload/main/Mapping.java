package net.toload.main;

/**
 * @author Art Hung
 */
public class Mapping {

	private String id;
	private String code;
	private String word;
	private String pcode;
	private String pword;
	private String related;
	private boolean isDictionary;
	private int score;
	
	
	/**
	 * @return the related
	 */
	public String getRelated() {
		return related;
	}


	/**
	 * @param related the related to set
	 */
	public void setRelated(String related) {
		this.related = related;
	}


	Mapping(){}
	
	
	Mapping(String c, String w, int s){
		this.setCode(c);
		this.setWord(w);
		this.setScore(s);
	}

	Mapping(String pc, String pw, String c, String w, int s){
		this.setPcode(pc);
		this.setPword(pw);
		this.setCode(c);
		this.setWord(w);
		this.setScore(s);
	}
	
	
	/**
	 * @return the pcode
	 */
	public String getPcode() {
		return pcode;
	}
	/**
	 * @param pcode the pcode to set
	 */
	public void setPcode(String pcode) {
		this.pcode = pcode;
	}
	/**
	 * @return the pword
	 */
	public String getPword() {
		return pword;
	}
	/**
	 * @param pword the pword to set
	 */
	public void setPword(String pword) {
		this.pword = pword;
	}
	/**
	 * @return the isDictionary
	 */
	public boolean isDictionary() {
		return isDictionary;
	}
	/**
	 * @param isDictionary the isDictionary to set
	 */
	public void setDictionary(boolean isDictionary) {
		this.isDictionary = isDictionary;
	}
	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}
	/**
	 * @param id the id to set
	 */
	public void setId(String id) {
		this.id = id;
	}
	/**
	 * @return the code
	 */
	public String getCode() {
		if(code != null){
			return code.toUpperCase();
		}
		return code;
	}
	/**
	 * @param code the code to set
	 */
	public void setCode(String code) {
		this.code = code;
	}
	/**
	 * @return the word
	 */
	public String getWord() {
		return word;
	}
	/**
	 * @param word the word to set
	 */
	public void setWord(String word) {
		this.word = word;
	}
	/**
	 * @return the score
	 */
	public int getScore() {
		return score;
	}
	/**
	 * @param score the score to set
	 */
	public void setScore(int score) {
		this.score = score;
	}
	
	
}
