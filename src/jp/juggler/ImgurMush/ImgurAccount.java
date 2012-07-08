package jp.juggler.ImgurMush;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

public class ImgurAccount {
	public static final String COL_ID = BaseColumns._ID;
	public static final String COL_NAME   = "name";
	public static final String COL_TOKEN  = "token";
	public static final String COL_SECRET = "secret";
	
	public long id = -1;
	public String name;
	public String token;
	public String secret;
	
	static DataProvider.TableMeta meta = DataProvider.account;
	
	void save(ContentResolver cr){
		if( id == -1 ){
			Cursor cursor = cr.query(meta.uri,null,"name=?",new String[]{ name },null);
			if( cursor != null ){
				try{
					if( cursor.moveToNext() ){
						id = cursor.getLong(cursor.getColumnIndex(COL_ID));
					}
				}finally{
					cursor.close();
				}
			}
		}
		ContentValues values = new ContentValues();
		values.put(COL_TOKEN,token);
		values.put(COL_SECRET,secret);
		values.put(COL_NAME,name);
		if( id == -1 ){
			cr.insert(meta.uri, values);
		}else{
			cr.update(Uri.withAppendedPath(meta.uri,"/"+id),values,null,null);
		}
	}
	
	void delete(ContentResolver cr){
		if( id != -1 ){
			cr.delete(Uri.withAppendedPath(meta.uri,"/"+id),null,null);
		}
	}

	
	public static ImgurAccount loadByName(ContentResolver cr,String account_name) {
		Cursor account_cursor = cr.query(ImgurAccount.meta.uri,null,ImgurAccount.COL_NAME+"=?",new String[]{ account_name },null);
		if( account_cursor!= null ){
			try{
				if( account_cursor.moveToNext()){
					int account_cursor_colidx_id = account_cursor.getColumnIndex(ImgurAccount.COL_ID);
					int account_cursor_colidx_name = account_cursor.getColumnIndex(ImgurAccount.COL_NAME);
					int account_cursor_colidx_token = account_cursor.getColumnIndex(ImgurAccount.COL_TOKEN);
					int account_cursor_colidx_secret = account_cursor.getColumnIndex(ImgurAccount.COL_SECRET);
					ImgurAccount item = new ImgurAccount();
					item.id     = account_cursor.getLong(account_cursor_colidx_id);
					item.name   = account_cursor.getString(account_cursor_colidx_name);
					item.token  = account_cursor.getString(account_cursor_colidx_token);
					item.secret = account_cursor.getString(account_cursor_colidx_secret);
					return item;
				}
			}finally{
				account_cursor.close();
			}
		}
		return null;
	}
}
