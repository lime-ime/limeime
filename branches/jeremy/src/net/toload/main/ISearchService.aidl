package net.toload.main;

interface ISearchService
{
		void initial();
		String getTablename();
		void setTablename(String tablename);
		List query(String code);
		List queryUserDic(String code, String word);
		void updateMapping(String id, String code, String word, String pcode, String pword, int score, boolean isDictionary);
		void addDictionary(String id, String code, String word, String pcode, String pword, int score, boolean isDictionary);
		void updateDictionary();
		
}