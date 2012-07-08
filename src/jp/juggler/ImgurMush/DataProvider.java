package jp.juggler.ImgurMush;

import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

public class DataProvider  extends ContentProvider{
	static final String TAG="ImgurMush";
	
	public static final String DBNAME = "ImgurMush.db";
	
	public static final String AUTHORITY = "jp.juggler.ImgurMush";
	
	public static final TableMeta account = new TableMeta(AUTHORITY,"account",1,2);

	public static final TableMeta history = new TableMeta(AUTHORITY,"history",3,4);

	////////////////////////////////////////////
	
	static class TableMeta{
		// authority
		public final String authority;
		// table name
		public final String table;
		// uri for notification
		public final Uri uri;
		// mime-type for dir
		public final String mimetype_dir;
		// mime-type for item 
		public final String mimetype_item;
		
		// ctor
		public TableMeta(String authority,String table,int match_dir,int match_item){
			this.authority = authority;
			this.table  = table;
			this.uri = Uri.parse("content://" + authority + "/" + table);
			this.mimetype_dir = "vnd.android.cursor.dir/" +authority+"." + table;
			this.mimetype_item = "vnd.android.cursor.item/"+authority+"." + table;
		}
	}

	static class MatchResult{
		TableMeta meta;
		boolean is_item;
		MatchResult(TableMeta meta,boolean is_item){
			this.meta = meta;
			this.is_item = is_item;
		}
	}
	
	DBHelper1 helper;
	UriMatcher uri_matcher;
	HashMap<Integer,MatchResult> match_map;
	
	int  addUriMatching(int match_next,TableMeta meta){
		int match_dir =  match_next;
		int match_item = match_next+1;

		uri_matcher.addURI(AUTHORITY, meta.table     ,match_dir);
		uri_matcher.addURI(AUTHORITY, meta.table+"/#",match_item);
		
		match_map.put( match_dir  ,new MatchResult(meta,false) );
		match_map.put( match_item ,new MatchResult(meta,true) );
		
		return match_next + 2;
	}

	//コンテンツプロバイダーの生成
	@Override
	public boolean onCreate() {
		// DBヘルパの作成
		helper = new DBHelper1(getContext());

		// URIマッチングの初期化
		uri_matcher= new UriMatcher(UriMatcher.NO_MATCH);
		match_map = new HashMap<Integer,MatchResult>();
		int match_next = 1;
		match_next = addUriMatching( match_next,account );
		match_next = addUriMatching( match_next,history );
	    return true;
	}
	
	final MatchResult matchUri(Uri uri){
		int n = uri_matcher.match(uri);
		MatchResult match = match_map.get(n );
//		if(match==null) Log.d(TAG,"uri="+uri+",match="+n+","+match);
		return match;
	}
	
	//URIからリクエストされた種別に変換する
	@Override
	public String getType(Uri uri) {
		MatchResult match = matchUri(uri);
		if(match==null) return null;
		TableMeta meta = match.meta;
		return match.is_item ? meta.mimetype_item : meta.mimetype_dir;
	}

	//指定内容を追加
	@Override
	public Uri insert(Uri uri, ContentValues values) {
		MatchResult match = matchUri(uri);
		if(match==null || match.is_item ) return null;
		TableMeta meta = match.meta;

		final SQLiteDatabase db = helper.getWritableDatabase();
		final long id = db.insertOrThrow(meta.table, null, values);

		// 変更を通知する
	    final Uri newUri = ContentUris.withAppendedId(meta.uri, id);
	    getContext().getContentResolver().notifyChange(newUri, null);

		return newUri;
	}

	//指定内容をdelete
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		MatchResult match = matchUri(uri);
		if(match==null) return 0;
		TableMeta meta = match.meta;
		
		final SQLiteDatabase db = helper.getWritableDatabase();
		int row_count;
		if( match.is_item ){
			row_count =  db.delete(meta.table, selection_with_id(uri,selection), selectionArgs);
		}else{
			row_count = db.delete(meta.table, selection, selectionArgs);
		}
		getContext().getContentResolver().notifyChange(meta.uri, null);
		return row_count;
	}

	//指定内容を更新する
	@Override
	public int update(Uri uri, ContentValues values, String selection,String[] selectionArgs) {
		MatchResult match = matchUri(uri);
		if(match==null) return 0;
		TableMeta meta = match.meta;
		
		final SQLiteDatabase db = helper.getWritableDatabase();
		int row_count;
		if( match.is_item ){
			row_count = db.update(meta.table, values, selection_with_id(uri,selection), selectionArgs);
			getContext().getContentResolver().notifyChange(uri, null);
		}else{
			row_count = db.update(meta.table, values, selection, selectionArgs);
			getContext().getContentResolver().notifyChange(uri, null);
			
		}
		return row_count;
	}
	
	//指定のクエリー
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,String[] selectionArgs, String sortOrder){
		MatchResult match = matchUri(uri);
		if(match==null) return null;
		TableMeta meta = match.meta;
		
		final SQLiteDatabase db = helper.getWritableDatabase();
		if( match.is_item ) selection = selection_with_id(uri,selection);
		Cursor cursor = db.query(meta.table, projection, selection, selectionArgs, null, null, sortOrder);
		cursor.setNotificationUri(getContext().getContentResolver(), uri);
		return cursor;
	}

	///////////////////////////////

	static final String selection_with_id(Uri uri,String selection){
		long id = Long.parseLong(uri.getPathSegments().get(1));
		return android.provider.BaseColumns._ID + "=" + Long.toString(id) + (selection == null ? "" : "AND (" + selection + ")");
	}	
	
	static final class DBHelper1 extends SQLiteOpenHelper {
		DBHelper1(Context context) {
			super(context, DBNAME, null, 3 ); // context,filename,CursorFactory,version
		}
		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("create table if not exists account ("
					+ImgurAccount.COL_ID     +" INTEGER PRIMARY KEY,"
                   	+ImgurAccount.COL_NAME   +" text not null,"
                   	+ImgurAccount.COL_TOKEN  +" text not null,"
                   	+ImgurAccount.COL_SECRET +" text not null"
   					+");"
			);
			db.execSQL("create unique index if not exists account_name on account("+ImgurAccount.COL_NAME+")");
			// 
			db.execSQL("create table if not exists history ("
					+ImgurHistory.COL_ID          +" INTEGER PRIMARY KEY,"
                   	+ImgurHistory.COL_IMGUR_PAGE  +" text not null,"
                   	+ImgurHistory.COL_DELETE_PAGE +" text not null,"
                   	+ImgurHistory.COL_IMAGE_LINK  +" text not null,"
                   	+ImgurHistory.COL_SQUARE      +" text not null,"
                   	+ImgurHistory.COL_UPLOAD_TIME +" integer not null,"
                   	+ImgurHistory.COL_ACCOUNT_NAME +" text,"
                   	+ImgurHistory.COL_ALBUM_ID +" text"
                   	+");"
			);
			db.execSQL("create index if not exists history_time on history("+ImgurHistory.COL_UPLOAD_TIME+")");
			db.execSQL("create index if not exists history_account_time on history("+ImgurHistory.COL_ACCOUNT_NAME+","+ImgurHistory.COL_UPLOAD_TIME+")");
			db.execSQL("create index if not exists history_account_album_time on history("+ImgurHistory.COL_ACCOUNT_NAME+","+ImgurHistory.COL_ALBUM_ID+","+ImgurHistory.COL_UPLOAD_TIME+")"); 
		}
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			if(oldVersion < 2 ){
				db.execSQL("create table if not exists history ("
						+ImgurHistory.COL_ID          +" INTEGER PRIMARY KEY,"
	                   	+ImgurHistory.COL_IMGUR_PAGE  +" text not null,"
	                   	+ImgurHistory.COL_DELETE_PAGE +" text not null,"
	                   	+ImgurHistory.COL_IMAGE_LINK  +" text not null,"
	                   	+ImgurHistory.COL_SQUARE      +" text not null,"
	                   	+ImgurHistory.COL_UPLOAD_TIME +" integer not null"
	                   	+");"
				);
			}

			if( oldVersion < 3 ){
				db.execSQL("alter table history add column "+ImgurHistory.COL_ACCOUNT_NAME+" text ");
				db.execSQL("alter table history add column "+ImgurHistory.COL_ALBUM_ID+" text ");
			}

			db.execSQL("create index if not exists history_time on history("+ImgurHistory.COL_UPLOAD_TIME+")"); 
			db.execSQL("create index if not exists history_account_time on history("+ImgurHistory.COL_ACCOUNT_NAME+","+ImgurHistory.COL_UPLOAD_TIME+")"); 
			db.execSQL("create index if not exists history_account_album_time on history("+ImgurHistory.COL_ACCOUNT_NAME+","+ImgurHistory.COL_ALBUM_ID+","+ImgurHistory.COL_UPLOAD_TIME+")"); 
		}
	}
}
