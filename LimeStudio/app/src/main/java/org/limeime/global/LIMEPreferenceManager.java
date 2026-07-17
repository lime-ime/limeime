/*
 *
 *  *
 *  **    Copyright 2026, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
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

package org.limeime.global;


import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.limeime.data.ImConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LIMEPreferenceManager {

	private static final String STARTUP_CONFIG_VERSION = "startup_config_version";

	public static class ReverseLookupOption {
		public final String label;
		public final String value;

		public ReverseLookupOption(String label, String value) {
			this.label = label;
			this.value = value;
		}
	}
	
	private final Context ctx;
	
	public LIMEPreferenceManager(Context context){		
		this.ctx = context;
		
	}
	
	public String getTableTotalRecords(String table){
		table = preProcessTableName(table);
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		String records = sp.getString(table + "total_record", "");
		if(records.isEmpty()){
			SharedPreferences ssp = ctx.getSharedPreferences(table + "total_record", Context.MODE_PRIVATE);
			records = ssp.getString(table + "total_record", "");
			if(!records.isEmpty()) setTableTotalRecords(table, records);
		}
		return records;
	}
	public void setTableTotalRecords(String table, String records){
		table = preProcessTableName(table);
		//SharedPreferences sp = ctx.getSharedPreferences(table + "total_record", 0);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putString(table + "total_record", records).apply();	
	}
	
	
	
	public String getTableVersion(String table){
		table = preProcessTableName(table);
		
		SharedPreferences sdp = PreferenceManager.getDefaultSharedPreferences(ctx);
		String version = sdp.getString(table + "mapping_version", "");
		// retain mapping_version saved in shared Preference and saved to default reference
		if(version.isEmpty()){
			SharedPreferences ssp = ctx.getSharedPreferences(table + "mapping_version", Context.MODE_PRIVATE);
			version = ssp.getString(table + "mapping_version", "");
			if(!version.isEmpty()) setTableVersion(table, version);
		}
		return version;
	}
	public void setTableVersion(String table, String version){
		table = preProcessTableName(table);
		//SharedPreferences sp = ctx.getSharedPreferences(table + "mapping_version", 0);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putString(table + "mapping_version", version).apply();	
	}
	
	public String getTableMappingFilename(String table){
		table = preProcessTableName(table);
		//SharedPreferences sp = ctx.getSharedPreferences(table + "mapping_file", 0);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getString(table + "mapping_file", "");
	}
	
	public void setTableMappingFilename(String table, String filename){
		table = preProcessTableName(table);
		//SharedPreferences sp = ctx.getSharedPreferences(table + "mapping_file", 0);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putString(table + "mapping_file", filename).apply();	
	}
	
	public String getTableMappingTempFilename(String table){
		table = preProcessTableName(table);
		//SharedPreferences sp = ctx.getSharedPreferences(table + "mapping_file_temp", 0);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getString(table + "mapping_file_temp", "");
	}
	
	public void setTableTempMappingFilename(String table, String filename){
		table = preProcessTableName(table);
		//SharedPreferences sp = ctx.getSharedPreferences(table + "mapping_file_temp", 0);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putString(table + "mapping_file_temp", filename).apply();	
	}
	
	
	public String getTotalUserdictRecords(){

		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		String records = sp.getString("total_userdict_record", "0");
		if(records.equals("0") ){
			SharedPreferences ssp = ctx.getSharedPreferences("total_userdict_record", Context.MODE_PRIVATE);
			records = ssp.getString("total_userdict_record", "0");
			if(records.equals("0")) setTotalUserdictRecords(records);
		}
		return records;
			
	}
	public void setTotalUserdictRecords(String records){

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putString("total_userdict_record", records).apply();	
	}

	public boolean getLanguageMode(){

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getString("language_mode", "no").equals("yes");
	}
	public void setLanguageMode(boolean englishOnly){

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		String loadingStatus = englishOnly?"yes":"no";
		
		putStringAndBumpStartupConfigVersionIfChanged(sp, "language_mode", loadingStatus);
		
	}

	private String getReverseLookupPreferenceKey(String table) {
		if (table == null || table.isEmpty()) {
			table = LIME.DB_TABLE_PHONETIC;
		}
		if (table.equals(LIME.DB_TABLE_PHONETIC)) {
			return "bpmf_im_reverselookup";
		}
		return table + "_im_reverselookup";
	}

	public String getReverseLookupTable(String table){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getString(getReverseLookupPreferenceKey(table), "none");
	}

	public String getRerverseLookupTable(String table){
		return getReverseLookupTable(table);
	}

	public void setReverseLookupTable(String table, String lookupTable){
		if (lookupTable == null || lookupTable.isEmpty()) {
			lookupTable = "none";
		}
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putString(getReverseLookupPreferenceKey(table), lookupTable).apply();
	}

	public static List<ReverseLookupOption> buildReverseLookupOptions(List<ImConfig> imList, String noneLabel) {
		List<String> codes = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		if (imList != null) {
			for (ImConfig im : imList) {
				if (im == null || im.getCode() == null || im.isDisable()) continue;
				String code = im.getCode();
				if ("emoji".equals(code) || indexOfIMCode(code) < 0) continue;
				codes.add(code);
				String label = im.getDesc();
				labels.add(label == null || label.isEmpty() ? fallbackIMLabel(code) : label);
			}
		}
		return buildReverseLookupOptions(codes, labels, noneLabel);
	}

	public static List<ReverseLookupOption> buildReverseLookupOptions(List<String> codes,
			List<String> labels, String noneLabel) {
		List<ReverseLookupOption> options = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		String safeNoneLabel = noneLabel == null || noneLabel.isEmpty() ? "none" : noneLabel;
		options.add(new ReverseLookupOption(safeNoneLabel, "none"));
		seen.add("none");
		if (codes != null) {
			for (int i = 0; i < codes.size(); i++) {
				String code = codes.get(i);
				if (code == null || code.isEmpty() || seen.contains(code) || indexOfIMCode(code) < 0) {
					continue;
				}
				String label = labels != null && i < labels.size() ? labels.get(i) : null;
				if (label == null || label.isEmpty()) {
					label = fallbackIMLabel(code);
				}
				options.add(new ReverseLookupOption(label, code));
				seen.add(code);
			}
		}
		return options.size() > 1 ? options : fallbackReverseLookupOptions(safeNoneLabel);
	}

	public static List<ReverseLookupOption> buildReverseLookupOptions(String activeState, String noneLabel) {
		List<String> codes = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		if (activeState != null && !activeState.trim().isEmpty()) {
			for (String raw : activeState.split(";")) {
				if (raw == null || raw.isEmpty()) continue;
				try {
					int index = Integer.parseInt(raw);
					if (index < 0 || index >= LIME.IM_CODES.length) continue;
					codes.add(LIME.IM_CODES[index]);
					labels.add(index < LIME.IM_FULL_NAMES.length ? LIME.IM_FULL_NAMES[index] : LIME.IM_SHORT_NAMES[index]);
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return buildReverseLookupOptions(codes, labels, noneLabel);
	}

	public List<ReverseLookupOption> getReverseLookupOptions() {
		return buildReverseLookupOptions(getIMActivatedState(), "無");
	}

	public static String[] reverseLookupLabels(List<ReverseLookupOption> options) {
		List<ReverseLookupOption> safeOptions = ensureOptions(options, "無");
		String[] labels = new String[safeOptions.size()];
		for (int i = 0; i < safeOptions.size(); i++) {
			labels[i] = safeOptions.get(i).label;
		}
		return labels;
	}

	public static String[] reverseLookupValues(List<ReverseLookupOption> options) {
		List<ReverseLookupOption> safeOptions = ensureOptions(options, "無");
		String[] values = new String[safeOptions.size()];
		for (int i = 0; i < safeOptions.size(); i++) {
			values[i] = safeOptions.get(i).value;
		}
		return values;
	}

	private static List<ReverseLookupOption> ensureOptions(List<ReverseLookupOption> options, String noneLabel) {
		return options == null || options.isEmpty() ? fallbackReverseLookupOptions(noneLabel) : options;
	}

	private static List<ReverseLookupOption> fallbackReverseLookupOptions(String noneLabel) {
		List<ReverseLookupOption> options = new ArrayList<>();
		options.add(new ReverseLookupOption(noneLabel, "none"));
		for (int i = 0; i < LIME.IM_CODES.length && i < LIME.IM_FULL_NAMES.length; i++) {
			options.add(new ReverseLookupOption(LIME.IM_FULL_NAMES[i], LIME.IM_CODES[i]));
		}
		return options;
	}

	private static String fallbackIMLabel(String code) {
		int index = indexOfIMCode(code);
		if (index >= 0 && index < LIME.IM_FULL_NAMES.length) {
			return LIME.IM_FULL_NAMES[index];
		}
		if (index >= 0 && index < LIME.IM_SHORT_NAMES.length) {
			return LIME.IM_SHORT_NAMES[index];
		}
		return code;
	}

	private static int indexOfIMCode(String code) {
		if (code == null) return -1;
		for (int i = 0; i < LIME.IM_CODES.length; i++) {
			if (code.equals(LIME.IM_CODES[i])) {
				return i;
			}
		}
		return -1;
	}
	
	
	
	public boolean getFixedCandidateViewDisplay() {

        return true;
    }

	
	public boolean getLearnRelatedWord(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("candidate_suggestion", true);
	}

	public boolean getLearnPhrase(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("learn_phrase", true);
	}
	
	public boolean getDisablePhysicalSelKeyOption(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("disable_physical_selkey_option", false);
	}
	
	public boolean getEnglishPrediction(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("english_dictionary_enable", true);
	}
	
	public boolean getPhysicalKeyboardEnable(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("physical_keyboard_enable", true);
	}
	
	public boolean getEnglishPredictionOnPhysicalKeyboard(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("english_dictionary_physical_keyboard", false);
	}
	
	public boolean getSortSuggestions(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("learning_switch", true);
	}

	public boolean getCandidateSuggestionPunctutation(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("candidate_suggestion_punctuation", true);
	}
	
	public boolean getPhysicalKeyboardSortSuggestions(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("physical_keyboard_sort", true);
	}

	public boolean getSimiliarEnable(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("similiar_enable", true);
	}
	
	/**
	 * Always returns {@code true}. The {@code candidate_switch} preference UI was
	 * removed because free-scroll candidate selection is the only sensible behaviour
	 * on modern Android; the paged alternative is unused. The stored value (if any)
	 * is ignored.
	 */
	public boolean getSelectDefaultOnSliding(){
		return true;
	}
	
	public boolean getVibrateOnKeyPressed(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("vibrate_on_keypress", true);
	}
	
	
	
	public boolean getSoundOnKeyPressed(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("sound_on_keypress", false);
	}

	public Float getKeypressSoundVolume(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return Float.parseFloat(sp.getString("keypress_sound_volume", "-1"));
	}

	public Integer getEmojiDisplayPosition(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		if (sp.contains("enable_emoji")) {
			SharedPreferences.Editor editor = sp.edit().remove("enable_emoji");
			if (!sp.getBoolean("enable_emoji", true)) {
				editor.putString("enable_emoji_position", "0");
			}
			editor.apply();
		}
		return Integer.parseInt(sp.getString("enable_emoji_position", "5"));
	}

	public boolean getPersistentLanguageMode(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("persistent_language_mode", false);
	}
	
	public boolean getShowNumberRowInEnglish(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("number_row_in_english", true);
	}
	public void syncIMActivatedState(List<ImConfig> imlist){
		StringBuilder state = new StringBuilder();
		HashMap<String, String> imMap = new HashMap<>();
		for(ImConfig i :imlist){
			if(i == null || i.isDisable()) {
				continue;
			}
			imMap.put(i.getCode(), i.getCode());
		}

		if(imMap.get(LIME.IM_CUSTOM) != null){
			state.append("0");
		}

		if(imMap.get(LIME.IM_CJ) != null){
			if(state.length() > 0){state.append(";");}
			state.append("1");
		}
		if(imMap.get(LIME.IM_SCJ) != null){
			if(state.length() > 0){state.append(";");}
			state.append("2");
		}
		if(imMap.get(LIME.IM_CJ5) != null){
			if(state.length() > 0){state.append(";");}
			state.append("3");
		}
		if(imMap.get(LIME.IM_ECJ) != null){
			if(state.length() > 0){state.append(";");}
			state.append("4");
		}
		if(imMap.get(LIME.IM_DAYI) != null){
			if(state.length() > 0){state.append(";");}
			state.append("5");
		}
		if(imMap.get(LIME.IM_PHONETIC) != null){
			if(state.length() > 0){state.append(";");}
			state.append("6");
		}
		if(imMap.get(LIME.IM_EZ) != null){
			if(state.length() > 0){state.append(";");}
			state.append("7");
		}
		if(imMap.get(LIME.IM_ARRAY) != null){
			if(state.length() > 0){state.append(";");}
			state.append("8");
		}
		if(imMap.get(LIME.IM_ARRAY10) != null){
			if(state.length() > 0){state.append(";");}
			state.append("9");
		}
		if(imMap.get(LIME.IM_WB) != null){
			if(state.length() > 0){state.append(";");}
			state.append("10");
		}
		if(imMap.get(LIME.IM_HS) != null){
			if(state.length() > 0){state.append(";");}
			state.append("11");
		}
		if(imMap.get(LIME.IM_PINYIN) != null){
			if(state.length() > 0){state.append(";");}
			state.append("12");
		}

		setIMActivatedState(state.toString());
	}
	
	public String getIMActivatedState(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getString("keyboard_state", "0;1;2;3;4;5;6;7;8;9;10;11;12");
	}
	public void setIMActivatedState(String state){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		putStringAndBumpStartupConfigVersionIfChanged(sp, "keyboard_state", String.valueOf(state));
	}
	
	public String getActiveIM(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getString("keyboard_list", LIME.DB_TABLE_PHONETIC);
	}
	
	
	public void setActiveIM(String activeIM){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		putStringAndBumpStartupConfigVersionIfChanged(sp, "keyboard_list", String.valueOf(activeIM));
	}
	
	public boolean getThreerowRemapping(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("three_rows_remapping", false);
	}
	
	public String getPhysicalKeyboardType(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getString("physical_keyboard_type", "normal_keyboard");
	}
	
	public int getAutoCommitValue(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return Integer.parseInt(sp.getString("auto_commit", "0"));
	}
	
	public String getPhoneticKeyboardType(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getString("phonetic_keyboard_type", LIME.DB_TABLE_PHONETIC);
	}
	
	public boolean getAutoCaptalization(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("auto_cap", true);
	}
	
	public boolean getQuickFixes(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("quick_fixes", true);
	}
	
	public boolean getAutoComplete(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("auto_complete", true);
	}
	
	public boolean getDisablePhysicalSelkey(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("disable_physical_selkey", false);
	}
	
	
	public Integer getHanCovertOption(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return Integer.parseInt(sp.getString("han_convert_option", "0"));
	}
	
	public void setHanCovertOption(int value){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putString( "han_convert_option", String.valueOf(value)).apply();	
		
	}
	
	public Integer getSelkeyOption(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return Integer.parseInt(sp.getString("selkey_option", "0"));
	}
	
	public Integer getSimilarCodeCandidates(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return Integer.parseInt(sp.getString("similiar_list", "20"));
	}
	
	public float getFontSize(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return Float.parseFloat(sp.getString("font_size", "1"));
		
	}
	
	public float getKeyboardSize(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return Float.parseFloat(sp.getString("keyboard_size", "1"));
		
	}

	public boolean getSmartChineseInput(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("smart_chinese_input", true);
	}
	
	public boolean getAutoChineseSymbol(){

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("auto_chinese_symbol", false);
	}
	
	
	public Integer getVibrateLevel(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return Integer.parseInt(sp.getString("vibrate_level", "40"));
	}
	
	
	public boolean getAllowNumberMapping(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("accept_number_index", false);
	}
	
	public boolean getAllowSymoblMapping(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("accept_symbol_index", false);
	}
	
	
	
	public boolean getSwitchEnglishModeHotKey(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("switch_english_mode", false);
	}

	public boolean getShiftSwitchEnglishMode(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("switch_english_mode_shift", true);
	}
	
	
	public boolean getAutoHideSoftKeyboard(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("hide_software_keyboard_typing_with_physical", true);

	}
	
	public int getShowArrowKeys(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return Integer.parseInt(sp.getString("show_arrow_key", "0"));
		
	}
	
	public void setShowArrowKeys(int mode){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		putStringAndBumpStartupConfigVersionIfChanged(sp, "show_arrow_key", Integer.toString(mode));
		
	}
	
	public int getSplitKeyboard(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return Integer.parseInt(sp.getString("split_keyboard_mode", "0"));
	}

	public int getKeyboardTheme(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return Integer.parseInt(sp.getString("keyboard_theme", "6"));
	}
	
	public void setSplitKeyboard(int mode){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		putStringAndBumpStartupConfigVersionIfChanged(sp, "split_keyboard_mode", Integer.toString(mode));

	}

	public int getOneHandMode(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return Integer.parseInt(sp.getString("one_hand_mode", "0"));
	}

	public void setOneHandMode(int mode){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		putStringAndBumpStartupConfigVersionIfChanged(sp, "one_hand_mode", Integer.toString(mode));
	}

	public int getNumpadAnchor(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return Integer.parseInt(sp.getString("numpad_anchor", "0"));
	}

	public void setNumpadAnchor(int mode){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		putStringAndBumpStartupConfigVersionIfChanged(sp, "numpad_anchor", Integer.toString(mode));
	}

	public long getStartupConfigVersion(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getLong(STARTUP_CONFIG_VERSION, 0L);
	}

	public long initializeStartupConfigVersion(){
		long current = getStartupConfigVersion();
		if(current > 0L) return current;
		return bumpStartupConfigVersion();
	}

	public void resetStartupConfigVersion(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putLong(STARTUP_CONFIG_VERSION, 0L).apply();
	}

	public boolean resetStartupConfigVersionIfStartupPreferenceChanged(String key){
		if(!isStartupConfigPreferenceKey(key)) return false;
		resetStartupConfigVersion();
		return true;
	}

	private static boolean isStartupConfigPreferenceKey(String key){
		if(key == null) return false;
		switch(key){
			case "keyboard_state":
			case "keyboard_list":
			case "show_arrow_key":
			case "split_keyboard_mode":
			case "one_hand_mode":
			case "numpad_anchor":
			case "keyboard_theme":
			case "language_mode":
			case "persistent_language_mode":
			case "phonetic_keyboard_type":
			case "number_row_in_english":
				return true;
			default:
				return key.endsWith("_keyboard_type");
		}
	}

	private void putStringAndBumpStartupConfigVersionIfChanged(SharedPreferences sp, String key, String value){
		String current = sp.getString(key, null);
		SharedPreferences.Editor editor = sp.edit().putString(key, value);
		if(!value.equals(current)){
			putNextStartupConfigVersion(sp, editor);
		}
		editor.apply();
	}

	private long bumpStartupConfigVersion(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		SharedPreferences.Editor editor = sp.edit();
		long next = putNextStartupConfigVersion(sp, editor);
		editor.apply();
		return next;
	}

	private long putNextStartupConfigVersion(SharedPreferences sp, SharedPreferences.Editor editor){
		long current = sp.getLong(STARTUP_CONFIG_VERSION, 0L);
		long next = Math.max(System.currentTimeMillis(), current + 1L);
		editor.putLong(STARTUP_CONFIG_VERSION, next);
		return next;
	}
	
	public boolean getResetCacheFlag(boolean defaultvalue){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("searchsrv_reset_cache", defaultvalue);
	}
	
	
	
	public void setResetCacheFlag(boolean value){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putBoolean("searchsrv_reset_cache", value).apply();	
	}
	
	
	
	/*
	 * INT Parameter SET/GET
	 */
	public void setParameter(String label, int value){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putInt(label, value).apply();	
	}
	public int getParameterInt(String label){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getInt(label, 0);
	}

	public int getParameterInt(String label, int defaultvalue){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getInt(label, defaultvalue);
	}
	
	/*
	 * LONG Parameter SET/GET
	 */
	public long getParameterLong(String label, long defaultvalue){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getLong(label, defaultvalue);
	}
	
	public long getParameterLong(String label){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getLong(label, 0);
	}
	
	public void setParameter(String label, long value){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putLong(label, value).apply();	
	}
	
	/*
	 * String Parameter SET/GET
	 */
	public void setParameter(String label, String value){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putString(label, value).apply();	
	}
	public String getParameterString(String label){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getString(label, "");
	}
	
	public String getParameterString(String label, String defaultstring){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getString(label, defaultstring);
	}


	/*
	 * Boolean Parameter SET/GET
	 */
	public void setParameter(String label, boolean value){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putBoolean(label, value).apply();	
	}
	public boolean getParameterBoolean(String label){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean(label, false);
	}
	public boolean getParameterBoolean(String label, boolean defaultvalue){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		try{
			return sp.getBoolean(label, defaultvalue);
		}catch(Exception e){
			return defaultvalue;
		}
	}
	
	private String preProcessTableName(String table){
		if(table.endsWith("_")|| table.isEmpty()){
			return table; // processed already.
		}else if(table.equals(LIME.DB_TABLE_PHONETIC)) {
			return "bpmf_";
		}else if(table.equals("mapping")||table.equals("lime") || table.equals("phone") ){
			return "";
		}else{ 
			return table+"_";
		}
	}
	
}
