package net.toload.main.hd.global;

import java.util.LinkedList;
import java.util.List;

public class ChineseSymbol {
	private final static String chineseSymbols = "，|。|、|？|！|：|；|（|）|「|」|『|』|【|】|" +
			"／|＼|－|＿|＊|＆|︿|％|＄|＃|＠|～|｛|｝|［|］|＜|＞|＋|｜|‵|＂";	
	
	
	private static List<Mapping> mChineseSymbolMapping = new LinkedList<Mapping>();
	public static String getSymbol(char symbol){
	
		switch(symbol){
		case '.': return "。";	
		case ',': return "，";	
		case '/': return "／";	
		case '\\': return "＼";	
		case '=': return "＝";	
		case '-': return "－";	
		case '_': return "＿";	
		case '*': return "＊";	
		case '&': return "＆";	
		case '^': return "︿";	
		case '%': return "％";	
		case '$': return "＄";	
		case '#': return "＃";	
		case '@': return "＠";	
		case '~': return "～";	
		case '`': return "‵";	
		case '"': return "＂";	
		case '\'': return "’";	
		case '?': return "？";	
		case '}': return "｝";	
		case '{': return "｛";	
		case ']': return "］";	
		case '[': return "［";	
		case '<': return "＜";	
		case '>': return "＞";	
		case '+': return "＋";	
		case '(': return "（";	
		case ')': return "）";	
		case '|': return "｜";	
		case ':': return "：";	
		case ';': return "；";	
		case '1': return "１";	
		case '2': return "２";	
		case '3': return "３";	
		case '4': return "４";	
		case '5': return "５";	
		case '6': return "６";	
		case '7': return "７";	
		case '8': return "８";	
		case '9': return "９";	
		case '0': return "０";
		case '!': return "！";
		}  
		return null;
	}
	
	public static List<Mapping> getChineseSymoblList(){

		if(mChineseSymbolMapping.size()==0){
			String [] symArray =  chineseSymbols.split("\\|");
			
			for(String sym: symArray){
				Mapping mapping = new Mapping();
				mapping.setCode("");
				mapping.setWord(sym);
				mapping.setDictionary(true);
				mChineseSymbolMapping.add(mapping);
				
			}
		}
		//Log.i("getChineseSymoblList()", "mChineseSymbolMapping.size()=" + mChineseSymbolMapping.size());
		return mChineseSymbolMapping;
		
	}
	
}
