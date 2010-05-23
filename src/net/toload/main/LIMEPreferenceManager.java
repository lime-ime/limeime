package net.toload.main;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class LIMEPreferenceManager {
	
	static private Context ctx; 
	
	public LIMEPreferenceManager(Context context){		
		this.ctx = context;
		
	}
	
	public String getTableTotalRecords(String table){
		table = preProcessTableName(table);
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		String records = sp.getString(table + "total_record", "");
		if(records.equals("")){
			SharedPreferences ssp = ctx.getSharedPreferences(table + "total_record", 0);
			records = ssp.getString(table + "total_record", "");
			if(!records.equals("")) setTableTotalRecords(table, records);
		}
		return records;
	}
	public void setTableTotalRecords(String table, String records){
		table = preProcessTableName(table);
		//SharedPreferences sp = ctx.getSharedPreferences(table + "total_record", 0);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putString(table + "total_record", records).commit();	
	}
	
	
	
	public String getTableVersion(String table){
		table = preProcessTableName(table);
		
		SharedPreferences sdp = PreferenceManager.getDefaultSharedPreferences(ctx);
		String version = sdp.getString(table + "mapping_version", "");
		// retain mapping_version saved in shared Preference and saved to default reference
		if(version.equals("")){
			SharedPreferences ssp = ctx.getSharedPreferences(table + "mapping_version", 0);
			version = ssp.getString(table + "mapping_version", "");
			if(!version.equals("")) setTableVersion(table, version);
		}
		return version;
	}
	public void setTableVersion(String table, String version){
		table = preProcessTableName(table);
		//SharedPreferences sp = ctx.getSharedPreferences(table + "mapping_version", 0);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putString(table + "mapping_version", version).commit();	
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
		sp.edit().putString(table + "mapping_file", filename).commit();	
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
		sp.edit().putString(table + "mapping_file_temp", filename).commit();	
	}
	
	
	public String getTotalUserdictRecords(){

		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		String records = sp.getString("total_userdict_record", "0");
		if(records.equals("0") ){
			SharedPreferences ssp = ctx.getSharedPreferences("total_userdict_record", 0);
			records = ssp.getString("total_userdict_record", "0");
			if(records.equals("0")) setTotalUserdictRecords(String.valueOf(records));
		}
		return records;
			
	}
	public void setTotalUserdictRecords(String records){

		//SharedPreferences sp = ctx.getSharedPreferences("total_userdict_record", 0);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putString("total_userdict_record", records).commit();	
	}
	
	public boolean getMappingLoading(){

		//SharedPreferences sp = ctx.getSharedPreferences("mapping_loadg", 0);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getString("mapping_loadg", "no").equals("yes");
	}
	public void setMappingLoading(boolean loading){

		//SharedPreferences sp = ctx.getSharedPreferences("mapping_loadg", 0);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		String loadingStatus = loading?"yes":"no";
		
		sp.edit().putString("mapping_loadg",loadingStatus).commit();
		
	}
	
	public int getMappingFileImportLines(){

		//SharedPreferences sp = ctx.getSharedPreferences("mapping_import_line", 0);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return Integer.parseInt( sp.getString( "mapping_import_line", "0"));
	}
	public void setMappingFileImportLines(int lines){
		
		//SharedPreferences sp = ctx.getSharedPreferences( "mapping_import_line", 0);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putString( "mapping_import_line", String.valueOf(lines)).commit();	
	}
	
	public String getRerverseLookupTable(String table){
		
		//if(table.equals("mapping")) table = "default";
		//SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getString(table + "_im_reverselookup", "none");
	}
	
	
	
	public boolean getLearnRelatedWord(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("candidate_suggestion", true);
	}
	
	public boolean getSortSuggestions(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("learning_switch", true);
	}
	
	
	
	public boolean getSelectDefaultOnSliding(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("candidate_switch", true);
	}
	
	public boolean getVibrateOnKeyPressed(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("vibrate_on_keypress", false);
	}
	
	
	
	public boolean getSoundOnKeyPressed(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("sound_on_keypress", false);
	}
	
	public boolean getDefaultInEnglish(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("default_in_english", false);
	}
	
	public String getSelectedKeyboardState(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getString("keyboard_state", "0;1;2;3;4;5;6");
	}
	
	public String getKeyboardSelection(){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getString("keyboard_list", "phonetic");
	}
	
	
	public void setKeyboardSelection(String keyboardname){
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		sp.edit().putString( "keyboard_list", String.valueOf(keyboardname)).commit();	
	}
	
	public boolean getThreerowRemapping(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("three_rows_remapping", false);
	}
	
	public boolean getAutoCaptalization(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("auto_cap", true);
	}
	
	public boolean getQuickFixes(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("quick_fixes", true);
	}
	
	public boolean getShowEnlishgSuggestions(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("show_suggestions", true);
	}
	
	
	public boolean getAutoComplete(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("auto_complete", true);
	}
	
	
	
	public Integer getHanCovertOption(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return Integer.parseInt(sp.getString("han_convert_option", "0"));
	}
	
	
	
	public Integer getSimilarCodeCandidates(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return Integer.parseInt(sp.getString("similiar_list", "20"));
	}
	
	public boolean getShowNumberKeypard(){
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		return sp.getBoolean("display_number_keypads", false);
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

	
	private String preProcessTableName(String table){
		if(table.endsWith("_")|| table.equals("")){ 
			return table; // processed already.
		}else if(table.equals("phonetic")) {
			return "bpmf_";
		}else if(table.equals("mapping")||table.equals("lime") || table.equals("phone") ){
			return "";
		}else{ 
			return table+"_";
		}
	}
		
	
}
