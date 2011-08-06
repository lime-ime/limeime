/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: C:\\DataCenter\\Project\\Android\\Development\\Workspace\\LIMEHD\\src\\net\\toload\\main\\hd\\ISearchService.aidl
 */
package net.toload.main.hd;
public interface ISearchService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements net.toload.main.hd.ISearchService
{
private static final java.lang.String DESCRIPTOR = "net.toload.main.hd.ISearchService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an net.toload.main.hd.ISearchService interface,
 * generating a proxy if needed.
 */
public static net.toload.main.hd.ISearchService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = (android.os.IInterface)obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof net.toload.main.hd.ISearchService))) {
return ((net.toload.main.hd.ISearchService)iin);
}
return new net.toload.main.hd.ISearchService.Stub.Proxy(obj);
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
case TRANSACTION_initial:
{
data.enforceInterface(DESCRIPTOR);
this.initial();
reply.writeNoException();
return true;
}
case TRANSACTION_getTablename:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _result = this.getTablename();
reply.writeNoException();
reply.writeString(_result);
return true;
}
case TRANSACTION_setTablename:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
boolean _arg1;
_arg1 = (0!=data.readInt());
boolean _arg2;
_arg2 = (0!=data.readInt());
this.setTablename(_arg0, _arg1, _arg2);
reply.writeNoException();
return true;
}
case TRANSACTION_query:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
boolean _arg1;
_arg1 = (0!=data.readInt());
boolean _arg2;
_arg2 = (0!=data.readInt());
java.util.List _result = this.query(_arg0, _arg1, _arg2);
reply.writeNoException();
reply.writeList(_result);
return true;
}
case TRANSACTION_rQuery:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
this.rQuery(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_queryUserDic:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.util.List _result = this.queryUserDic(_arg0);
reply.writeNoException();
reply.writeList(_result);
return true;
}
case TRANSACTION_addUserDict:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.lang.String _arg1;
_arg1 = data.readString();
java.lang.String _arg2;
_arg2 = data.readString();
java.lang.String _arg3;
_arg3 = data.readString();
int _arg4;
_arg4 = data.readInt();
boolean _arg5;
_arg5 = (0!=data.readInt());
this.addUserDict(_arg0, _arg1, _arg2, _arg3, _arg4, _arg5);
reply.writeNoException();
return true;
}
case TRANSACTION_clear:
{
data.enforceInterface(DESCRIPTOR);
this.clear();
reply.writeNoException();
return true;
}
case TRANSACTION_postFinishInput:
{
data.enforceInterface(DESCRIPTOR);
this.postFinishInput();
reply.writeNoException();
return true;
}
case TRANSACTION_hanConvert:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.lang.String _result = this.hanConvert(_arg0);
reply.writeNoException();
reply.writeString(_result);
return true;
}
case TRANSACTION_keyToKeyname:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.lang.String _result = this.keyToKeyname(_arg0);
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
case TRANSACTION_getImList:
{
data.enforceInterface(DESCRIPTOR);
java.util.List _result = this.getImList();
reply.writeNoException();
reply.writeList(_result);
return true;
}
case TRANSACTION_queryDictionary:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.util.List _result = this.queryDictionary(_arg0);
reply.writeNoException();
reply.writeList(_result);
return true;
}
case TRANSACTION_setSelectedText:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
this.setSelectedText(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_getSelectedText:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _result = this.getSelectedText();
reply.writeNoException();
reply.writeString(_result);
return true;
}
case TRANSACTION_close:
{
data.enforceInterface(DESCRIPTOR);
this.close();
reply.writeNoException();
return true;
}
case TRANSACTION_isImKeys:
{
data.enforceInterface(DESCRIPTOR);
char _arg0;
_arg0 = (char)data.readInt();
boolean _result = this.isImKeys(_arg0);
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_getSelkey:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _result = this.getSelkey();
reply.writeNoException();
reply.writeString(_result);
return true;
}
case TRANSACTION_isSelkey:
{
data.enforceInterface(DESCRIPTOR);
char _arg0;
_arg0 = (char)data.readInt();
int _result = this.isSelkey(_arg0);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_addLDPhrase:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
java.lang.String _arg1;
_arg1 = data.readString();
java.lang.String _arg2;
_arg2 = data.readString();
int _arg3;
_arg3 = data.readInt();
boolean _arg4;
_arg4 = (0!=data.readInt());
this.addLDPhrase(_arg0, _arg1, _arg2, _arg3, _arg4);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements net.toload.main.hd.ISearchService
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
public void initial() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_initial, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public java.lang.String getTablename() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.lang.String _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getTablename, _data, _reply, 0);
_reply.readException();
_result = _reply.readString();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public void setTablename(java.lang.String tablename, boolean numberMapping, boolean symbolMapping) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(tablename);
_data.writeInt(((numberMapping)?(1):(0)));
_data.writeInt(((symbolMapping)?(1):(0)));
mRemote.transact(Stub.TRANSACTION_setTablename, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public java.util.List query(java.lang.String code, boolean softkeyboard, boolean getAllRecords) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.util.List _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(code);
_data.writeInt(((softkeyboard)?(1):(0)));
_data.writeInt(((getAllRecords)?(1):(0)));
mRemote.transact(Stub.TRANSACTION_query, _data, _reply, 0);
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
public void rQuery(java.lang.String word) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(word);
mRemote.transact(Stub.TRANSACTION_rQuery, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public java.util.List queryUserDic(java.lang.String word) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.util.List _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(word);
mRemote.transact(Stub.TRANSACTION_queryUserDic, _data, _reply, 0);
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
public void addUserDict(java.lang.String id, java.lang.String code, java.lang.String word, java.lang.String pword, int score, boolean isDictionary) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(id);
_data.writeString(code);
_data.writeString(word);
_data.writeString(pword);
_data.writeInt(score);
_data.writeInt(((isDictionary)?(1):(0)));
mRemote.transact(Stub.TRANSACTION_addUserDict, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void clear() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_clear, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public void postFinishInput() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_postFinishInput, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public java.lang.String hanConvert(java.lang.String input) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.lang.String _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(input);
mRemote.transact(Stub.TRANSACTION_hanConvert, _data, _reply, 0);
_reply.readException();
_result = _reply.readString();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public java.lang.String keyToKeyname(java.lang.String code) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.lang.String _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(code);
mRemote.transact(Stub.TRANSACTION_keyToKeyname, _data, _reply, 0);
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
public java.util.List getImList() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.util.List _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getImList, _data, _reply, 0);
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
public java.util.List queryDictionary(java.lang.String word) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.util.List _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(word);
mRemote.transact(Stub.TRANSACTION_queryDictionary, _data, _reply, 0);
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
public void setSelectedText(java.lang.String text) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(text);
mRemote.transact(Stub.TRANSACTION_setSelectedText, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public java.lang.String getSelectedText() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.lang.String _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getSelectedText, _data, _reply, 0);
_reply.readException();
_result = _reply.readString();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public void close() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_close, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
public boolean isImKeys(char c) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(((int)c));
mRemote.transact(Stub.TRANSACTION_isImKeys, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public java.lang.String getSelkey() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.lang.String _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getSelkey, _data, _reply, 0);
_reply.readException();
_result = _reply.readString();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public int isSelkey(char c) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(((int)c));
mRemote.transact(Stub.TRANSACTION_isSelkey, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public void addLDPhrase(java.lang.String id, java.lang.String code, java.lang.String word, int score, boolean ending) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(id);
_data.writeString(code);
_data.writeString(word);
_data.writeInt(score);
_data.writeInt(((ending)?(1):(0)));
mRemote.transact(Stub.TRANSACTION_addLDPhrase, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_initial = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_getTablename = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_setTablename = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_query = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
static final int TRANSACTION_rQuery = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
static final int TRANSACTION_queryUserDic = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
static final int TRANSACTION_addUserDict = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
static final int TRANSACTION_clear = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
static final int TRANSACTION_postFinishInput = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
static final int TRANSACTION_hanConvert = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
static final int TRANSACTION_keyToKeyname = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
static final int TRANSACTION_getKeyboardList = (android.os.IBinder.FIRST_CALL_TRANSACTION + 11);
static final int TRANSACTION_getImList = (android.os.IBinder.FIRST_CALL_TRANSACTION + 12);
static final int TRANSACTION_queryDictionary = (android.os.IBinder.FIRST_CALL_TRANSACTION + 13);
static final int TRANSACTION_setSelectedText = (android.os.IBinder.FIRST_CALL_TRANSACTION + 14);
static final int TRANSACTION_getSelectedText = (android.os.IBinder.FIRST_CALL_TRANSACTION + 15);
static final int TRANSACTION_close = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16);
static final int TRANSACTION_isImKeys = (android.os.IBinder.FIRST_CALL_TRANSACTION + 17);
static final int TRANSACTION_getSelkey = (android.os.IBinder.FIRST_CALL_TRANSACTION + 18);
static final int TRANSACTION_isSelkey = (android.os.IBinder.FIRST_CALL_TRANSACTION + 19);
static final int TRANSACTION_addLDPhrase = (android.os.IBinder.FIRST_CALL_TRANSACTION + 20);
}
public void initial() throws android.os.RemoteException;
public java.lang.String getTablename() throws android.os.RemoteException;
public void setTablename(java.lang.String tablename, boolean numberMapping, boolean symbolMapping) throws android.os.RemoteException;
public java.util.List query(java.lang.String code, boolean softkeyboard, boolean getAllRecords) throws android.os.RemoteException;
public void rQuery(java.lang.String word) throws android.os.RemoteException;
public java.util.List queryUserDic(java.lang.String word) throws android.os.RemoteException;
public void addUserDict(java.lang.String id, java.lang.String code, java.lang.String word, java.lang.String pword, int score, boolean isDictionary) throws android.os.RemoteException;
public void clear() throws android.os.RemoteException;
public void postFinishInput() throws android.os.RemoteException;
public java.lang.String hanConvert(java.lang.String input) throws android.os.RemoteException;
public java.lang.String keyToKeyname(java.lang.String code) throws android.os.RemoteException;
public java.util.List getKeyboardList() throws android.os.RemoteException;
public java.util.List getImList() throws android.os.RemoteException;
public java.util.List queryDictionary(java.lang.String word) throws android.os.RemoteException;
public void setSelectedText(java.lang.String text) throws android.os.RemoteException;
public java.lang.String getSelectedText() throws android.os.RemoteException;
public void close() throws android.os.RemoteException;
public boolean isImKeys(char c) throws android.os.RemoteException;
public java.lang.String getSelkey() throws android.os.RemoteException;
public int isSelkey(char c) throws android.os.RemoteException;
public void addLDPhrase(java.lang.String id, java.lang.String code, java.lang.String word, int score, boolean ending) throws android.os.RemoteException;
}
