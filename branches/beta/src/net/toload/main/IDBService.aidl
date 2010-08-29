package net.toload.main;

interface IDBService
{
	void loadMapping(String filename, String tablename);
	void resetMapping(String tablename);
	void executeUserBackup();
	void restoreRelatedUserdic();
	void resetUserBackup();
	void resetDownloadDatabase();
	void downloadPreloadedDatabase();
	
	 
}