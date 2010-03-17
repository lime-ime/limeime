package net.toload.main;

interface IDBService
{
	void loadMapping(String filename);
	void resetMapping();
	void executeUserBackup();
	void restoreRelatedUserdic();
	void resetUserBackup();
	 
}