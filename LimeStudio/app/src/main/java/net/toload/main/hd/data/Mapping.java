/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd.data;

/**
 * Unified data model for IME mapping records.
 * 
 * <p>This class represents both:
 * <ul>
 *   <li>IME candidates (code → word mappings with match type metadata)</li>
 *   <li>Database records (for import/export and management UI)</li>
 *   <li>Related phrases (parent → child word associations)</li>
 * </ul>
 * 
 * <p>Subclasses:
 * <ul>
 *   <li>{@link Record} - Alias for UI layer (ManageIm, SetupIm)</li>
 *   <li>{@link Related} - Alias for related phrase management UI</li>
 * </ul>
 * 
 * <p>This class is a pure POJO with no SQL code. All database operations
 * should be performed through {@link net.toload.main.hd.limedb.LimeDB}.
 * 
 * @author Art Hung
 * @author LimeIME Team
 */
public class Mapping {

	// ==================== Identity ====================
	private String id;  // String for null checks in SearchServer logic

	// ==================== Code Fields ====================
	private String code;
	private String codeorig;
	private String code3r;  // Code without tone keys (for phonetic table)

	// ==================== Word Fields ====================
	private String word;    // Output word (also used as cword for Related)
	private String pword;   // Parent/previous word (for related phrases)
	private String related; // Related phrase info string

	// ==================== Scoring ====================
	private int score;      // User score
	private int basescore;  // Base score from preloaded data

	// ==================== Display Metadata ====================
	//Jeremy '12,5,30 changed from string to boolean to indicate if it's from highLighted list or exact match result
	//Jeremy '15,6,4 renamed to highLighted.
	private Boolean highLighted = true;
	private int recordType;

	// ==================== Record Type Constants ====================
	public static final int RECORD_COMPOSING_CODE = 1;
	public static final int RECORD_EXACT_MATCH_TO_CODE = 2;
	public static final int RECORD_PARTIAL_MATCH_TO_CODE = 3;
	public static final int RECORD_RELATED_PHRASE = 4;
	public static final int RECORD_ENGLISH_SUGGESTION = 5;
	public static final int RECORD_RUNTIME_BUILT_PHRASE = 6;
	public static final int RECORD_CHINESE_PUNCTUATION_SYMBOL = 7;
	public static final int RECORD_HAS_MORE_RECORDS_MARK = 8;
	public static final int RECORD_EXACT_MATCH_TO_WORD = 9;
	public static final int RECORD_PARTIAL_MATCH_TO_WORD = 10;
	public static final int RECORD_COMPLETION_SUGGESTION_WORD = 11;
	public static final int RECORD_EMOJI_WORD = 12;

	// ==================== Constructors ====================
	
	/** Empty constructor */
	public Mapping() {}

	/**
	 * Copy constructor for cloning a mapping.
	 * @param mapping The mapping to clone
	 */
	public Mapping(Mapping mapping) {
		this.setId(mapping.id);
		this.setCode(mapping.code);
		this.setCodeorig(mapping.codeorig);
		this.setCode3r(mapping.code3r);
		this.setWord(mapping.word);
		this.setPword(mapping.pword);
		this.setRelated(mapping.related);
		this.setScore(mapping.score);
		this.setBasescore(mapping.basescore);
		this.setHighLighted(mapping.isHighLighted());
		this.setRecordType(mapping.recordType);
	}

	// ==================== Identity Accessors ====================

	/**
	 * @return the id (String, nullable for special cases like composing code)
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
	 * Gets the id as an integer for database operations.
	 * @return the id parsed as int, or 0 if null/invalid
	 */
	public int getIdAsInt() {
		if (id != null && !id.isEmpty()) {
			try {
				return Integer.parseInt(id);
			} catch (NumberFormatException e) {
				return 0;
			}
		}
		return 0;
	}

	/**
	 * Sets the id from an integer value.
	 * @param id the integer id to set
	 */
	public void setId(int id) {
		this.id = String.valueOf(id);
	}

	// ==================== Code Accessors ====================

	/**
	 * @return the code (lowercase)
	 */
	public String getCode() {
		if (code != null) {
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

	public String getCodeorig() {
		return codeorig;
	}

	public void setCodeorig(String codeorig) {
		this.codeorig = codeorig;
	}

	/**
	 * Gets the code without tone keys (for phonetic table).
	 * @return The code without tone keys (3, 4, 6, 7)
	 */
	public String getCode3r() {
		return code3r;
	}

	/**
	 * Sets the code without tone keys.
	 * @param code3r The code without tone keys
	 */
	public void setCode3r(String code3r) {
		this.code3r = code3r;
	}

	// ==================== Word Accessors ====================

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
	 * Gets the child word (alias for getWord, used in Related context).
	 * @return the word (same as getWord)
	 */
	public String getCword() {
		return word;
	}

	/**
	 * Sets the child word (alias for setWord, used in Related context).
	 * @param cword the child word to set
	 */
	public void setCword(String cword) {
		this.word = cword;
	}

	/**
	 * @return parent/previous word (used in related phrases)
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
	 * Gets the related phrase information.
	 * @return The related phrase string, or null if none
	 */
	public String getRelated() {
		return related;
	}

	/**
	 * Sets the related phrase information.
	 * @param related The related phrase string
	 */
	public void setRelated(String related) {
		this.related = related;
	}

	// ==================== Score Accessors ====================

	/**
	 * @return the score (user score)
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

	/**
	 * Gets the user score (alias for getScore, used in Related context).
	 * @return the score
	 */
	public int getUserscore() {
		return score;
	}

	/**
	 * Sets the user score (alias for setScore, used in Related context).
	 * @param userscore the user score to set
	 */
	public void setUserscore(int userscore) {
		this.score = userscore;
	}

	public int getBasescore() {
		return basescore;
	}

	public void setBasescore(int score) {
		this.basescore = score;
	}

	// ==================== Display Metadata ====================

	public Boolean isHighLighted() {
		return highLighted;
	}

	public void setHighLighted(Boolean related) {
		this.highLighted = related;
	}

	private void setRecordType(int recordType) {
		this.recordType = recordType;
	}

	public int getRecordType() {
		return recordType;
	}

	// ==================== Record Type Checkers ====================

	public boolean isComposingCodeRecord() {
		return recordType == RECORD_COMPOSING_CODE;
	}

	public boolean isExactMatchToCodeRecord() {
		return recordType == RECORD_EXACT_MATCH_TO_CODE;
	}

	public boolean isPartialMatchToCodeRecord() {
		return recordType == RECORD_PARTIAL_MATCH_TO_CODE;
	}

	public boolean isRelatedPhraseRecord() {
		return recordType == RECORD_RELATED_PHRASE;
	}

	public boolean isEnglishSuggestionRecord() {
		return recordType == RECORD_ENGLISH_SUGGESTION;
	}

	public boolean isChinesePunctuationSymbolRecord() {
		return recordType == RECORD_CHINESE_PUNCTUATION_SYMBOL;
	}

	public boolean isHasMoreRecordsMarkRecord() {
		return recordType == RECORD_HAS_MORE_RECORDS_MARK;
	}

	public boolean isRuntimeBuiltPhraseRecord() {
		return recordType == RECORD_RUNTIME_BUILT_PHRASE;
	}

	public boolean isEmojiRecord() {
		return recordType == RECORD_EMOJI_WORD;
	}

	// Identify exactly or partially match to the word queried (reverse query codes by word)
	public boolean isExactMatchToWordRecord() {
		return recordType == RECORD_EXACT_MATCH_TO_WORD;
	}

	public boolean isPartialMatchToWordRecord() {
		return recordType == RECORD_PARTIAL_MATCH_TO_WORD;
	}

	public boolean isCompletionSuggestionRecord() {
		return recordType == RECORD_COMPLETION_SUGGESTION_WORD;
	}

	// ==================== Record Type Setters ====================

	// Identify the record to be the current code typed by user and can be used to type English in mixed mode.
	public void setComposingCodeRecord() {
		this.recordType = RECORD_COMPOSING_CODE;
	}

	public void setExactMatchToCodeRecord() {
		this.recordType = RECORD_EXACT_MATCH_TO_CODE;
	}

	public void setPartialMatchToCodeRecord() {
		this.recordType = RECORD_PARTIAL_MATCH_TO_CODE;
	}

	public void setRelatedPhraseRecord() {
		this.recordType = RECORD_RELATED_PHRASE;
	}

	public void setEnglishSuggestionRecord() {
		this.recordType = RECORD_ENGLISH_SUGGESTION;
	}

	public void setChinesePunctuationSymbolRecord() {
		this.recordType = RECORD_CHINESE_PUNCTUATION_SYMBOL;
	}

	public void setHasMoreRecordsMarkRecord() {
		this.recordType = RECORD_HAS_MORE_RECORDS_MARK;
	}

	public void setRuntimeBuiltPhraseRecord() {
		this.recordType = RECORD_RUNTIME_BUILT_PHRASE;
	}

	// Identify exactly or partially match to the word queried (reverse query codes by word)
	public void setExactMatchToWordRecord() {
		this.recordType = RECORD_EXACT_MATCH_TO_WORD;
	}

	public void setPartialMatchToWordRecord() {
		this.recordType = RECORD_PARTIAL_MATCH_TO_WORD;
	}

	public void setCompletionSuggestionRecord() {
		this.recordType = RECORD_COMPLETION_SUGGESTION_WORD;
	}

	public void setEmojiRecord() {
		this.recordType = RECORD_EMOJI_WORD;
	}
}
