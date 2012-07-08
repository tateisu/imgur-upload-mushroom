package jp.juggler.ImgurMush;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

public class ImgurHistory {
	static DataProvider.TableMeta meta = DataProvider.history;
	
	public static final String COL_ID = BaseColumns._ID;
	public static final String COL_IMGUR_PAGE = "imgur_page";
	public static final String COL_DELETE_PAGE = "delete_page";
	public static final String COL_IMAGE_LINK = "original";
	public static final String COL_SQUARE = "small_square";
	public static final String COL_UPLOAD_TIME = "upload_time";
	public static final String COL_ACCOUNT_NAME = "account_name";
	public static final String COL_ALBUM_ID = "album_id";
	
	
	public long id = -1;
	public String page;
	public String delete;
	public String image;
	public String square;
	public long upload_time;
	public String account_name;
	public String album_id;
	
	public static ImgurHistory load(ContentResolver cr,long id){
		Cursor c = cr.query(Uri.withAppendedPath(meta.uri,"/"+id),null,null,null,null);
		if(c !=null){
			try{
				if(c.moveToNext() ){
					ImgurHistory item = new ImgurHistory();
					item.id = c.getLong(c.getColumnIndex(COL_ID));
					item.page = c.getString(c.getColumnIndex(COL_IMGUR_PAGE));
					item.delete = c.getString(c.getColumnIndex(COL_DELETE_PAGE));
					item.image = c.getString(c.getColumnIndex(COL_IMAGE_LINK));
					item.square = c.getString(c.getColumnIndex(COL_SQUARE));
					item.upload_time = c.getLong(c.getColumnIndex(COL_UPLOAD_TIME));
					int idx = c.getColumnIndex(COL_ACCOUNT_NAME);
					item.account_name =( c.isNull(idx)? null : c.getString(idx) );
					idx = c.getColumnIndex(COL_ALBUM_ID);
					item.album_id =( c.isNull(idx)? null : c.getString(idx) );
					return item;
				}
			}finally{
				c.close();
			}
		}
		return null;
	}
	
	void save(ContentResolver cr){
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
			cr.update(Uri.withAppendedPath(meta.uri,"/"+id),values,null,null);
		}
	}
}
