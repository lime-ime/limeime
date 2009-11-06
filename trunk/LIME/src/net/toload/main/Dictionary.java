
/* 
**
** Copyright 2008, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

package net.toload.main;

/**
 * @author Art Hung
 */
public class Dictionary {

	private String id;
	private String code;
	private String word;
	private String pcode;
	private String pword;
	private boolean isDictionary;
	private int score;
	
	Dictionary(){}
	Dictionary(String c, String w, int s){
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
