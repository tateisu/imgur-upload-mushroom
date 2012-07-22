package jp.juggler.ImgurMush.data;

import jp.juggler.ImgurMush.DataProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;

public class ImgurHistory {
	public static TableMeta meta = new TableMeta(DataProvider.AUTHORITY,"history");
	
	public static final String COL_ID = BaseColumns._ID;
	public static final String COL_IMGUR_PAGE = "imgur_page";
	public static final String COL_DELETE_PAGE = "delete_page";
	public static final String COL_IMAGE_LINK = "original";
	public static final String COL_SQUARE = "small_square";
	public static final String COL_UPLOAD_TIME = "upload_time";
	public static final String COL_ACCOUNT_NAME = "account_name";
	public static final String COL_ALBUM_ID = "album_id";
	
	public static class ColumnIndex{
		Cursor cursor;
		int i_id;
		int i_page;
		int i_delete;
		int i_image;
		int i_square;
		int i_upload_time;
		int i_account_name;
		int i_album_id;
		public void prepare(Cursor cursor){
			if( this.cursor != cursor ){
				this.cursor = cursor;
				i_id = cursor.getColumnIndex(COL_ID);
				i_page = cursor.getColumnIndex(COL_IMGUR_PAGE);
				i_delete = cursor.getColumnIndex(COL_DELETE_PAGE);
				i_image = cursor.getColumnIndex(COL_IMAGE_LINK);
				i_square = cursor.getColumnIndex(COL_SQUARE);
				i_upload_time = cursor.getColumnIndex(COL_UPLOAD_TIME);
				i_account_name = cursor.getColumnIndex(COL_ACCOUNT_NAME);
				i_album_id = cursor.getColumnIndex(COL_ALBUM_ID);
			}
		}
	}
	
	
	public long id = -1;
	public String page;
	public String delete;
	public String image;
	public String square;
	public long upload_time;
	public String account_name;
	public String album_id;
	
	public static ImgurHistory loadFromCursor(Cursor c, ColumnIndex colidx) {
		if( colidx == null ) colidx = new ColumnIndex();
		colidx.prepare(c);
		ImgurHistory item = new ImgurHistory();
		item.id = c.getLong(colidx.i_id);
		item.page = c.getString(colidx.i_page);
		item.delete = c.getString(colidx.i_delete);
		item.image = c.getString(colidx.i_image);
		item.square = c.getString(colidx.i_square);
		item.upload_time = c.getLong(colidx.i_upload_time);
		item.account_name =( c.isNull(colidx.i_account_name)? null : c.getString(colidx.i_account_name) );
		item.album_id =( c.isNull(colidx.i_album_id)? null : c.getString(colidx.i_album_id) );
		return item;
		
	}
	
	public static ImgurHistory load(ContentResolver cr,long id){
		Cursor c = cr.query(Uri.withAppendedPath(meta.uri,"/"+id),null,null,null,null);
		if(c !=null){
			try{
				if(c.moveToNext() ){
					return loadFromCursor(c,null);
				}
			}finally{
				c.close();
			}
		}
		return null;
	}
	
	public void save(ContentResolver cr){
		if( id == -1 ){
			Cursor cursor = cr.query(meta.uri,null,COL_IMAGE_LINK+"=?",new String[]{ image },null);
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
		values.put(COL_IMGUR_PAGE,page);
		values.put(COL_DELETE_PAGE,delete);
		values.put(COL_IMAGE_LINK,image);
		values.put(COL_SQUARE,square);
		values.put(COL_UPLOAD_TIME,upload_time);
		values.put(COL_ACCOUNT_NAME, account_name);
		values.put(COL_ALBUM_ID, album_id);
		if( id == -1 ){
			cr.insert(meta.uri, values);
		}else{
			cr.update(meta.uriFromId(id),values,null,null);
		}
	}

	public static void nazoById(ContentResolver cr, long id) {
		ContentValues v = new ContentValues();
		v.put(ImgurHistory.COL_SQUARE,"");
		cr.update(meta.uriFromId(id),v,null,null);
	}
	
	public static void deleteById(ContentResolver cr, long id) {
		cr.delete(meta.uriFromId(id),null,null);
	}

	public void delete(ContentResolver cr) {
		cr.delete(meta.uriFromId(id),null,null);
	}

	public static void create_table(SQLiteDatabase db) {
		db.execSQL("create table if not exists history ("
				+COL_ID          +" INTEGER PRIMARY KEY,"
               	+COL_IMGUR_PAGE  +" text not null,"
               	+COL_DELETE_PAGE +" text not null,"
               	+COL_IMAGE_LINK  +" text not null,"
               	+COL_SQUARE      +" text not null,"
               	+COL_UPLOAD_TIME +" integer not null,"
               	+COL_ACCOUNT_NAME +" text,"
               	+COL_ALBUM_ID +" text"
               	+");"
		);
		create_index(db);
	}
	public static void create_index(SQLiteDatabase db) {
		db.execSQL("create index if not exists history_time on history("+COL_UPLOAD_TIME+")");
		db.execSQL("create index if not exists history_account_time on history("+COL_ACCOUNT_NAME+","+COL_UPLOAD_TIME+")");
		db.execSQL("create index if not exists history_account_album_time on history("+COL_ACCOUNT_NAME+","+COL_ALBUM_ID+","+COL_UPLOAD_TIME+")");
	}

	public static void upgrade_table(SQLiteDatabase db, int oldVersion,int newVersion) {
		if(oldVersion < 2 ){
			create_table(db);
			return;
		}
		if( oldVersion < 3 ){
			db.execSQL("alter table history add column "+COL_ACCOUNT_NAME+" text ");
			db.execSQL("alter table history add column "+COL_ALBUM_ID+" text ");
		}
		create_index(db);
	}


}
