package jp.juggler.ImgurMush.data;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import jp.juggler.ImgurMush.DataProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

public class ImgurAccount {
	public static final String COL_ID = BaseColumns._ID;
	public static final String COL_NAME   = "name";
	public static final String COL_TOKEN  = "token";
	public static final String COL_SECRET = "secret";

	public static class ColumnIndex{
		Cursor cursor;
		int i_id ;
		int i_name ;
		int i_token ;
		int i_secret ;
		public void prepare(Cursor cursor){
			if( this.cursor != cursor ){
				this.cursor = cursor;
				i_id = cursor.getColumnIndex(ImgurAccount.COL_ID);
				i_name = cursor.getColumnIndex(ImgurAccount.COL_NAME);
				i_token = cursor.getColumnIndex(ImgurAccount.COL_TOKEN);
				i_secret = cursor.getColumnIndex(ImgurAccount.COL_SECRET);
			}
		}
	}

	public long id = -1;
	public String name;
	public String token;
	public String secret;

	public static TableMeta meta = new TableMeta(DataProvider.AUTHORITY,"account");

	public void save(ContentResolver cr){
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
			cr.update(meta.uriFromId(id),values,null,null);
		}
	}

	public void delete(ContentResolver cr){
		if( id != -1 ){
			cr.delete(meta.uriFromId(id),null,null);
		}
	}


	public static ImgurAccount loadByName(ContentResolver cr,String account_name) {
		Cursor cursor = cr.query(ImgurAccount.meta.uri,null,ImgurAccount.COL_NAME+"=?",new String[]{ account_name },null);
		if( cursor!= null ){
			try{
				return loadFromCursor(cursor,null,0);
			}finally{
				cursor.close();
			}
		}
		return null;
	}

	public static ImgurAccount loadFromCursor(Cursor cursor, ColumnIndex colidx,int position) {
		if( position >= 0 && cursor.moveToPosition(position) ){
			return loadFromCursor(cursor,colidx);
		}
		return null;
	}
	public static ImgurAccount loadFromCursor(Cursor cursor, ColumnIndex colidx) {
		if( colidx == null ) colidx = new ColumnIndex();
		colidx.prepare(cursor);
		ImgurAccount item = new ImgurAccount();
		item.id     = cursor.getLong(colidx.i_id);
		item.name   = cursor.getString(colidx.i_name);
		item.token  = cursor.getString(colidx.i_token);
		item.secret = cursor.getString(colidx.i_secret);
		return item;
	}

	public static void create_table(SQLiteDatabase db) {
		db.execSQL("create table if not exists account ("
				+COL_ID     +" INTEGER PRIMARY KEY,"
				+COL_NAME   +" text not null,"
				+COL_TOKEN  +" text not null,"
				+COL_SECRET +" text not null"
				+");"
		);
		db.execSQL("create unique index if not exists account_name on account("+ImgurAccount.COL_NAME+")");

	}

	public static ArrayList<ImgurAccount> loadAll(ContentResolver cr,AtomicBoolean bCancelled) {
		ArrayList<ImgurAccount> list = new ArrayList<ImgurAccount>();
		ImgurAccount.ColumnIndex colidx = new ImgurAccount.ColumnIndex();
		Cursor c = cr.query(ImgurAccount.meta.uri,null,null,null,ImgurAccount.COL_NAME+" asc");
		if( c!= null ){
			try{
				while( c.moveToNext() ){
					if( bCancelled != null && bCancelled.get()) break;
					ImgurAccount item = ImgurAccount.loadFromCursor(c,colidx);
					if(item!=null) list.add(item);
				}
			}finally{
				c.close();
			}
		}
		return list;
	}
}
