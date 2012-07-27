package jp.juggler.ImgurMush.data;

import android.net.Uri;

public class TableMeta {
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
	public TableMeta(String authority,String table){
		this.authority = authority;
		this.table  = table;
		this.uri = Uri.parse("content://" + authority + "/" + table);
		this.mimetype_dir = "vnd.android.cursor.dir/" +authority+"." + table;
		this.mimetype_item = "vnd.android.cursor.item/"+authority+"." + table;
	}

	public Uri uriFromId(long id) {
		return Uri.withAppendedPath(uri,"/"+id);
	}
}
