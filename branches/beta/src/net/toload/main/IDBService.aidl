package net.toload.main;

interface IDBService
{
	void loadMapping(String filename, String tablename);
	void resetMapping(String tablename);
	void resetDownloadDatabase();
	void downloadPreloadedDatabase();
	void backupDatabase();
	void restoreDatabase();
	void resetImInfo(String im);
	void removeImInfo(String im, String field);
	void setImInfo(String im, String field, String value);
	String getImInfo(String im, String field);
}