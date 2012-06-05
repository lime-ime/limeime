/*    
**    Copyright 2010, The LimeIME Open Source Project
** 
**    Project Url: http://code.google.com/p/limeime/
**                 http://android.toload.net/
**
**    This program is free software: you can redistribute it and/or modify
**    it under the terms of the GNU General Public License as published by
**    the Free Software Foundation, either version 3 of the License, or
**    (at your option) any later version.

**    This program is distributed in the hope that it will be useful,
**    but WITHOUT ANY WARRANTY; without even the implied warranty of
**    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
**    GNU General Public License for more details.

**    You should have received a copy of the GNU General Public License
**    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package net.toload.main.hd.global;

/**
 * @author Art Hung
 */
public class Mapping {

	private String id;
	private String code;
	//private String pcode;
	private String word;
	private String pword;
	private Boolean related=true;  //Jeremy '12,5,30 changed from string to boolean to indicate if it's from related list or exact match result
	private boolean isDictionary;
	private int score;
	
	
	/**
	 * @return the related
	 */
	public Boolean getRelated() {
		return related;
	}


	/**
	 * @param related the related to set
	 */
	public void setRelated(Boolean related) {
		this.related = related;
	}


	public Mapping(){}
	
	
	public Mapping(String c, String w, int s, boolean d){
		this.setCode(c);
		this.setWord(w);
		this.setScore(s);
		this.setDictionary(d);
	}

	public Mapping(String pw, String c, String w, int s, boolean d){
		this.setPword(pw);
		this.setCode(c);
		this.setWord(w);
		this.setScore(s);
		this.setDictionary(d);
	}
	
	/**
	 * Clone mapping '12,6,5 Jeremy.
	 * @param mapping
	 */
	public Mapping(Mapping mapping) {
		this.setId(mapping.id);
		this.setCode(mapping.code);
		this.setWord(mapping.word);
		this.setPword(mapping.pword);
		this.setScore(mapping.score);
		this.setDictionary(mapping.isDictionary);
		this.setRelated(mapping.getRelated());
	}


	/**
	 * @return the pcode
	 *
	public String getPcode() {
		return pcode;
	}
	/**
	 * @param pcode the pcode to set
	 *
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
			return code.toLowerCase();
		}
		return null;
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


	public void clear() {
		id="";
		code="";
		word="";
		pword="";
		related=false;
		isDictionary=false;
		score=0;
		
	}
	
	
}
