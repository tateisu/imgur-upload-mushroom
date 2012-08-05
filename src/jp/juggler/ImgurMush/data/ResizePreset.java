package jp.juggler.ImgurMush.data;

import jp.juggler.ImgurMush.DataProvider;
import jp.juggler.ImgurMush.R;
import jp.juggler.util.HelperEnv;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

public class ResizePreset {
	public static final String COL_ID = BaseColumns._ID;
	public static final String COL_MODE = "rp_mode";
	public static final String COL_VALUE = "rp_value";

	public static void create_table(SQLiteDatabase db) {
		db.execSQL("create table if not exists resize_preset ("
				+COL_ID    +" INTEGER PRIMARY KEY,"
				+COL_MODE  +" integer not null,"
				+COL_VALUE +" integer not null"
				+");"
		);
	}

	public static void upgrade_table(SQLiteDatabase db, int oldVersion,int newVersion) {
		if( oldVersion < 4 ){
			create_table(db);
		}
	}

	public static class ColumnIndex{
		Cursor cursor;
		int i_id ;
		int i_mode;
		int i_value;
		public void prepare(Cursor cursor){
			if( this.cursor != cursor ){
				this.cursor = cursor;
				i_id = cursor.getColumnIndex(COL_ID);
				i_mode = cursor.getColumnIndex(COL_MODE);
				i_value = cursor.getColumnIndex(COL_VALUE);
			}
		}
	}

	public long id = -1;
	public int mode;  // 0:%指定 1:長辺px 2:短辺px
	public int value;

	public static TableMeta meta = new TableMeta(DataProvider.AUTHORITY,"resize_preset");

	public void save(ContentResolver cr){
		ContentValues values = new ContentValues();
		values.put(COL_MODE,mode);
		values.put(COL_VALUE,value);
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


	public static ResizePreset loadByName(ContentResolver cr,String account_name) {
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

	public static ResizePreset loadFromCursor(Cursor cursor, ColumnIndex colidx,int position) {
		if( position >= 0 && cursor.moveToPosition(position) ){
			return loadFromCursor(cursor,colidx);
		}
		return null;
	}
	public static ResizePreset loadFromCursor(Cursor cursor, ColumnIndex colidx) {
		if( colidx == null ) colidx = new ColumnIndex();
		colidx.prepare(cursor);
		ResizePreset item = new ResizePreset();
		item.id     = cursor.getLong(colidx.i_id);
		item.mode   = cursor.getInt(colidx.i_mode);
		item.value  = cursor.getInt(colidx.i_value);
		return item;
	}

	public String makeTitle(HelperEnv env) {
		switch(mode){
		case 0: return env.getString(R.string.resize_percent,value);
		case 1: return env.getString(R.string.resize_limit_long,value);
		case 2: return env.getString(R.string.resize_limit_short,value);
		default: return "?";
		}
	}


}
