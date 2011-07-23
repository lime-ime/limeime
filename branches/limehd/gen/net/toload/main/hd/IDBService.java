/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: C:\\DataCenter\\Project\\Android\\Development\\Workspace\\LIMEHD\\src\\net\\toload\\main\\hd\\IDBService.aidl
 */
package net.toload.main.hd;
public interface IDBService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements net.toload.main.hd.IDBService
{
private static final java.lang.String DESCRIPTOR = "net.toload.main.hd.IDBService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an net.toload.main.hd.IDBService interface,
 * generating a proxy if needed.
 */
public static net.toload.main.hd.IDBService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = (android.os.IInterface)obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof net.toload.main.hd.IDBService))) {
return ((net.toload.main.hd.IDBService)iin);
}
return new net.toload.main.hd.IDBService.Stub.Proxy(obj);
}
public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_loadMapping:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.lang.String _arg1;
_arg1 = data.readString();
this.loadMapping(_arg0, _arg1);
reply.writeNoException();
return true;
}
case TRANSACTION_resetMapping:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
this.resetMapping(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_resetDownloadDatabase:
{
data.enforceInterface(DESCRIPTOR);
this.resetDownloadDatabase();
reply.writeNoException();
return true;
}
case TRANSACTION_downloadDayi:
{
data.enforceInterface(DESCRIPTOR);
this.downloadDayi();
reply.writeNoException();
return true;
}
case TRANSACTION_downloadPhonetic:
{
data.enforceInterface(DESCRIPTOR);
this.downloadPhonetic();
reply.writeNoException();
return true;
}
case TRANSACTION_downloadPhoneticAdv:
{
data.enforceInterface(DESCRIPTOR);
this.downloadPhoneticAdv();
reply.writeNoException();
return true;
}
case TRANSACTION_downloadCj:
{
data.enforceInterface(DESCRIPTOR);
this.downloadCj();
reply.writeNoException();
return true;
}
case TRANSACTION_downloadScj:
{
data.enforceInterface(DESCRIPTOR);
this.downloadScj();
reply.writeNoException();
return true;
}
case TRANSACTION_downloadCj5:
{
data.enforceInterface(DESCRIPTOR);
this.downloadCj5();
reply.writeNoException();
return true;
}
case TRANSACTION_downloadEcj:
{
data.enforceInterface(DESCRIPTOR);
this.downloadEcj();
reply.writeNoException();
return true;
}
case TRANSACTION_downloadArray:
{
data.enforceInterface(DESCRIPTOR);
this.downloadArray();
reply.writeNoException();
return true;
}
case TRANSACTION_downloadArray10:
{
data.enforceInterface(DESCRIPTOR);
this.downloadArray10();
reply.writeNoException();
return true;
}
case TRANSACTION_downloadWb:
{
data.enforceInterface(DESCRIPTOR);
this.downloadWb();
reply.writeNoException();
return true;
}
case TRANSACTION_downloadEz:
{
data.enforceInterface(DESCRIPTOR);
this.downloadEz();
reply.writeNoException();
return true;
}
case TRANSACTION_downloadPreloadedDatabase:
{
data.enforceInterface(DESCRIPTOR);
this.downloadPreloadedDatabase();
reply.writeNoException();
return true;
}
case TRANSACTION_downloadEmptyDatabase:
{
data.enforceInterface(DESCRIPTOR);
this.downloadEmptyDatabase();
reply.writeNoException();
return true;
}
case TRANSACTION_backupDatabase:
{
data.enforceInterface(DESCRIPTOR);
this.backupDatabase();
reply.writeNoException();
return true;
}
case TRANSACTION_restoreDatabase:
{
data.enforceInterface(DESCRIPTOR);
this.restoreDatabase();
reply.writeNoException();
return true;
}
case TRANSACTION_resetImInfo:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
this.resetImInfo(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_removeImInfo:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.lang.String _arg1;
_arg1 = data.readString();
this.removeImInfo(_arg0, _arg1);
reply.writeNoException();
return true;
}
case TRANSACTION_setImInfo:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.lang.String _arg1;
_arg1 = data.readString();
java.lang.String _arg2;
_arg2 = data.readString();
this.setImInfo(_arg0, _arg1, _arg2);
reply.writeNoException();
return true;
}
case TRANSACTION_setIMKeyboard:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.lang.String _arg1;
_arg1 = data.readString();
java.lang.String _arg2;
_arg2 = data.readString();
this.setIMKeyboard(_arg0, _arg1, _arg2);
reply.writeNoException();
return true;
}
case TRANSACTION_closeDatabse:
{
data.enforceInterface(DESCRIPTOR);
this.closeDatabse();
reply.writeNoException();
return true;
}
case TRANSACTION_getImInfo:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.lang.String _arg1;
_arg1 = data.readString();
java.lang.String _result = this.getImInfo(_arg0, _arg1);
reply.writeNoException();
reply.writeString(_result);
return true;
}
case TRANSACTION_getKeyboardCode:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.lang.String _result = this.getKeyboardCode(_arg0);
reply.writeNoException();
reply.writeString(_result);
return true;
}
case TRANSACTION_getKeyboardList:
{
data.enforceInterface(DESCRIPTOR);
java.util.List _result = this.getKeyboardList();
reply.writeNoException();
reply.writeList(_result);
return true;
}
case TRANSACTION_getKeyboardInfo:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.lang.String _arg1;
_arg1 = data.readString();
java.lang.String _result = this.getKeyboardInfo(_arg0, _arg1);
reply.writeNoException();
reply.writeString(_result);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements net.toload.main.hd.IDBService
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
public void loadMapping(java.lang.String filename, java.lang.String tablename) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(filename);
_data.writeString(tablename);
mRemote.transact(Stub.TRANSACTION_loadMapping, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void resetMapping(java.lang.String tablename) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(tablename);
mRemote.transact(Stub.TRANSACTION_resetMapping, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void resetDownloadDatabase() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_resetDownloadDatabase, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void downloadDayi() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_downloadDayi, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void downloadPhonetic() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_downloadPhonetic, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void downloadPhoneticAdv() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_downloadPhoneticAdv, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void downloadCj() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_downloadCj, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void downloadScj() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_downloadScj, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void downloadCj5() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_downloadCj5, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void downloadEcj() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_downloadEcj, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void downloadArray() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_downloadArray, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void downloadArray10() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_downloadArray10, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void downloadWb() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_downloadWb, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void downloadEz() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_downloadEz, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void downloadPreloadedDatabase() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_downloadPreloadedDatabase, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void downloadEmptyDatabase() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_downloadEmptyDatabase, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void backupDatabase() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_backupDatabase, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void restoreDatabase() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_restoreDatabase, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void resetImInfo(java.lang.String im) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(im);
mRemote.transact(Stub.TRANSACTION_resetImInfo, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void removeImInfo(java.lang.String im, java.lang.String field) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(im);
_data.writeString(field);
mRemote.transact(Stub.TRANSACTION_removeImInfo, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void setImInfo(java.lang.String im, java.lang.String field, java.lang.String value) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(im);
_data.writeString(field);
_data.writeString(value);
mRemote.transact(Stub.TRANSACTION_setImInfo, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void setIMKeyboard(java.lang.String im, java.lang.String value, java.lang.String keyboard) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(im);
_data.writeString(value);
_data.writeString(keyboard);
mRemote.transact(Stub.TRANSACTION_setIMKeyboard, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void closeDatabse() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_closeDatabse, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public java.lang.String getImInfo(java.lang.String im, java.lang.String field) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.lang.String _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(im);
_data.writeString(field);
mRemote.transact(Stub.TRANSACTION_getImInfo, _data, _reply, 0);
_reply.readException();
_result = _reply.readString();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public java.lang.String getKeyboardCode(java.lang.String im) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.lang.String _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(im);
mRemote.transact(Stub.TRANSACTION_getKeyboardCode, _data, _reply, 0);
_reply.readException();
_result = _reply.readString();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public java.util.List getKeyboardList() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.util.List _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getKeyboardList, _data, _reply, 0);
_reply.readException();
java.lang.ClassLoader cl = (java.lang.ClassLoader)this.getClass().getClassLoader();
_result = _reply.readArrayList(cl);
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public java.lang.String getKeyboardInfo(java.lang.String keyboardCode, java.lang.String field) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.lang.String _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(keyboardCode);
_data.writeString(field);
mRemote.transact(Stub.TRANSACTION_getKeyboardInfo, _data, _reply, 0);
_reply.readException();
_result = _reply.readString();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
}
static final int TRANSACTION_loadMapping = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_resetMapping = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_resetDownloadDatabase = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_downloadDayi = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
static final int TRANSACTION_downloadPhonetic = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
static final int TRANSACTION_downloadPhoneticAdv = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
static final int TRANSACTION_downloadCj = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
static final int TRANSACTION_downloadScj = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
static final int TRANSACTION_downloadCj5 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
static final int TRANSACTION_downloadEcj = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
static final int TRANSACTION_downloadArray = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
static final int TRANSACTION_downloadArray10 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 11);
static final int TRANSACTION_downloadWb = (android.os.IBinder.FIRST_CALL_TRANSACTION + 12);
static final int TRANSACTION_downloadEz = (android.os.IBinder.FIRST_CALL_TRANSACTION + 13);
static final int TRANSACTION_downloadPreloadedDatabase = (android.os.IBinder.FIRST_CALL_TRANSACTION + 14);
static final int TRANSACTION_downloadEmptyDatabase = (android.os.IBinder.FIRST_CALL_TRANSACTION + 15);
static final int TRANSACTION_backupDatabase = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16);
static final int TRANSACTION_restoreDatabase = (android.os.IBinder.FIRST_CALL_TRANSACTION + 17);
static final int TRANSACTION_resetImInfo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 18);
static final int TRANSACTION_removeImInfo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 19);
static final int TRANSACTION_setImInfo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 20);
static final int TRANSACTION_setIMKeyboard = (android.os.IBinder.FIRST_CALL_TRANSACTION + 21);
static final int TRANSACTION_closeDatabse = (android.os.IBinder.FIRST_CALL_TRANSACTION + 22);
static final int TRANSACTION_getImInfo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 23);
static final int TRANSACTION_getKeyboardCode = (android.os.IBinder.FIRST_CALL_TRANSACTION + 24);
static final int TRANSACTION_getKeyboardList = (android.os.IBinder.FIRST_CALL_TRANSACTION + 25);
static final int TRANSACTION_getKeyboardInfo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 26);
}
public void loadMapping(java.lang.String filename, java.lang.String tablename) throws android.os.RemoteException;
public void resetMapping(java.lang.String tablename) throws android.os.RemoteException;
public void resetDownloadDatabase() throws android.os.RemoteException;
public void downloadDayi() throws android.os.RemoteException;
public void downloadPhonetic() throws android.os.RemoteException;
public void downloadPhoneticAdv() throws android.os.RemoteException;
public void downloadCj() throws android.os.RemoteException;
public void downloadScj() throws android.os.RemoteException;
public void downloadCj5() throws android.os.RemoteException;
public void downloadEcj() throws android.os.RemoteException;
public void downloadArray() throws android.os.RemoteException;
public void downloadArray10() throws android.os.RemoteException;
public void downloadWb() throws android.os.RemoteException;
public void downloadEz() throws android.os.RemoteException;
public void downloadPreloadedDatabase() throws android.os.RemoteException;
public void downloadEmptyDatabase() throws android.os.RemoteException;
public void backupDatabase() throws android.os.RemoteException;
public void restoreDatabase() throws android.os.RemoteException;
public void resetImInfo(java.lang.String im) throws android.os.RemoteException;
public void removeImInfo(java.lang.String im, java.lang.String field) throws android.os.RemoteException;
public void setImInfo(java.lang.String im, java.lang.String field, java.lang.String value) throws android.os.RemoteException;
public void setIMKeyboard(java.lang.String im, java.lang.String value, java.lang.String keyboard) throws android.os.RemoteException;
public void closeDatabse() throws android.os.RemoteException;
public java.lang.String getImInfo(java.lang.String im, java.lang.String field) throws android.os.RemoteException;
public java.lang.String getKeyboardCode(java.lang.String im) throws android.os.RemoteException;
public java.util.List getKeyboardList() throws android.os.RemoteException;
public java.lang.String getKeyboardInfo(java.lang.String keyboardCode, java.lang.String field) throws android.os.RemoteException;
}
