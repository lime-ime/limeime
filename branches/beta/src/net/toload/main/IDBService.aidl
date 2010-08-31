package net.toload.main;

interface IDBService
{
	void loadMapping(String filename, String tablename);
	void resetMapping(String tablename);
	void resetDownloadDatabase();
	void downloadPreloadedDatabase();
	void backupDatabase();
	void restoreDatabase();
	
	
	 
}