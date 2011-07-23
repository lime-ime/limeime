package net.toload.main.hd;

public class ChineseSymbol {
	private final String selkey = ",./";

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
	
}
